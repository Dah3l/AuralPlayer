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
import com.aural.player.data.DownloadNotifications
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
        DownloadNotifications.cancel(
            context, DownloadNotifications.idFor("youtube:$videoId")
        )
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
                    runCatching { searchSongsHybrid(service, clean) }.getOrDefault(emptyList())
                }
                val albumsDeferred = async {
                    runCatching { searchAlbumsHybrid(service, clean) }.getOrDefault(emptyList())
                }
                val playlistsDeferred = async {
                    runCatching {
                        searchCollections(service, clean, "music_playlists", OnlineCollectionKind.PLAYLIST)
                    }.getOrDefault(emptyList())
                }
                val artistsDeferred = async {
                    runCatching { searchArtistsHybrid(service, clean) }.getOrDefault(emptyList())
                }
                // Sin duplicados: un mismo videoId dos veces se
                // registra como el mismo Song (id negativo) y la
                // LazyColumn crashea con «Key was already used».
                val results = OnlineSearchResults(
                    query = clean,
                    songs = songsDeferred.await().distinctBy { it.videoId },
                    albums = albumsDeferred.await().distinctBy { it.url },
                    playlists = playlistsDeferred.await().distinctBy { it.url },
                    artists = artistsDeferred.await().distinctBy { it.url }
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
        if (musicSongs.isNotEmpty()) return musicSongs.distinctBy { it.videoId }
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

    private fun mapCollectionItem(
        item: PlaylistInfoItem,
        kind: OnlineCollectionKind
    ): OnlineCollectionResult? {
        val url = item.url ?: return null
        val count = runCatching { item.streamCount }.getOrDefault(0L)
        val subtitle = listOfNotNull(
            item.uploaderName?.takeIf { it.isNotBlank() },
            if (count > 0L) "$count canciones" else null
        ).joinToString(" · ")
        return OnlineCollectionResult(
            kind = kind,
            url = url,
            title = item.name ?: "Sin título",
            subtitle = subtitle,
            thumbnailUrl = item.thumbnails.firstOrNull()?.url
        )
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
            .mapNotNull { mapCollectionItem(it, kind) }
            .distinctBy { it.url }

    /** Álbumes con cadena de respaldo (adaptación LastWave): sin
     *  VPN el filtro music_albums puede volver vacío aunque el
     *  artista tenga álbumes. Directo → búsqueda general (solo
     *  playlists de álbum OLAK) → misma búsqueda con "album". */
    private fun searchAlbums(
        service: StreamingService,
        query: String
    ): List<OnlineCollectionResult> {
        val direct = runCatching {
            searchCollections(service, query, "music_albums", OnlineCollectionKind.ALBUM)
        }.getOrDefault(emptyList())
        if (direct.isNotEmpty()) return direct
        fun generalAlbums(q: String): List<OnlineCollectionResult> = runCatching {
            searchItems(service, q, null)
                .filterIsInstance<PlaylistInfoItem>()
                .filter { (it.url ?: "").contains("OLAK") }
                .take(12)
                .mapNotNull { mapCollectionItem(it, OnlineCollectionKind.ALBUM) }
        }.getOrDefault(emptyList())
        val general = generalAlbums(query)
        if (general.isNotEmpty()) return general
        if (!query.lowercase().contains("album")) {
            val withAlbum = runCatching {
                searchCollections(service, "$query album", "music_albums", OnlineCollectionKind.ALBUM)
            }.getOrDefault(emptyList())
            if (withAlbum.isNotEmpty()) return withAlbum
            return generalAlbums("$query album")
        }
        return emptyList()
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
            .distinctBy { it.url }

    /** Etapa 1 sin VPN: InnerTube WEB_REMIX es la fuente principal
     *  de canciones/álbumes/artistas (ahí viven las oficiales y los
     *  álbumes OLAK que NewPipe no devuelve sin VPN); NewPipe queda
     *  de respaldo donde ya funciona. La UI no cambia. */
    private fun searchSongsHybrid(service: StreamingService, query: String): List<YTResult> {
        val inner = runCatching { InnerTubeSearch.searchSongs(query) }.getOrDefault(emptyList())
        return inner.ifEmpty { searchSongs(service, query) }
    }

    private fun searchAlbumsHybrid(
        service: StreamingService,
        query: String
    ): List<OnlineCollectionResult> {
        val inner = runCatching { InnerTubeSearch.searchAlbums(query) }.getOrDefault(emptyList())
        return inner.ifEmpty { searchAlbums(service, query) }
    }

    private fun searchArtistsHybrid(
        service: StreamingService,
        query: String
    ): List<OnlineCollectionResult> {
        val inner = runCatching { InnerTubeSearch.searchArtists(query) }.getOrDefault(emptyList())
        return inner.ifEmpty { searchArtists(service, query) }
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
                    searchSongsHybrid(service, result.title)
                } else if (result.url.contains("/browse/MPREb")) {
                    // Álbum InnerTube sin playlist OLAK: sus canciones
                    // salen del browse de YouTube Music.
                    val browseId = result.url.substringAfterLast("/")
                    val songs = runCatching {
                        InnerTubeSearch.browseAlbumSongs(browseId)
                    }.getOrDefault(emptyList())
                    songs.ifEmpty {
                        val info = PlaylistInfo.getInfo(service, result.url)
                        info.relatedItems
                            .filterIsInstance<StreamInfoItem>()
                            .mapNotNull { mapSongItem(it) }
                    }
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
            // Una playlist puede traer el mismo video dos veces:
            // al registrarse sería el mismo Song y la lista del
            // detalle crashearía por clave repetida.
        }.distinctBy { it.videoId }

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
        val notificationId = DownloadNotifications.idFor("youtube:${result.videoId}")
        val notificationTitle = "${result.artist} - ${result.title}"
        DownloadNotifications.progress(context, notificationId, notificationTitle, 0)
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
                    var lastPercent = -1
                    val mediaId = downloadToMusic(audioStream.content, fileName, result, result.videoId) { pct ->
                        _progress.value = _progress.value + (result.videoId to pct)
                        val percent = (pct * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            DownloadNotifications.progress(
                                context, notificationId, notificationTitle, percent
                            )
                        }
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
                if (savedFileName != null) {
                    DownloadNotifications.complete(
                        context, notificationId, notificationTitle
                    )
                } else if (errorMsg != null) {
                    DownloadNotifications.error(
                        context, notificationId, notificationTitle
                    )
                } else {
                    DownloadNotifications.cancel(context, notificationId)
                }
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
 * Búsqueda InnerTube WEB_REMIX (Etapa 1 sin VPN, decisión de Dahel
 * 2026-11-26): el backend de YouTube Music con visitorData
 * bootstrapped desde la config web real de music.youtube.com
 * (apiKey/clientVersion/visitorData), reintento de contexto limpio
 * en/US ante un 400 y visitante refrescado desde responseContext.
 * Es la fuente principal de canciones/álbumes/artistas online;
 * NewPipe queda de respaldo y las descargas no se tocan. Sin
 * PoToken/BotGuard ni descifrado de firmas (fuera de alcance).
 */
object InnerTubeSearch {

    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val MUSIC_API = "https://music.youtube.com/youtubei/v1"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    private const val YOUTUBE_API = "https://www.youtube.com/youtubei/v1"
    private const val WEB_CLIENT_VERSION = "2.20261011.05.00"
    private const val WEB_PLAYLIST_SEARCH_PARAMS = "Eg-KAQwIABAAGAAgACgB"
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    private const val CONSENT_COOKIE =
        "CONSENT=YES+cb.20210328-17-p0.en+FX+100; SOCS=CAISEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
    private const val FALLBACK_WEB_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val FALLBACK_WEB_VERSION = "1.20260707.12.00"
    private const val SONG_SEARCH_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
    private const val ALBUM_SEARCH_FILTER = "EgWKAQIYAWoKEAkQBRAKEAMQBA=="
    private const val ARTIST_SEARCH_FILTER = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
    private const val CONFIG_RETRY_COOLDOWN_MS = 60_000L

    private data class WebConfig(
        val apiKey: String,
        val clientVersion: String,
        val visitorData: String?
    )

    @Volatile
    private var webConfig: WebConfig? = null
    @Volatile
    private var lastConfigAttemptMs: Long = 0L
    @Volatile
    private var youtubeConfig: WebConfig? = null
    @Volatile
    private var lastYoutubeConfigAttemptMs: Long = 0L

    fun visitorData(): String? = webConfig().visitorData?.takeIf { isValidVisitorData(it) }

    private fun isValidVisitorData(token: String?): Boolean {
        if (token.isNullOrBlank() || token.length < 15) return false
        return token.all { it.isLetterOrDigit() || it in "+/=-_%~" }
    }

    @Synchronized
    private fun webConfig(): WebConfig {
        webConfig?.takeIf { isValidVisitorData(it.visitorData) }?.let { return it }
        val now = System.currentTimeMillis()
        if (now - lastConfigAttemptMs >= CONFIG_RETRY_COOLDOWN_MS) {
            lastConfigAttemptMs = now
            runCatching { fetchWebConfig() }.getOrNull()?.let { fetched ->
                if (fetched.apiKey.isNotBlank()) {
                    webConfig = fetched
                    return fetched
                }
            }
        }
        return webConfig ?: WebConfig(FALLBACK_WEB_KEY, FALLBACK_WEB_VERSION, null)
            .also { webConfig = it }
    }

    private fun fetchWebConfig(): WebConfig {
        var connection: java.net.HttpURLConnection? = null
        try {
            connection = java.net.URL("$MUSIC_ORIGIN/")
                .openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", WEB_USER_AGENT)
            connection.setRequestProperty("Cookie", CONSENT_COOKIE)
            val html = connection.inputStream.bufferedReader().readText()
            return WebConfig(
                apiKey = findConfig(html, "INNERTUBE_API_KEY") ?: FALLBACK_WEB_KEY,
                clientVersion = findConfig(html, "INNERTUBE_CONTEXT_CLIENT_VERSION")
                    ?: FALLBACK_WEB_VERSION,
                visitorData = (findConfig(html, "VISITOR_DATA")
                    ?: findConfig(html, "EOM_VISITOR_DATA"))?.takeIf { isValidVisitorData(it) }
            )
        } finally {
            connection?.disconnect()
        }
    }

    /** Config WEB propia de youtube.com (INNERTUBE_API_KEY y
     *  versión del cliente WEB): la llave de Music no sirve en
     *  el endpoint de youtube.com. Se scrapea de la portada,
     *  igual que la de Music, con su propio cooldown. */
    @Synchronized
    private fun youtubeWebConfig(): WebConfig? {
        youtubeConfig?.takeIf { it.apiKey.isNotBlank() }?.let { return it }
        val now = System.currentTimeMillis()
        if (now - lastYoutubeConfigAttemptMs < CONFIG_RETRY_COOLDOWN_MS) {
            return youtubeConfig
        }
        lastYoutubeConfigAttemptMs = now
        val fetched = runCatching { fetchYoutubeWebConfig() }.getOrNull()
        if (fetched != null && fetched.apiKey.isNotBlank()) {
            youtubeConfig = fetched
            return fetched
        }
        return youtubeConfig
    }

    private fun fetchYoutubeWebConfig(): WebConfig {
        var connection: java.net.HttpURLConnection? = null
        try {
            connection = java.net.URL("$YOUTUBE_ORIGIN/")
                .openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", WEB_USER_AGENT)
            connection.setRequestProperty("Cookie", CONSENT_COOKIE)
            val html = connection.inputStream.bufferedReader().readText()
            return WebConfig(
                apiKey = findConfig(html, "INNERTUBE_API_KEY") ?: "",
                clientVersion = findConfig(html, "INNERTUBE_CONTEXT_CLIENT_VERSION")
                    ?: WEB_CLIENT_VERSION,
                visitorData = (findConfig(html, "VISITOR_DATA")
                    ?: findConfig(html, "EOM_VISITOR_DATA"))?.takeIf { isValidVisitorData(it) }
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun findConfig(html: String, key: String): String? {
        val patterns = listOf(
            Regex(""""$key"\s*:\s*"([^"]+)""""),
            Regex("""'$key'\s*:\s*'([^']+)'"""),
            Regex("""\\"$key\\"\s*:\s*\\"([^\\"]+)\\"""")
        )
        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.getOrNull(1)?.let { value ->
                return value
                    .replace("\\u003d", "=")
                    .replace("\\x3d", "=")
                    .replace("\\/", "/")
            }
        }
        return null
    }

    /** Visitante fresco: endpoint visitor_id y, si no, scrape de la
     *  config web. Actualiza la config cacheada si lo consigue. */
    private fun fetchFreshVisitorData(): String? {
        val config = webConfig()
        val body = org.json.JSONObject()
            .put(
                "context",
                org.json.JSONObject().put(
                    "client",
                    org.json.JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", config.clientVersion)
                        .put("hl", "en")
                        .put("gl", "US")
                )
            )
        val root = postJson("$MUSIC_API/visitor_id?key=${config.apiKey}&prettyPrint=false", body, config)
        val token = root?.optJSONObject("responseContext")?.optString("visitorData")
        if (isValidVisitorData(token)) {
            webConfig = config.copy(visitorData = token)
            return token
        }
        val scraped = runCatching { fetchWebConfig() }.getOrNull()
        if (scraped != null && isValidVisitorData(scraped.visitorData)) {
            webConfig = scraped
            return scraped.visitorData
        }
        return null
    }

    private fun clientContext(config: WebConfig, clean: Boolean): org.json.JSONObject {
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", config.clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        if (!clean && isValidVisitorData(config.visitorData)) {
            client.put("visitorData", config.visitorData)
        }
        return org.json.JSONObject().put("client", client)
    }

    /** POST InnerTube con reintento de contexto limpio ante un 400
     *  (hl/gl o visitante rechazados): se reenvía UNA vez sin
     *  visitorData y con en/US, sin tocar la config compartida. */
    private fun postJson(
        url: String,
        body: org.json.JSONObject,
        config: WebConfig
    ): org.json.JSONObject? {
        var cleanRetry = false
        while (true) {
            var connection: java.net.HttpURLConnection? = null
            try {
                val requestBody = if (cleanRetry) {
                    org.json.JSONObject(body.toString()).put("context", clientContext(config, clean = true))
                } else {
                    body
                }
                connection = java.net.URL(url)
                    .openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", WEB_USER_AGENT)
                connection.setRequestProperty("Origin", MUSIC_ORIGIN)
                connection.setRequestProperty("X-Origin", MUSIC_ORIGIN)
                connection.setRequestProperty("Referer", "$MUSIC_ORIGIN/")
                connection.setRequestProperty("X-Goog-Api-Format-Version", "1")
                connection.setRequestProperty("X-YouTube-Client-Name", "67")
                connection.setRequestProperty("X-YouTube-Client-Version", config.clientVersion)
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                connection.setRequestProperty("Cookie", CONSENT_COOKIE)
                if (!cleanRetry && isValidVisitorData(config.visitorData)) {
                    connection.setRequestProperty("X-Goog-Visitor-Id", config.visitorData)
                }
                connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
                val code = connection.responseCode
                if (code == 400 && !cleanRetry) {
                    cleanRetry = true
                    continue
                }
                if (code !in 200..299) return null
                val root = org.json.JSONObject(connection.inputStream.bufferedReader().readText())
                val respVisitor = root.optJSONObject("responseContext")?.optString("visitorData")
                if (isValidVisitorData(respVisitor) && !isValidVisitorData(webConfig?.visitorData)) {
                    webConfig = config.copy(visitorData = respVisitor)
                }
                return root
            } catch (_: Exception) {
                return null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun searchRoot(query: String, params: String?): org.json.JSONObject? {
        val config = webConfig()
        val body = org.json.JSONObject()
            .put("context", clientContext(config, clean = false))
            .put("query", query)
        if (params != null) body.put("params", params)
        return postJson("$MUSIC_API/search?key=${config.apiKey}&prettyPrint=false", body, config)
    }

    fun searchSongs(query: String): List<YTResult> {
        fun run(params: String?): List<YTResult> =
            searchRoot(query, params)?.let { parseSongs(it) } ?: emptyList()
        var results = run(SONG_SEARCH_FILTER)
        if (results.isEmpty()) results = run(null)
        if (results.isEmpty()) {
            // Visitante fresco y reintento: un visitante malo deja la
            // búsqueda oficial vacía aunque el catálogo exista.
            if (fetchFreshVisitorData() != null) {
                results = run(SONG_SEARCH_FILTER)
                if (results.isEmpty()) results = run(null)
            }
        }
        return results.distinctBy { it.videoId }.take(20)
    }

    fun searchAlbums(query: String): List<OnlineCollectionResult> {
        fun run(q: String, params: String?): List<OnlineCollectionResult> =
            searchRoot(q, params)?.let { parseAlbums(it) } ?: emptyList()
        var results = run(query, ALBUM_SEARCH_FILTER)
        if (results.isEmpty()) results = run(query, null)
        if (results.isEmpty()) {
            // Respaldo WEB (youtube.com): desde redes donde
            // YouTube Music devuelve vacío, el YouTube normal sí
            // trae las listas de álbum (OLAK), como el
            // searchEntitiesFallback de LastWave.
            results = webSearchAlbums(query)
        }
        if (results.isEmpty() && !query.lowercase().contains("album")) {
            results = run("$query album", null)
            if (results.isEmpty()) results = webSearchAlbums("$query album")
        }
        return results.distinctBy { it.url }.take(12)
    }

    private fun webSearchAlbums(query: String): List<OnlineCollectionResult> {
        fun run(params: String?): List<OnlineCollectionResult> =
            webSearchRoot(query, params)?.let { parseWebAlbums(it) } ?: emptyList()
        var results = run(WEB_PLAYLIST_SEARCH_PARAMS)
        if (results.isEmpty()) results = run(null)
        return results
    }

    /** Búsqueda en el YouTube normal con cliente WEB: mismo
     *  catálogo que Music (los álbumes viven como listas OLAK),
     *  sin la restricción regional de YouTube Music. */
    private fun webSearchRoot(query: String, params: String?): org.json.JSONObject? {
        val config = youtubeWebConfig() ?: return null
        val client = org.json.JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", config.clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        if (isValidVisitorData(config.visitorData)) {
            client.put("visitorData", config.visitorData)
        }
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("query", query)
        if (params != null) body.put("params", params)
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = java.net.URL(
                "$YOUTUBE_API/search?key=${config.apiKey}&prettyPrint=false"
            ).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", WEB_USER_AGENT)
            connection.setRequestProperty("Origin", YOUTUBE_ORIGIN)
            connection.setRequestProperty("Referer", "$YOUTUBE_ORIGIN/")
            connection.setRequestProperty("X-Goog-Api-Format-Version", "1")
            connection.setRequestProperty("X-YouTube-Client-Name", "1")
            connection.setRequestProperty("X-YouTube-Client-Version", config.clientVersion)
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Cookie", CONSENT_COOKIE)
            if (isValidVisitorData(config.visitorData)) {
                connection.setRequestProperty("X-Goog-Visitor-Id", config.visitorData)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) {
                null
            } else {
                org.json.JSONObject(connection.inputStream.bufferedReader().readText())
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Álbumes desde la búsqueda WEB: listas OLAK en
     *  playlistRenderer (playlistId) y en lockupViewModel
     *  (contentId), con título en title/metadata y miniatura;
     *  mismo molde OnlineCollectionResult. */
    private fun parseWebAlbums(root: org.json.JSONObject): List<OnlineCollectionResult> {
        val found = mutableListOf<Pair<String, org.json.JSONObject>>()
        collectWebAlbums(root, found)
        return found.mapNotNull { (playlistId, item) ->
            val title = webAlbumTitle(item).trim()
                .takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OnlineCollectionResult(
                kind = OnlineCollectionKind.ALBUM,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                title = title,
                subtitle = webAlbumSubtitle(item),
                thumbnailUrl = findThumbnail(item)
            )
        }.distinctBy { it.url }
    }

    private fun collectWebAlbums(
        node: Any?,
        out: MutableList<Pair<String, org.json.JSONObject>>
    ) {
        when (node) {
            is org.json.JSONObject -> {
                node.optJSONObject("playlistRenderer")?.let { renderer ->
                    renderer.optString("playlistId")
                        .takeIf { it.startsWith("OLAK") }
                        ?.let { out += it to renderer }
                }
                node.optJSONObject("lockupViewModel")?.let { lockup ->
                    lockup.optString("contentId")
                        .takeIf { it.startsWith("OLAK") }
                        ?.let { out += it to lockup }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectWebAlbums(node.opt(keys.next()), out)
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) collectWebAlbums(node.opt(i), out)
            }
        }
    }

    private fun webAlbumTitle(item: org.json.JSONObject): String {
        val fromTitle = runsText(item.optJSONObject("title")).trim()
        if (fromTitle.isNotBlank()) return fromTitle
        val metadata = item.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
            ?: item.optJSONObject("lockupMetadataViewModel")
        val metaTitle = metadata?.optJSONObject("title")
        val content = metaTitle?.optString("content").orEmpty().trim()
        if (content.isNotBlank()) return content
        return runsText(metaTitle).trim()
    }

    private fun webAlbumSubtitle(item: org.json.JSONObject): String {
        val byline = runsText(item.optJSONObject("longBylineText")).trim()
            .ifBlank { runsText(item.optJSONObject("shortBylineText")).trim() }
        if (byline.isNotBlank()) return byline
        val metadata = item.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
            ?: item.optJSONObject("lockupMetadataViewModel")
        val rows = metadata?.optJSONObject("metadata")
            ?.optJSONArray("metadataRows")
        if (rows != null) {
            val parts = mutableListOf<String>()
            for (i in 0 until rows.length()) {
                val partsArr = rows.optJSONObject(i)?.optJSONArray("metadataParts")
                if (partsArr != null) {
                    for (j in 0 until partsArr.length()) {
                        val text = partsArr.optJSONObject(j)
                            ?.optJSONObject("text")?.optString("content").orEmpty().trim()
                        if (text.isNotBlank()) parts += text
                    }
                }
            }
            val joined = parts.joinToString(" · ")
            if (joined.isNotBlank()) return joined
        }
        return ""
    }

    fun searchArtists(query: String): List<OnlineCollectionResult> {
        fun run(params: String?): List<OnlineCollectionResult> =
            searchRoot(query, params)?.let { parseArtists(it) } ?: emptyList()
        var results = run(ARTIST_SEARCH_FILTER)
        if (results.isEmpty()) results = run(null)
        return results.distinctBy { it.url }.take(8)
    }

    fun browseAlbumSongs(browseId: String): List<YTResult> {
        val config = webConfig()
        val body = org.json.JSONObject()
            .put("context", clientContext(config, clean = false))
            .put("browseId", browseId)
        val root = postJson("$MUSIC_API/browse?key=${config.apiKey}&prettyPrint=false", body, config)
            ?: return emptyList()
        return parseSongs(root).distinctBy { it.videoId }
    }

    // ---- Parseo de renderers de YouTube Music ----

    private fun collectRenderers(node: Any?, out: MutableList<org.json.JSONObject>) {
        when (node) {
            is org.json.JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { out += it }
                // La búsqueda WEB_REMIX con filtro de álbumes/artistas
                // devuelve tarjetas musicTwoRowItemRenderer (sin
                // flexColumns): sin recolectarlas, Álbumes salía vacío.
                node.optJSONObject("musicTwoRowItemRenderer")?.let { out += it }
                val keys = node.keys()
                while (keys.hasNext()) collectRenderers(node.opt(keys.next()), out)
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) collectRenderers(node.opt(i), out)
            }
        }
    }

    private fun renderers(root: org.json.JSONObject): List<org.json.JSONObject> {
        val out = mutableListOf<org.json.JSONObject>()
        collectRenderers(root, out)
        return out
    }

    private fun flexRuns(item: org.json.JSONObject): List<List<String>> {
        val columns = item.optJSONArray("flexColumns") ?: return emptyList()
        val result = mutableListOf<List<String>>()
        for (i in 0 until columns.length()) {
            val runs = columns.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            val texts = mutableListOf<String>()
            if (runs != null) {
                for (j in 0 until runs.length()) {
                    runs.optJSONObject(j)?.optString("text")?.let { texts += it }
                }
            }
            result += texts
        }
        return result
    }

    /** Texto de un bloque runs de musicTwoRowItemRenderer
     *  (title.runs / subtitle.runs); las flexColumns no aplican a
     *  ese renderer. */
    private fun runsText(block: org.json.JSONObject?): String {
        if (block == null) return ""
        val runs = block.optJSONArray("runs")
        if (runs != null) {
            val sb = StringBuilder()
            for (i in 0 until runs.length()) {
                sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
            return sb.toString()
        }
        return block.optString("simpleText")
    }

    private fun runsList(block: org.json.JSONObject?): List<String> {
        val runs = block?.optJSONArray("runs") ?: return emptyList()
        val texts = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            runs.optJSONObject(i)?.optString("text")?.let { texts += it }
        }
        return texts
    }

    /** browseId/playlistId desde el navigationEndpoint del item
     *  (en two-row vive ahí, no en flexColumns). */
    private fun navString(item: org.json.JSONObject, key: String): String? {
        item.optJSONObject("navigationEndpoint")?.let { nav ->
            findString(nav, key)?.let { return it }
        }
        val titleRuns = item.optJSONObject("title")?.optJSONArray("runs")
        if (titleRuns != null) {
            for (i in 0 until titleRuns.length()) {
                titleRuns.optJSONObject(i)?.optJSONObject("navigationEndpoint")?.let { nav ->
                    findString(nav, key)?.let { return it }
                }
            }
        }
        return findString(item, key)
    }

    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is org.json.JSONObject -> {
                val direct = node.optString(key)
                if (direct.isNotBlank()) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    findString(node.opt(keys.next()), key)?.let { return it }
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    findString(node.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findThumbnail(item: org.json.JSONObject): String? {
        fun walk(node: Any?): String? {
            when (node) {
                is org.json.JSONObject -> {
                    val thumbs = node.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        val url = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                        if (!url.isNullOrBlank()) return url
                    }
                    // lockupViewModel: image.sources[].url
                    val sources = node.optJSONArray("sources")
                    if (sources != null && sources.length() > 0) {
                        val url = sources.optJSONObject(sources.length() - 1)?.optString("url")
                        if (!url.isNullOrBlank() && url.startsWith("http")) return url
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        walk(node.opt(keys.next()))?.let { return it }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))?.let { return it }
                    }
                }
            }
            return null
        }
        return walk(item)
    }

    private fun parseDurationSec(runs: List<String>): Long {
        val match = runs.firstOrNull { it.trim().matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")) }
            ?: return 0L
        val parts = match.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private val typeLabels = setOf(
        "song", "video", "album", "single", "ep", "playlist", "artist",
        "canción", "álbum", "artista", "sencillo"
    )

    private fun parseSongs(root: org.json.JSONObject): List<YTResult> =
        renderers(root).mapNotNull { item ->
            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
                ?: findString(item, "videoId")
                ?: return@mapNotNull null
            val columns = flexRuns(item)
            val title = columns.getOrNull(0)?.joinToString("")?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val second = columns.getOrNull(1) ?: emptyList()
            val artist = second.firstOrNull {
                it.isNotBlank() && it.trim() != "•" && it.trim().lowercase() !in typeLabels
            }?.trim() ?: "Artista misterioso"
            YTResult(
                videoId = videoId,
                title = title,
                artist = artist,
                durationSec = parseDurationSec(second),
                thumbnailUrl = findThumbnail(item)
            )
        }

    private fun parseAlbums(root: org.json.JSONObject): List<OnlineCollectionResult> =
        renderers(root).mapNotNull { item ->
            val browseId = navString(item, "browseId")
            val playlistId = navString(item, "playlistId")
            val isAlbum = browseId?.startsWith("MPREb") == true ||
                playlistId?.startsWith("OLAK") == true
            if (!isAlbum) return@mapNotNull null
            val columns = flexRuns(item)
            // musicTwoRowItemRenderer: título en title.runs y
            // subtítulo en subtitle.runs (sin flexColumns).
            val title = (columns.getOrNull(0)?.joinToString("")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: runsText(item.optJSONObject("title")).trim().takeIf { it.isNotBlank() })
                ?: return@mapNotNull null
            val subtitleRuns = columns.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: runsList(item.optJSONObject("subtitle"))
            val subtitle = subtitleRuns
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "•" && it.lowercase() !in typeLabels }
                .joinToString(" · ")
            val url = when {
                playlistId?.startsWith("OLAK") == true ->
                    "https://music.youtube.com/playlist?list=$playlistId"
                browseId != null -> "https://music.youtube.com/browse/$browseId"
                else -> return@mapNotNull null
            }
            OnlineCollectionResult(
                kind = OnlineCollectionKind.ALBUM,
                url = url,
                title = title,
                subtitle = subtitle,
                thumbnailUrl = findThumbnail(item)
            )
        }

    private fun parseArtists(root: org.json.JSONObject): List<OnlineCollectionResult> =
        renderers(root).mapNotNull { item ->
            val browseId = navString(item, "browseId")
                ?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
            val columns = flexRuns(item)
            val title = (columns.getOrNull(0)?.joinToString("")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: runsText(item.optJSONObject("title")).trim().takeIf { it.isNotBlank() })
                ?: return@mapNotNull null
            OnlineCollectionResult(
                kind = OnlineCollectionKind.ARTIST,
                url = "https://music.youtube.com/channel/$browseId",
                title = title,
                subtitle = "Artista",
                thumbnailUrl = findThumbnail(item)
            )
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
        val progressive: Boolean = false,
        /** Identidad HTTP con la que se resolvió y sondeó la URL:
         *  viaja hasta Media3 para pedir googlevideo igual que
         *  en la sonda (adaptación LastWave). */
        val requestHeaders: Map<String, String> = BROWSER_HEADERS
    )

    private data class CachedUrl(
        val url: String,
        val expiresAtMs: Long,
        val diskKey: String,
        /** Bitrate real de la candidata elegida (KBPS, unidades
         *  de NewPipe): lo lee la pill de calidad del Now
         *  Playing. */
        val bitrateKbps: Int = 0,
        val requestHeaders: Map<String, String> = BROWSER_HEADERS
    )

    private val BROWSER_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedUrl>()

    /** Bitrate real (KBPS) del stream resuelto por video: la
     *  pill de calidad del Now Playing lo lee de aquí vía
     *  PlaybackManager. Se actualiza al resolver y al
     *  refrescar a otro formato; se olvida al invalidar. */
    private val streamBitrates = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Bitrate del stream ya resuelto de ese video, o null si
     *  aún no se resolvió. */
    fun streamBitrateKbps(videoId: String): Int? = streamBitrates[videoId]

    /** Limpia URLs y bitrates cacheados: al cambiar la calidad
     *  en Ajustes, la próxima resolución ya usa la calidad
     *  nueva. La canción que suena no se reinicia. */
    fun clearAll() {
        cache.clear()
        streamBitrates.clear()
    }

    // Medición de conexión para el indicador del Now Playing:
    // throughput real de las cargas del stream (sin gastar
    // datos extra), suavizado con media móvil para que no
    // parpadee. Se reinicia al cambiar de video: sin muestra
    // del video actual, el indicador no se muestra.
    private val connectionLock = Any()

    /** Muestras suavizadas por video: la siguiente canción no
     *  borra la medición de la que está sonando. */
    private val connectionSamples =
        java.util.concurrent.ConcurrentHashMap<String, Double>()

    /** Anota una carga completada del stream de ese video.
     *  Cargas muy cortas (lecturas de caché en disco) no
     *  cuentan: no miden la red. */
    fun noteConnectionLoad(videoId: String, bytesLoaded: Long, loadDurationMs: Long) {
        if (videoId.isBlank() || bytesLoaded < 32L * 1024L || loadDurationMs < 150L) return
        noteConnectionSample(videoId, bytesLoaded, loadDurationMs)
    }

    /** Anota una ventana de lectura de red (~1 s o >=256 KB)
     *  medida durante la reproducción: la pill aparece a los
     *  pocos segundos sin esperar a que una carga complete. */
    fun noteConnectionWindow(videoId: String, bytesLoaded: Long, windowMs: Long) {
        if (videoId.isBlank() || bytesLoaded < 16L * 1024L || windowMs < 50L) return
        noteConnectionSample(videoId, bytesLoaded, windowMs)
    }

    private fun noteConnectionSample(videoId: String, bytesLoaded: Long, durationMs: Long) {
        val sampleKbps = bytesLoaded * 8.0 / durationMs
        synchronized(connectionLock) {
            val previous = connectionSamples[videoId]
            connectionSamples[videoId] =
                if (previous == null) sampleKbps
                else previous * 0.7 + sampleKbps * 0.3
        }
    }

    /** Velocidad suavizada (kbps) del stream de ese video, o
     *  null si aún no hay muestra de ese video. */
    fun connectionKbps(videoId: String): Int? =
        connectionSamples[videoId]?.toInt()

    /** Candado por video: principal, fundido y precarga solo se
     *  turnan cuando resuelven la MISMA canción; canciones
     *  distintas resuelven en paralelo (el candado global
     *  anterior serializaba todo y se sentía como lag/ANR). */
    private val videoLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun lockFor(videoId: String): Any =
        videoLocks.computeIfAbsent(videoId) { Any() }
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

    fun resolveStream(videoId: String, preferQuality: Boolean): ResolvedStream? =
        synchronized(lockFor(videoId)) {
            resolveStreamInternal(videoId, preferQuality, forceFresh = false, excludeUrl = null)
        }

    /** Re-resuelve con StreamInfo fresco tras un 403/410 o corte de
     *  googlevideo, excluyendo la URL que falló. */
    fun refreshStream(
        videoId: String,
        preferQuality: Boolean,
        failedUrl: String?,
        failedKey: String? = null
    ): ResolvedStream? = synchronized(lockFor(videoId)) {
        if (!failedUrl.isNullOrBlank()) lastFailedUrls[videoId] = failedUrl
        resolveStreamInternal(
            videoId, preferQuality, forceFresh = true,
            excludeUrl = failedUrl, sameFormatKey = formatKeyOf(failedKey)
        )
    }

    /** Formato/bitrate de una clave de caché: el refresco busca
     *  primero el MISMO formato para que los offsets ya
     *  bufferizados sigan válidos. */
    private fun formatKeyOf(diskKey: String?): String? {
        val parts = diskKey?.split(":") ?: return null
        return if (parts.size >= 5) parts[3] + ":" + parts[4] else null
    }

    private fun resolveStreamInternal(
        videoId: String,
        preferQuality: Boolean,
        forceFresh: Boolean,
        excludeUrl: String?,
        sameFormatKey: String? = null
    ): ResolvedStream? {
        if (videoId.isBlank()) return null
        val now = System.currentTimeMillis()
        val effectiveQuality = preferQuality && now >= temporarySaverUntilMs
        val cacheKey = "yt:$videoId:${if (effectiveQuality) "quality" else "saver"}"
        val effectiveExclude = excludeUrl ?: lastFailedUrls[videoId]
        if (!forceFresh) {
            cache[cacheKey]?.let { cached ->
                if (cached.expiresAtMs > now + 30_000L && cached.url != effectiveExclude) {
                    // La URL cacheada se entrega directo, sin
                    // re-sonda: sondearla en cada apertura leía
                    // 1 MB de red y serializaba la carga. Si la
                    // red cambió y la URL falla al sonar, el
                    // refresco 403/410 la invalida y re-resuelve.
                    if (cached.bitrateKbps > 0) {
                        streamBitrates[videoId] = cached.bitrateKbps
                    }
                    return ResolvedStream(
                        cached.url,
                        diskKeyWithMode(cached.diskKey, false),
                        false,
                        cached.requestHeaders
                    )
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
            // Motor simple (decisión de Dahel 2026-11-26): NewPipe
            // resuelve y Media3 reproduce directo en M4A. Sin
            // escalera InnerTube ni Opus primero: un reintento
            // fresco excluyendo la URL fallida lo hace
            // PlaybackManager. Arranque rápido (Dahel
            // 2026-11-26): la primera candidata se entrega SIN
            // sonda — sondearla bajaba 512 KB que se botaban y
            // la primera canción tardaba 10–20 s en sonar; si
            // googlevideo la rechaza (403/410/corte), el
            // refresco existente la invalida y re-resuelve
            // excluyéndola. La sonda corta queda solo para las
            // candidatas de respaldo.
            val candidates = candidateStreams(streamInfo, effectiveQuality)
                .sortedByDescending {
                    sameFormatKey != null &&
                        "${it.format}:${it.averageBitrate}" == sameFormatKey
                }
            var considered = 0
            for (audioStream in candidates.take(3)) {
                val url = audioStream.content ?: continue
                if (url == effectiveExclude) continue
                considered++
                if (considered > 1 && !probeCandidate(url, BROWSER_HEADERS)) continue
                val baseDiskKey = "$cacheKey:${audioStream.format}:${audioStream.averageBitrate}"
                val diskKey = diskKeyWithMode(baseDiskKey, false)
                cache[cacheKey] = CachedUrl(
                    url, expiryMs(url, now), diskKey, audioStream.averageBitrate
                )
                streamBitrates[videoId] = audioStream.averageBitrate
                lastFailedUrls.remove(videoId)
                return ResolvedStream(url, diskKey, false)
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

    /** Clave de caché con modo: el motor simple solo usa
     *  directo; el sufijo se conserva para que las claves
     *  viejas con :prog no se mezclen con las directas. */
    private fun diskKeyWithMode(diskKey: String, progressive: Boolean): String {
        val base = diskKey.removeSuffix(":direct").removeSuffix(":prog")
        return base + if (progressive) ":prog" else ":direct"
    }

    /** Prueba corta de una URL de respaldo: misma identidad
     *  HTTP que reproducirá Media3 y solo los primeros bytes
     *  del rango inicial. La primera candidata ya no se
     *  sondea (arranque rápido); esto solo filtra respaldos
     *  muertos sin pagar una descarga que se bota. */
    private fun probeCandidate(url: String, headers: Map<String, String>): Boolean =
        probeRange(url, 0L, headers).ok

    private data class ProbeRangeResult(val ok: Boolean, val totalLength: Long?)

    /** Lee como máximo [PROBE_BYTES] del rango pedido (o
     *  hasta el fin del archivo si es menor): ok solo si
     *  llegan todos los bytes pedidos. */
    private fun probeRange(url: String, start: Long, headers: Map<String, String>): ProbeRangeResult {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = openProbeConnection(url, "bytes=$start-${start + PROBE_BYTES - 1}", headers)
            val code = connection.responseCode
            if (code !in 200..299) {
                ProbeRangeResult(false, null)
            } else {
                val total = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast("/")
                    ?.toLongOrNull()
                val expected = if (total != null) {
                    minOf(PROBE_BYTES, (total - start).coerceAtLeast(0L))
                } else {
                    PROBE_BYTES
                }
                var readTotal = 0L
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (readTotal < expected) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expected - readTotal).toInt())
                        if (read < 0) break
                        readTotal += read
                    }
                }
                ProbeRangeResult(readTotal >= expected && expected > 0L, total)
            }
        } catch (_: Exception) {
            ProbeRangeResult(false, null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openProbeConnection(
        url: String,
        range: String,
        headers: Map<String, String>
    ): java.net.HttpURLConnection {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.setRequestProperty("Range", range)
        return connection
    }

    fun invalidate(videoId: String) {
        synchronized(lockFor(videoId)) {
            cache.keys.removeAll { it.startsWith("yt:$videoId:") }
            streamBitrates.remove(videoId)
        }
    }

    /** Marca la URL cacheada de ese video como fallida: el
     *  reintento de PlaybackManager re-resuelve con StreamInfo
     *  fresco excluyéndola, conservando la posición. */
    fun markFailed(videoId: String) {
        synchronized(lockFor(videoId)) {
            val cached = cache.entries.firstOrNull { it.key.startsWith("yt:$videoId:") }?.value
            if (cached != null) lastFailedUrls[videoId] = cached.url
            invalidate(videoId)
        }
    }

    /** La canción suena: se olvida la URL fallida de ese video. */
    fun clearFailed(videoId: String) {
        synchronized(lockFor(videoId)) {
            lastFailedUrls.remove(videoId)
        }
    }

    private val lastFailedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Candidatas M4A para streaming (bitrate NewPipe en KBPS:
     *  ahorro 48, calidad 128). En Calidad: 128, luego 256 y
     *  48 solo como último recurso — el orden ascendente
     *  anterior hacía que un fallo de la URL de 128 cayera a
     *  48 y ahí se quedaba. En Ahorro: 48 primero y
     *  ascendentes. Las descargas usan su propia selección y
     *  no se tocan. */
    private fun candidateStreams(
        streamInfo: StreamInfo,
        preferQuality: Boolean
    ): List<org.schabi.newpipe.extractor.stream.AudioStream> {
        val m4a = streamInfo.audioStreams
            .filter { it.format == MediaFormat.M4A }
        val ordered = if (preferQuality) {
            m4a.filter { it.averageBitrate >= 128 }.sortedBy { it.averageBitrate } +
                m4a.filter { it.averageBitrate < 128 }.sortedByDescending { it.averageBitrate }
        } else {
            m4a.sortedBy { it.averageBitrate }
        }
        return ordered.distinctBy { it.content }
    }

    /** Sonda corta de respaldos: 16 KB bastan para saber si
     *  googlevideo acepta la URL; la sonda de 512 KB se
     *  descargaba y se botaba en cada arranque. */
    private const val PROBE_BYTES = 16L * 1024L

    private fun expiryMs(url: String, nowMs: Long): Long {
        val expireSec = Regex("[?&]expire=(\\d+)").find(url)
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        return if (expireSec != null && expireSec > 0L) expireSec * 1000L
        else nowMs + 5 * 60_000L
    }
}
