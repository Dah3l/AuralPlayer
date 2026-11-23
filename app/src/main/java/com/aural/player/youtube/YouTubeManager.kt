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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aural.player.data.SettingsStore
import com.aural.player.data.TagEditor
import com.aural.player.data.TagWriteResult
import com.aural.player.shared.YoutubeTitleParser
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.StreamingService
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
                val preferQuality = try {
                    SettingsStore(context).downloadQuality.first() == "quality"
                } catch (_: Exception) {
                    false
                }
                val minBitrate = if (preferQuality) 128000 else 48000
                val audioStream = streamInfo.audioStreams
                    .sortedWith(
                        compareBy(
                            { !it.content.contains("mime=audio/mp4") },
                            { it.averageBitrate }
                        )
                    )
                    .firstOrNull { it.averageBitrate >= minBitrate }
                    // Fallback: el mejor m4a (para no perder las
                    // etiquetas) y si no hay, el mejor disponible
                    // (antes era minByOrNull = el peor; en PC 1.2.22
                    // ya se cambió a maxByOrNull).
                    ?: streamInfo.audioStreams
                        .filter { it.content.contains("mime=audio/mp4") }
                        .maxByOrNull { it.averageBitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

                if (audioStream != null) {
                    val ext = when {
                        audioStream.content.contains("mime=audio/mp4") -> "m4a"
                        audioStream.content.contains("mime=audio/webm") -> "webm"
                        else -> "m4a"
                    }
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
