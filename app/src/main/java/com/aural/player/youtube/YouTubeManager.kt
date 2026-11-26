// Gestor de búsqueda y descarga de YouTube vía NewPipe Extractor
package com.aural.player.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aural.player.data.SettingsStore
import com.aural.player.data.TagEditor
import com.aural.player.data.TagWriteResult
import com.aural.player.shared.YoutubeTitleParser
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.net.URL

/**
 * Resultado de búsqueda de YouTube.
 */
data class YTResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?
)

/** Tipo de colección online en la búsqueda unificada. */
enum class OnlineCollectionKind { ALBUM, PLAYLIST, ARTIST }

/** Álbum, playlist o artista online encontrado en la búsqueda. */
data class OnlineCollectionResult(
    val kind: OnlineCollectionKind,
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?
)

/** Resultados online agrupados para la búsqueda unificada (1.3.0). */
data class OnlineSearchResults(
    val query: String = "",
    val songs: List<YTResult> = emptyList(),
    val albums: List<OnlineCollectionResult> = emptyList(),
    val playlists: List<OnlineCollectionResult> = emptyList(),
    val artists: List<OnlineCollectionResult> = emptyList()
)

/**
 * Busca y descarga audio de YouTube sin necesidad de API key.
 */
class YouTubeManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _results = MutableStateFlow<List<YTResult>>(emptyList())
    val results: StateFlow<List<YTResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _onlineResults = MutableStateFlow(OnlineSearchResults())
    val onlineResults: StateFlow<OnlineSearchResults> = _onlineResults.asStateFlow()

    private val _onlineSearching = MutableStateFlow(false)
    val onlineSearching: StateFlow<Boolean> = _onlineSearching.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    @Volatile
    private var onlineSearchGeneration = 0

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    // Progreso por videoId: 0.0 a 1.0
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    // v1.0.7: videos descargados en esta sesión (videoId -> nombre de archivo).
    // Vive en el manager (no en un remember) para que el indicador "Descargada"
    // sobreviva a que la pantalla salga de la composición durante el rescan.
    private val _downloaded = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloaded: StateFlow<Map<String, String>> = _downloaded.asStateFlow()

    fun markDownloaded(videoId: String, fileName: String) {
        _downloaded.value = _downloaded.value + (videoId to fileName)
    }
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    /**
     * Cancela una descarga en curso.
     */
    fun cancelDownload(videoId: String) {
        // Al quitarlo de _downloading, el loop de descarga lo detecta y se detiene
        _downloading.value = _downloading.value - videoId
        _progress.value = _progress.value - videoId
        android.util.Log.d("YouTubeManager", "Descarga cancelada: $videoId")
    }

    init {
        // Inicializar NewPipe con el downloader por defecto
        try {
            NewPipe.init(NewPipeDownloaderImpl())
        } catch (_: Exception) {
            // Ya inicializado
        }
    }

    fun search(query: String) {
        if (query.isBlank() || _isSearching.value) return
        _isSearching.value = true
        _results.value = emptyList()
        scope.launch {
            try {
                val service: StreamingService = NewPipe.getService("YouTube")
                val searchInfo: SearchInfo = SearchInfo.getInfo(
                    service,
                    service.searchQHFactory.fromQuery(query)
                )
                val items = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(20)
                    .mapNotNull { item ->
                        try {
                            // Título y artista reales: el título del video
                            // trae canal + artista + canción mezclados.
                            val parsed = YoutubeTitleParser.parse(
                                item.name ?: "Sin título",
                                item.uploaderName ?: "Artista misterioso"
                            )
                            YTResult(
                                videoId = extractVideoId(item.url),
                                title = parsed.title,
                                artist = parsed.artist,
                                durationSec = item.duration,
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url
                            )
                        } catch (_: Exception) { null }
                    }
                withContext(Dispatchers.Main) {
                    _results.value = items
                    _isSearching.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeManager", "Error buscando: ${e.message}")
                withContext(Dispatchers.Main) { _isSearching.value = false }
            }
        }
    }

    /**
     * Búsqueda unificada 1.3.0: canciones, álbumes, playlists y
     * artistas en paralelo. Una generación descarta respuestas
     * viejas si el texto cambió mientras buscaba.
     */
    fun searchUnified(query: String) {
        val clean = query.trim()
        val generation = ++onlineSearchGeneration
        if (clean.isBlank()) {
            _onlineResults.value = OnlineSearchResults()
            _onlineSearching.value = false
            _onlineError.value = null
            return
        }
        _onlineSearching.value = true
        _onlineError.value = null
        scope.launch {
            try {
                val service: StreamingService = NewPipe.getService("YouTube")
                // Cada sección se aísla: si un filtro falla sin VPN
                // (p. ej. music_songs), las demás siguen saliendo.
                val songsDeferred = async {
                    runCatching { searchSongs(service, clean) }.getOrDefault(emptyList())
                }
                val albumsDeferred = async {
                    runCatching {
                        searchCollections(service, clean, "music_albums", OnlineCollectionKind.ALBUM)
                    }.getOrDefault(emptyList())
                }
                val playlistsDeferred = async {
                    runCatching {
                        searchCollections(service, clean, "music_playlists", OnlineCollectionKind.PLAYLIST)
                    }.getOrDefault(emptyList())
                }
                val artistsDeferred = async {
                    runCatching { searchArtists(service, clean) }.getOrDefault(emptyList())
                }
                val results = OnlineSearchResults(
                    query = clean,
                    songs = songsDeferred.await(),
                    albums = albumsDeferred.await(),
                    playlists = playlistsDeferred.await(),
                    artists = artistsDeferred.await()
                )
                withContext(Dispatchers.Main) {
                    if (generation == onlineSearchGeneration) {
                        _onlineResults.value = results
                        _onlineSearching.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeManager", "Error en búsqueda unificada: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (generation == onlineSearchGeneration) {
                        _onlineResults.value = OnlineSearchResults(query = clean)
                        _onlineError.value = "online_search_failed"
                        _onlineSearching.value = false
                    }
                }
            }
        }
    }

    fun clearOnlineResults() {
        onlineSearchGeneration++
        _onlineResults.value = OnlineSearchResults()
        _onlineSearching.value = false
        _onlineError.value = null
    }

    private fun searchItems(
        service: StreamingService,
        query: String,
        filter: String?
    ): List<org.schabi.newpipe.extractor.InfoItem> {
        val searchInfo: SearchInfo = SearchInfo.getInfo(
            service,
            if (filter == null) {
                service.searchQHFactory.fromQuery(query)
            } else {
                service.searchQHFactory.fromQuery(query, listOf(filter), "")
            }
        )
        return searchInfo.relatedItems
    }

    private fun mapSongItem(item: StreamInfoItem): YTResult? = try {
        val parsed = YoutubeTitleParser.parse(
            item.name ?: "Sin título",
            item.uploaderName ?: "Artista misterioso"
        )
        YTResult(
            videoId = extractVideoId(item.url),
            title = parsed.title,
            artist = parsed.artist,
            durationSec = item.duration,
            thumbnailUrl = item.thumbnails.firstOrNull()?.url
        ).takeIf { it.videoId.isNotBlank() }
    } catch (_: Exception) { null }

    private fun searchSongs(service: StreamingService, query: String): List<YTResult> {
        val musicSongs = runCatching {
            searchItems(service, query, "music_songs")
                .filterIsInstance<StreamInfoItem>()
                .take(20)
                .mapNotNull { mapSongItem(it) }
        }.getOrDefault(emptyList())
        if (musicSongs.isNotEmpty()) return musicSongs
        // Respaldo sin VPN: el filtro music_songs puede volver
        // vacío o fallar aunque la búsqueda general sí devuelva
        // videos. Se mapean igual que las canciones, mismo diseño.
        return runCatching {
            searchItems(service, query, null)
                .filterIsInstance<StreamInfoItem>()
                .take(20)
                .mapNotNull { mapSongItem(it) }
                .distinctBy { it.videoId }
        }.getOrDefault(emptyList())
    }

    private fun searchCollections(
        service: StreamingService,
        query: String,
        filter: String,
        kind: OnlineCollectionKind
    ): List<OnlineCollectionResult> =
        searchItems(service, query, filter)
            .filterIsInstance<PlaylistInfoItem>()
            .take(12)
            .mapNotNull { item ->
                val url = item.url ?: return@mapNotNull null
                val count = runCatching { item.streamCount }.getOrDefault(0L)
                val subtitle = listOfNotNull(
                    item.uploaderName?.takeIf { it.isNotBlank() },
                    if (count > 0L) "$count canciones" else null
                ).joinToString(" · ")
                OnlineCollectionResult(
                    kind = kind,
                    url = url,
                    title = item.name ?: "Sin título",
                    subtitle = subtitle,
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url
                )
            }

    private fun searchArtists(
        service: StreamingService,
        query: String
    ): List<OnlineCollectionResult> =
        searchItems(service, query, "channels")
            .filterIsInstance<ChannelInfoItem>()
            .take(8)
            .mapNotNull { item ->
                val url = item.url ?: return@mapNotNull null
                OnlineCollectionResult(
                    kind = OnlineCollectionKind.ARTIST,
                    url = url,
                    title = item.name ?: "Artista misterioso",
                    subtitle = "Artista",
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url
                )
            }

    /**
     * Canciones de un álbum/playlist online (para reproducirlo desde
     * la búsqueda). El detalle completo y guardar llegan en el
     * bloque de playlists; aquí solo suena la colección.
     */
    suspend fun fetchCollectionSongs(result: OnlineCollectionResult): List<YTResult> =
        withContext(Dispatchers.IO) {
            try {
                val service: StreamingService = NewPipe.getService("YouTube")
                if (result.kind == OnlineCollectionKind.ARTIST) {
                    searchSongs(service, result.title)
                } else {
                    val info = PlaylistInfo.getInfo(service, result.url)
                    info.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { mapSongItem(it) }
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeManager", "Error cargando colección: ${e.message}")
                emptyList()
            }
        }

    private fun extractVideoId(url: String?): String {
        if (url == null) return ""
        // Formato: https://www.youtube.com/watch?v=VIDEO_ID
        val regex = "[?&]v=([^&]+)".toRegex()
        return regex.find(url)?.groupValues?.get(1) ?: url.substringAfterLast("/")
    }

    /**
     * Descarga el audio de un video y lo guarda en Música/Aural.
     */
    /**
     * Descarga el audio. onComplete recibe el nombre del archivo guardado
     * (o null si falló) y el ID exacto de MediaStore de la canción (-1 si falló).
     */
    fun download(result: YTResult, onComplete: (fileName: String?, mediaId: Long, tagsEmbedded: Boolean) -> Unit) {
        if (_downloading.value.contains(result.videoId)) return
        _downloading.value = _downloading.value + result.videoId
        _progress.value = _progress.value + (result.videoId to 0f)
        _error.value = null
        scope.launch {
            var savedFileName: String? = null
            var savedMediaId = -1L
            var tagsEmbedded = false
            var errorMsg: String? = null
            try {
                val service: StreamingService = NewPipe.getService("YouTube")
                val streamInfo = StreamInfo.getInfo(
                    service,
                    "https://www.youtube.com/watch?v=${result.videoId}"
                )

                // SOLO audio puro — sin fallback a video. Calidad
                // elegida en Ajustes → Sonido (ahorro 48 Kbps por
                // defecto; calidad 128 Kbps). Se sigue prefiriendo el
                // m4a porque es el único al que se le incrustan título,
                // artista y portada.
                // OJO UNIDADES: NewPipe reporta averageBitrate en KBPS
                // (139→48, 140→128, 141→256, 251→160), NO en bps — por
                // eso el viejo ">= 48000" NUNCA matcheaba y todo caía
                // en el fallback (el más chico antes de tanda 1; el más
                // grande = Opus 251 tras tanda 1; el mejor m4a = 141
                // en c5aa71c = archivos enormes y descargas lentas).
                // La selección se hace por FORMATO (MediaFormat.M4A),
                // no por el "mime=" de la URL, y por bitrate en kbps.
                val preferQuality = try {
                    SettingsStore(context).downloadQuality.first() == "quality"
                } catch (_: Exception) {
                    false
                }
                val minKbps = if (preferQuality) 128 else 48
                val mp4Streams = streamInfo.audioStreams
                    .filter { it.format == MediaFormat.M4A }
                val audioStream = mp4Streams
                    .sortedBy { it.averageBitrate }
                    .firstOrNull { it.averageBitrate >= minKbps }
                    // Fallback: el mejor m4a, y si no hay m4a, el mejor
                    // disponible (nunca Opus si hay m4a).
                    ?: mp4Streams.maxByOrNull { it.averageBitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

                if (audioStream != null) {
                    val ext = if (audioStream.format == MediaFormat.M4A) "m4a" else "webm"
                    val fileName = sanitize("${result.artist} - ${result.title}.$ext")
                    val mediaId = downloadToMusic(audioStream.content, fileName, result, result.videoId) { pct ->
                        _progress.value = _progress.value + (result.videoId to pct)
                    }
                    if (mediaId > 0L) {
                        savedFileName = fileName
                        savedMediaId = mediaId
                        // Metadata incrustada en el archivo (título,
                        // artista, portada): el archivo es nuestro
                        // (lo creó la app), así que no pide permiso.
                        // .webm no se puede etiquetar: queda con lo
                        // de MediaStore y el fallback de la app.
                        tagsEmbedded = if (ext == "m4a") {
                            embedMetadata(mediaId, result)
                        } else false
                    } else {
                        errorMsg = "No se pudo guardar el archivo"
                    }
                } else {
                    errorMsg = "Sin audio disponible para este video"
                }
            } catch (e: Exception) {
                errorMsg = "Error: ${e.message?.take(100)}"
                android.util.Log.e("YouTubeManager", "Error descargando: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                _downloading.value = _downloading.value - result.videoId
                _progress.value = _progress.value - result.videoId
                _error.value = errorMsg
                onComplete(savedFileName, savedMediaId, tagsEmbedded)
            }
        }
    }

    private fun sanitize(name: String): String {
        return name.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .replace("?", "_").replace("*", "_").replace("\"", "_")
            .take(100)
    }

    /**
     * Guarda el audio en Música/Aural y devuelve el ID exacto de MediaStore
     * de la fila creada (-1 si falló). Con ese ID, la playlist "Descargas"
     * se actualiza por identidad, sin adivinar por título.
     */
    private fun downloadToMusic(
        url: String,
        fileName: String,
        result: YTResult,
        videoId: String,
        onProgress: (Float) -> Unit
    ): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/Aural")
                    put(android.provider.MediaStore.Audio.Media.TITLE, result.title)
                    put(android.provider.MediaStore.Audio.Media.ARTIST, result.artist)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return -1L
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        downloadWithProgress(url, out, videoId, onProgress)
                    }
                } catch (e: Exception) {
                    // No dejar la fila vacía en la biblioteca si la descarga falló
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    throw e
                }
                android.util.Log.d("YouTubeManager", "Guardado: $fileName")
                android.content.ContentUris.parseId(uri)
            } else {
                @Suppress("DEPRECATION")
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                val auralDir = File(musicDir, "Aural")
                auralDir.mkdirs()
                val destFile = File(auralDir, fileName)
                destFile.outputStream().use { out ->
                    downloadWithProgress(url, out, videoId, onProgress)
                }
                // Escanear el archivo y esperar (acotado) a conocer su ID indexado
                var scannedId = -1L
                val latch = java.util.concurrent.CountDownLatch(1)
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf("audio/mp4")
                ) { _, scannedUri ->
                    if (scannedUri != null) {
                        scannedId = runCatching {
                            android.content.ContentUris.parseId(scannedUri)
                        }.getOrDefault(-1L)
                    }
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                if (scannedId <= 0L) {
                    // Respaldo: consultar MediaStore por la ruta exacta del archivo
                    context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Audio.Media._ID),
                        "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(destFile.absolutePath),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) scannedId = cursor.getLong(0)
                    }
                }
                android.util.Log.d("YouTubeManager", "Guardado: $fileName")
                scannedId
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeManager", "Error guardando: ${e.message}")
            -1L
        }
    }

    private var currentDownloadId: String? = null

    private fun downloadWithProgress(
        url: String,
        out: java.io.OutputStream,
        videoId: String,
        onProgress: (Float) -> Unit
    ) {
        currentDownloadId = videoId
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        // Timeouts como en PC (1.2.22): sin ellos, un corte de red deja
        // la descarga colgada para siempre con la barra congelada.
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        // Headers de navegador
        connection.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Range", "bytes=0-")
        connection.connect()
        val total = connection.contentLengthLong
        var downloaded = 0L
        val buffer = ByteArray(65536) // 64KB
        var cancelled = false
        connection.inputStream.use { input ->
            while (true) {
                // Chequear cancelación
                if (!_downloading.value.contains(videoId)) {
                    cancelled = true
                    break
                }
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                downloaded += read
                if (total > 0) onProgress(downloaded.toFloat() / total)
            }
        }
        connection.disconnect()
        if (!cancelled) onProgress(1f)
        else throw java.io.IOException("Descarga cancelada por el usuario")
    }

    /**
     * Escribe título/artista/portada DENTRO del archivo descargado
     * (TagEditor: remux sin pérdida si es MP4 fragmentado +
     * verificación releyendo). Devuelve true solo si los tags
     * quedaron en el archivo. La portada es la miniatura del video.
     */
    private suspend fun embedMetadata(mediaId: Long, result: YTResult): Boolean {
        return try {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
            ).toString()
            val coverArt = result.thumbnailUrl?.let { downloadThumbnail(it) }
            TagEditor.writeTags(
                context, uri, result.title, result.artist, "", coverArt
            ) == TagWriteResult.Success
        } catch (e: Exception) {
            android.util.Log.w("YouTubeManager", "No se pudo incrustar metadata: ${e.message}")
            false
        }
    }

    /** Baja la miniatura del video (para incrustarla como portada). */
    private fun downloadThumbnail(url: String): Pair<ByteArray, String>? {
        return try {
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "AuralPlayer/1.0")
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            val mime = conn.contentType?.substringBefore(";")?.trim()
                ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            bytes to mime
        } catch (e: Exception) {
            android.util.Log.w("YouTubeManager", "No se pudo bajar la miniatura: ${e.message}")
            null
        }
    }

    fun clearResults() {
        _results.value = emptyList()
    }
}

