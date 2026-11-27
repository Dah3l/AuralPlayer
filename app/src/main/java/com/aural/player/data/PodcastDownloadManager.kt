// Descargas de episodios de podcast: mismo patrón que las
// descargas de YouTube (MediaStore en Música/Aural, progreso
// observable y timeouts), pero marcados como podcast para no
// entrar en el escaneo musical. El registro vive en
// SettingsStore (podcast_downloads_json).
package com.aural.player.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PodcastDownloadManager(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadingKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadingKeys: StateFlow<Set<String>> = _downloadingKeys.asStateFlow()

    /** Progreso por clave de episodio: 0.0 a 1.0. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun download(show: PodcastShow, episode: PodcastEpisode) {
        val key = PodcastEpisode.progressKey(show.feedUrl, episode.guid)
        if (key in _downloadingKeys.value) return
        _downloadingKeys.value = _downloadingKeys.value + key
        _progress.value = _progress.value + (key to 0f)
        val notificationId = DownloadNotifications.idFor("podcast:$key")
        DownloadNotifications.progress(appContext, notificationId, episode.title, 0)
        scope.launch {
            var lastPercent = -1
            try {
                val download = downloadEpisode(show, episode, key) { pct ->
                    _progress.value = _progress.value + (key to pct)
                    val percent = (pct * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        DownloadNotifications.progress(
                            appContext, notificationId, episode.title, percent
                        )
                    }
                }
                if (download != null) {
                    settings.setPodcastDownload(key, download)
                    DownloadNotifications.complete(
                        appContext, notificationId, episode.title
                    )
                } else {
                    DownloadNotifications.error(
                        appContext, notificationId, episode.title
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "PodcastDownloadManager",
                    "Error descargando episodio: ${e.message}",
                    e
                )
                DownloadNotifications.error(
                    appContext, notificationId, episode.title
                )
            } finally {
                _downloadingKeys.value = _downloadingKeys.value - key
                _progress.value = _progress.value - key
            }
        }
    }

    /** Borra el archivo local de una descarga registrada. */
    suspend fun deleteDownload(download: PodcastDownload) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(download.uri)
                when (uri.scheme) {
                    "content" -> appContext.contentResolver.delete(uri, null, null)
                    "file" -> uri.path?.let { File(it).delete() }
                    else -> File(download.uri).delete()
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "PodcastDownloadManager",
                    "Error borrando descarga: ${e.message}",
                    e
                )
            }
        }
    }

    private fun downloadEpisode(
        show: PodcastShow,
        episode: PodcastEpisode,
        key: String,
        onProgress: (Float) -> Unit
    ): PodcastDownload? {
        val mimeType = mimeTypeFor(episode)
        val fileName = sanitize("${show.title} - ${episode.title}.${extensionFor(episode, mimeType)}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Aural/Podcasts")
                put(MediaStore.Audio.Media.TITLE, episode.title)
                put(MediaStore.Audio.Media.ARTIST, show.title)
                put(MediaStore.Audio.Media.ALBUM, show.author)
                put(MediaStore.Audio.Media.IS_PODCAST, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                if (episode.durationMs > 0L) {
                    put(MediaStore.Audio.Media.DURATION, episode.durationMs)
                }
            }
            val uri = appContext.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            try {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    downloadWithProgress(episode, key, out, onProgress)
                } ?: throw IOException("Sin salida para guardar el episodio")
            } catch (e: Exception) {
                runCatching { appContext.contentResolver.delete(uri, null, null) }
                throw e
            }
            PodcastDownload(
                uri = uri.toString(),
                mediaId = ContentUris.parseId(uri),
                fileName = fileName,
                downloadedAtMs = System.currentTimeMillis()
            )
        } else {
            @Suppress("DEPRECATION")
            val podcastsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PODCASTS
            )
            val auralDir = File(podcastsDir, "Aural")
            auralDir.mkdirs()
            val destFile = File(auralDir, fileName)
            destFile.outputStream().use { out ->
                downloadWithProgress(episode, key, out, onProgress)
            }
            var scannedUri: Uri? = null
            val latch = CountDownLatch(1)
            MediaScannerConnection.scanFile(
                appContext, arrayOf(destFile.absolutePath), arrayOf(mimeType)
            ) { _, uri ->
                scannedUri = uri
                latch.countDown()
            }
            latch.await(3, TimeUnit.SECONDS)
            val uri = scannedUri ?: Uri.fromFile(destFile)
            PodcastDownload(
                uri = uri.toString(),
                mediaId = if (uri.scheme == "content") {
                    runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
                } else -1L,
                fileName = fileName,
                downloadedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun downloadWithProgress(
        episode: PodcastEpisode,
        key: String,
        out: OutputStream,
        onProgress: (Float) -> Unit
    ) {
        val connection = URL(episode.audioUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "AuralPlayer/1.3.0 (Android)")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Range", "bytes=0-")
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong.takeIf { it > 0L }
            ?: episode.audioBytes
        var downloaded = 0L
        val buffer = ByteArray(65536)
        connection.inputStream.use { input ->
            while (true) {
                if (key !in _downloadingKeys.value) {
                    throw IOException("Descarga cancelada")
                }
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                downloaded += read
                if (total > 0L) {
                    onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
        onProgress(1f)
    }

    private fun mimeTypeFor(episode: PodcastEpisode): String {
        val declared = episode.audioMimeType.substringBefore(";").trim()
        if (declared.startsWith("audio/")) return declared
        return when (extensionFromUrl(episode.audioUrl)) {
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            else -> "audio/mpeg"
        }
    }

    private fun extensionFor(episode: PodcastEpisode, mimeType: String): String {
        val fromUrl = extensionFromUrl(episode.audioUrl)
        if (fromUrl in setOf("mp3", "m4a", "mp4", "ogg", "opus", "wav", "aac")) {
            return fromUrl
        }
        return when (mimeType) {
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/aac" -> "aac"
            else -> "mp3"
        }
    }

    private fun extensionFromUrl(url: String): String =
        url.substringBefore("?").substringAfterLast(".", "")
            .lowercase().takeIf { it.length in 2..5 } ?: ""

    private fun sanitize(name: String): String =
        name.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .replace("?", "_").replace("*", "_").replace("\"", "_")
            .take(120)
}
