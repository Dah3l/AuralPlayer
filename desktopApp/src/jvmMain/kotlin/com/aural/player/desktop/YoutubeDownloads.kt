// Búsqueda y descarga de audio de YouTube en PC, vía NewPipeExtractor
// (misma librería y misma lógica que el móvil: YouTubeManager.kt).
// Sin API key: NewPipe habla directo con YouTube. El audio baja crudo
// (DASH, m4a/webm sin remux, igual que en el móvil) a la primera
// carpeta de la biblioteca; el re-escaneo lo recoge como una canción
// más. Los tags de un MP4 fragmentado no se pueden editar (ver
// DesktopTagEditor): la descarga en sí suena sin problema en VLC.
package com.aural.player.desktop

import com.aural.player.shared.YoutubeResult
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Downloader de NewPipe por HttpURLConnection (puerto del móvil). */
class YoutubeDownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = request.httpMethod()
            connection.instanceFollowRedirects = true
            // Headers de navegador real para evitar detección
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            for ((key, values) in request.headers()) {
                for (value in values) {
                    // No sobrescribir el User-Agent que ya pusimos
                    if (!key.equals("User-Agent", ignoreCase = true)) {
                        connection.setRequestProperty(key, value)
                    }
                }
            }
            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }
            val code = connection.responseCode
            val message = connection.responseMessage ?: ""
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { it.value }
            return Response(code, message, headers, body, request.url())
        } catch (e: IOException) {
            throw IOException("Error en request a ${request.url()}: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }
}

/** Motor de búsqueda y descarga de YouTube (NewPipeExtractor). */
class YoutubeEngine {

    init {
        try {
            NewPipe.init(YoutubeDownloaderImpl())
        } catch (_: Exception) {
            // Ya inicializado
        }
    }

    /** Busca en YouTube y devuelve hasta 20 resultados de audio. */
    suspend fun search(query: String): List<YoutubeResult> = withContext(Dispatchers.IO) {
        val service = NewPipe.getService("YouTube")
        val searchInfo: SearchInfo = SearchInfo.getInfo(
            service,
            service.searchQHFactory.fromQuery(query)
        )
        searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(20)
            .mapNotNull { item ->
                try {
                    YoutubeResult(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "Sin título",
                        uploader = item.uploaderName ?: "Artista misterioso",
                        durationSec = item.duration,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url
                    )
                } catch (_: Exception) {
                    null
                }
            }
    }

    /**
     * Descarga el audio de un video a [destDir]. Devuelve el archivo
     * guardado o null si falló o se canceló. `isCancelled` se consulta
     * en cada bloque: si devuelve true, se aborta y se borra el parcial.
     */
    suspend fun download(
        videoId: String,
        title: String,
        uploader: String,
        destDir: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        var dest: File? = null
        try {
            val service = NewPipe.getService("YouTube")
            val streamInfo = StreamInfo.getInfo(
                service,
                "https://www.youtube.com/watch?v=$videoId"
            )
            // SOLO audio puro — sin fallback a video (igual que el móvil)
            val audioStream = streamInfo.audioStreams
                .sortedWith(
                    compareBy(
                        { !it.content.contains("mime=audio/mp4") },
                        { it.averageBitrate }
                    )
                )
                .firstOrNull { it.averageBitrate >= 48000 }
                ?: streamInfo.audioStreams.minByOrNull { it.averageBitrate }
                ?: return@withContext null
            val ext = when {
                audioStream.content.contains("mime=audio/mp4") -> "m4a"
                audioStream.content.contains("mime=audio/webm") -> "webm"
                else -> "m4a"
            }
            val fileName = sanitize("$uploader - $title.$ext")
            destDir.mkdirs()
            dest = File(destDir, fileName)
            dest.outputStream().use { out ->
                downloadWithProgress(audioStream.content, out, onProgress, isCancelled)
            }
            dest
        } catch (_: Exception) {
            // Sin parcial a medias en la biblioteca.
            dest?.let { if (it.exists()) it.delete() }
            null
        }
    }

    private fun sanitize(name: String): String {
        return name.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .replace("?", "_").replace("*", "_").replace("\"", "_")
            .take(100)
    }

    private fun extractVideoId(url: String?): String {
        if (url == null) return ""
        // Formato: https://www.youtube.com/watch?v=VIDEO_ID
        val regex = "[?&]v=([^&]+)".toRegex()
        return regex.find(url)?.groupValues?.get(1) ?: url.substringAfterLast("/")
    }

    private fun downloadWithProgress(
        url: String,
        out: OutputStream,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        // Headers de navegador
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
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
                if (isCancelled()) {
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
        else throw IOException("Descarga cancelada por el usuario")
    }
}