/**
 * Resuelve la URL de audio de un video AL REPRODUCIR (1.3.0). La
 * cola guarda `aural-online://youtube/<videoId>` y el DataSource
 * del servicio llama aquí en el hilo de carga; la URL resuelta se
 * cachea hasta su caducidad para no resolver en cada rango.
 */
object YouTubeStreamResolver {

    data class ResolvedStream(
        val url: String,
        val cacheKey: String,
        val progressive: Boolean = false
    )

    private data class CachedUrl(val url: String, val expiresAtMs: Long, val diskKey: String)

    private val cache = mutableMapOf<String, CachedUrl>()
    private val prefetchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aural-stream-prefetch").apply { isDaemon = true }
    }

    /** Tras un rebuffer real, la siguiente resolución baja a ahorro
     *  durante unos minutos; el ajuste del usuario sigue siendo el
     *  techo cuando la red se recupera. */
    @Volatile
    private var temporarySaverUntilMs: Long = 0L

    fun noteRebuffer() {
        temporarySaverUntilMs = System.currentTimeMillis() + 10 * 60_000L
    }

    fun prefetch(videoId: String, preferQuality: Boolean) {
        if (videoId.isBlank()) return
        prefetchExecutor.execute {
            try {
                resolveStream(videoId, preferQuality)
            } catch (_: Exception) {
                // La precarga es oportunista: si falla, la resolución
                // normal al reproducir lo intentará de nuevo.
            }
        }
    }

    fun resolve(videoId: String, preferQuality: Boolean): String? =
        resolveStream(videoId, preferQuality)?.url

    @Synchronized
    fun resolveStream(videoId: String, preferQuality: Boolean): ResolvedStream? =
        resolveStreamInternal(videoId, preferQuality, forceFresh = false, excludeUrl = null)

    /** Re-resuelve con StreamInfo fresco tras un 403/410 o corte de
     *  googlevideo, excluyendo la URL que falló. */
    @Synchronized
    fun refreshStream(
        videoId: String,
        preferQuality: Boolean,
        failedUrl: String?
    ): ResolvedStream? =
        resolveStreamInternal(videoId, preferQuality, forceFresh = true, excludeUrl = failedUrl)

    private fun resolveStreamInternal(
        videoId: String,
        preferQuality: Boolean,
        forceFresh: Boolean,
        excludeUrl: String?
    ): ResolvedStream? {
        if (videoId.isBlank()) return null
        val now = System.currentTimeMillis()
        val effectiveQuality = preferQuality && now >= temporarySaverUntilMs
        val cacheKey = "yt:$videoId:${if (effectiveQuality) "quality" else "saver"}"
        if (!forceFresh) {
            cache[cacheKey]?.let { cached ->
                if (cached.expiresAtMs > now + 30_000L && cached.url != excludeUrl) {
                    // La URL cacheada se prueba igual: la red pudo
                    // cambiar (p. ej. VPN apagado) desde que se guardó.
                    val cachedMode = probeCandidate(cached.url)
                    if (cachedMode != ProbeMode.FAIL) {
                        val progressive = cachedMode == ProbeMode.PROGRESSIVE
                        return ResolvedStream(
                            cached.url,
                            diskKeyWithMode(cached.diskKey, progressive),
                            progressive
                        )
                    }
                    cache.remove(cacheKey)
                }
            }
        }
        return try {
            try {
                NewPipe.init(NewPipeDownloaderImpl())
            } catch (_: Exception) {
                // Ya inicializado
            }
            val service: StreamingService = NewPipe.getService("YouTube")
            val streamInfo = StreamInfo.getInfo(
                service,
                "https://www.youtube.com/watch?v=$videoId"
            )
            // Candidatas en orden: la preferida (48/128) primero y
            // luego otros formatos/bitrates. Se prefiere la que
            // acepta rangos directos (inicio + rango intermedio);
            // si ninguna los acepta pero alguna responde a la
            // lectura secuencial tipo descarga, se entrega en modo
            // progresivo. Si ninguna responde, se falla limpio y
            // el reproductor avisa/salta.
            var progressiveFallback: ResolvedStream? = null
            var progressiveFallbackExpiry = 0L
            for (audioStream in candidateStreams(streamInfo, effectiveQuality).take(6)) {
                val url = audioStream.content ?: continue
                if (url == excludeUrl) continue
                val baseDiskKey = "$cacheKey:${audioStream.format}:${audioStream.averageBitrate}"
                when (probeCandidate(url)) {
                    ProbeMode.DIRECT -> {
                        val diskKey = diskKeyWithMode(baseDiskKey, false)
                        cache[cacheKey] = CachedUrl(url, expiryMs(url, now), diskKey)
                        return ResolvedStream(url, diskKey, false)
                    }
                    ProbeMode.PROGRESSIVE -> if (progressiveFallback == null) {
                        val diskKey = diskKeyWithMode(baseDiskKey, true)
                        progressiveFallback = ResolvedStream(url, diskKey, true)
                        progressiveFallbackExpiry = expiryMs(url, now)
                    }
                    ProbeMode.FAIL -> Unit
                }
            }
            progressiveFallback?.let { fallback ->
                cache[cacheKey] = CachedUrl(fallback.url, progressiveFallbackExpiry, fallback.cacheKey)
                return fallback
            }
            android.util.Log.e(
                "YouTubeStreamResolver",
                "Ninguna URL respondió para $videoId"
            )
            null
        } catch (e: Exception) {
            android.util.Log.e("YouTubeStreamResolver", "No se pudo resolver $videoId: ${e.message}")
            null
        }
    }

    private enum class ProbeMode { DIRECT, PROGRESSIVE, FAIL }

    private fun diskKeyWithMode(diskKey: String, progressive: Boolean): String {
        val base = diskKey.removeSuffix(":direct").removeSuffix(":prog")
        return base + if (progressive) ":prog" else ":direct"
    }

    /** Prueba una URL candidata con la misma petición que usa la
     *  descarga (headers de navegador). DIRECT si acepta rangos
     *  acotados al inicio y en un punto intermedio; PROGRESSIVE
     *  si solo acepta la lectura secuencial abierta tipo descarga
     *  (pasa sin VPN con algunos formatos: el rango acotado se
     *  rechaza pero el abierto no). */
    private fun probeCandidate(url: String): ProbeMode {
        val first = probeRange(url, 0L, 2047L)
        if (first.ok) {
            val total = first.totalLength
            val mid = when {
                total != null && total > 8192L -> total / 2
                total != null -> 0L
                else -> 262_144L
            }
            if (mid == 0L || probeRange(url, mid, mid + 2047L).ok) {
                return ProbeMode.DIRECT
            }
        }
        return if (probeOpenEnded(url)) ProbeMode.PROGRESSIVE else ProbeMode.FAIL
    }

    private data class ProbeRangeResult(val ok: Boolean, val totalLength: Long?)

    private fun probeRange(url: String, start: Long, end: Long): ProbeRangeResult {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = openProbeConnection(url, "bytes=$start-$end")
            val code = connection.responseCode
            if (code !in 200..299) {
                ProbeRangeResult(false, null)
            } else {
                val total = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast("/")
                    ?.toLongOrNull()
                    ?: connection.contentLengthLong.takeIf { it > 0L }
                val ok = connection.inputStream.use { input -> input.read() >= 0 }
                ProbeRangeResult(ok, total)
            }
        } catch (_: Exception) {
            ProbeRangeResult(false, null)
        } finally {
            connection?.disconnect()
        }
    }

    /** Lectura secuencial abierta, como la descarga: si los
     *  primeros bytes llegan así, el modo progresivo puede sonar
     *  desde caché mientras baja por esta misma vía. */
    private fun probeOpenEnded(url: String): Boolean {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = openProbeConnection(url, "bytes=0-")
            val code = connection.responseCode
            if (code !in 200..299) {
                false
            } else {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    input.read(buffer) > 0
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun openProbeConnection(
        url: String,
        range: String
    ): java.net.HttpURLConnection {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Range", range)
        return connection
    }

    @Synchronized
    fun invalidate(videoId: String) {
        cache.keys.removeAll { it.startsWith("yt:$videoId:") }
    }

    /** Candidatas ordenadas para streaming: primero la misma
     *  selección que la descarga (M4A, bitrate en KBPS: 48 ahorro /
     *  128 calidad) y después el resto de formatos/bitrates, para
     *  poder reintentar con otra si googlevideo rechaza una. */
    private fun candidateStreams(
        streamInfo: StreamInfo,
        preferQuality: Boolean
    ): List<org.schabi.newpipe.extractor.stream.AudioStream> {
        val minKbps = if (preferQuality) 128 else 48
        val mp4Streams = streamInfo.audioStreams
            .filter { it.format == MediaFormat.M4A }
            .sortedBy { it.averageBitrate }
        val otherStreams = streamInfo.audioStreams
            .filter { it.format != MediaFormat.M4A }
            .sortedBy { it.averageBitrate }
        val preferred = mp4Streams.firstOrNull { it.averageBitrate >= minKbps }
            ?: mp4Streams.maxByOrNull { it.averageBitrate }
            ?: otherStreams.maxByOrNull { it.averageBitrate }
        val ordered = mutableListOf<org.schabi.newpipe.extractor.stream.AudioStream>()
        preferred?.let { ordered += it }
        ordered += mp4Streams
        ordered += if (preferQuality) otherStreams.reversed() else otherStreams
        return ordered.distinctBy { it.content }
    }

    private fun expiryMs(url: String, nowMs: Long): Long {
        val expireSec = Regex("[?&]expire=(\\d+)").find(url)
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        return if (expireSec != null && expireSec > 0L) expireSec * 1000L
        else nowMs + 5 * 60_000L
    }
}
