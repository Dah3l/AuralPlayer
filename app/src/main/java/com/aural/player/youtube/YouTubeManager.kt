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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                            YTResult(
                                videoId = extractVideoId(item.url),
                                title = item.name ?: "Sin título",
                                artist = item.uploaderName ?: "Desconocido",
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
    fun download(result: YTResult, onComplete: (Boolean) -> Unit) {
        if (_downloading.value.contains(result.videoId)) return
        _downloading.value = _downloading.value + result.videoId
        _progress.value = _progress.value + (result.videoId to 0f)
        _error.value = null
        scope.launch {
            var success = false
            var errorMsg: String? = null
            try {
                val service: StreamingService = NewPipe.getService("YouTube")
                val streamInfo = StreamInfo.getInfo(
                    service,
                    "https://www.youtube.com/watch?v=${result.videoId}"
                )
                android.util.Log.d("YouTubeManager",
                    "Audios: ${streamInfo.audioStreams.size}, Videos: ${streamInfo.videoStreams.size}")

                // SOLO audio puro — sin fallback a video
                // Priorizar m4a (mejor compatibilidad), luego el de menor bitrate decente
                val audioStream = streamInfo.audioStreams
                    .sortedWith(
                        compareBy(
                            { !it.content.contains("mime=audio/mp4") }, // m4a primero
                            { it.averageBitrate } // luego menor bitrate = descarga rápida
                        )
                    )
                    .firstOrNull { it.averageBitrate >= 48000 } // mínimo 48kbps
                    ?: streamInfo.audioStreams.minByOrNull { it.averageBitrate }

                if (audioStream != null) {
                    val ext = when {
                        audioStream.content.contains("mime=audio/mp4") -> "m4a"
                        audioStream.content.contains("mime=audio/webm") -> "webm"
                        else -> "m4a"
                    }
                    val fileName = sanitize("${result.artist} - ${result.title}.$ext")
                    success = downloadToMusic(audioStream.content, fileName, result) { pct ->
                        _progress.value = _progress.value + (result.videoId to pct)
                    }
                    if (!success) errorMsg = "No se pudo guardar el archivo"
                } else {
                    errorMsg = "Sin audio disponible para este video"
                    android.util.Log.e("YouTubeManager", errorMsg)
                }
            } catch (e: Exception) {
                errorMsg = "Error: ${e.message?.take(100)}"
                android.util.Log.e("YouTubeManager", "Error descargando: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                _downloading.value = _downloading.value - result.videoId
                _progress.value = _progress.value - result.videoId
                _error.value = errorMsg
                onComplete(success)
            }
        }
    }

    private fun sanitize(name: String): String {
        return name.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .replace("?", "_").replace("*", "_").replace("\"", "_")
            .take(100)
    }

    private fun downloadToMusic(
        url: String,
        fileName: String,
        result: YTResult,
        onProgress: (Float) -> Unit
    ): Boolean {
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
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    downloadWithProgress(url, out, result.videoId, onProgress)
                }
            } else {
                @Suppress("DEPRECATION")
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                val auralDir = File(musicDir, "Aural")
                auralDir.mkdirs()
                val destFile = File(auralDir, fileName)
                destFile.outputStream().use { out ->
                    downloadWithProgress(url, out, result.videoId, onProgress)
                }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf("audio/mp4"), null
                )
            }
            android.util.Log.d("YouTubeManager", "Guardado: $fileName")
            true
        } catch (e: Exception) {
            android.util.Log.e("YouTubeManager", "Error guardando: ${e.message}")
            false
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

    fun clearResults() {
        _results.value = emptyList()
    }
}
