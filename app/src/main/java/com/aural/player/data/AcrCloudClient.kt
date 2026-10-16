// Reconocimiento de música por audio vía ACRCloud (API HTTP, sin SDK nativo)
package com.aural.player.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Credenciales de ACRCloud.
 *
 * PARA ACTIVAR:
 * 1. Crea una cuenta gratuita en https://www.acrcloud.com/
 * 2. Crea un proyecto de tipo "Audio Identification"
 * 3. Pega aquí el Host, Access Key y Access Secret de tu proyecto
 */
object AcrCloudConfig {
    // Host de tu proyecto (ej: identify-eu-west-1.acrcloud.com)
    const val HOST = "identify-eu-west-1.acrcloud.com"
    const val ACCESS_KEY = ""
    const val ACCESS_SECRET = ""

    val isConfigured: Boolean
        get() = ACCESS_KEY.isNotBlank() && ACCESS_SECRET.isNotBlank()
            && !HOST.contains("ejemplo")
}

/** Canción identificada por ACRCloud. */
data class RecognizedTrack(
    val title: String,
    val artist: String,
    val album: String,
    val score: Int
)

/**
 * Cliente HTTP de ACRCloud. Envía el audio grabado y devuelve la canción
 * identificada, o null si no hay coincidencia o falla la petición.
 * No sube metadatos del usuario, solo el fragmento de audio.
 */
object AcrCloudClient {

    /**
     * Identifica la canción del archivo de audio dado.
     * El archivo debe ser un fragmento de ~8-12 segundos.
     */
    suspend fun identify(audioFile: File): RecognizedTrack? = withContext(Dispatchers.IO) {
        if (!AcrCloudConfig.isConfigured) return@withContext null
        if (!audioFile.exists() || audioFile.length() == 0L) return@withContext null
        try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val signature = hmacSha1Base64(timestamp, AcrCloudConfig.ACCESS_SECRET)
            val boundary = "----AuralBoundary${UUID.randomUUID()}"

            val conn = (URL("https://${AcrCloudConfig.HOST}/v1/identify")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty(
                    "Content-Type", "multipart/form-data; boundary=$boundary"
                )
            }

            conn.outputStream.use { out ->
                fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                fun field(name: String, value: String) {
                    text("--$boundary\r\n")
                    text("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    text("$value\r\n")
                }
                field("access_key", AcrCloudConfig.ACCESS_KEY)
                field("data_type", "audio")
                field("signature_version", "1")
                field("signature", signature)
                field("timestamp", timestamp)
                field("sample_bytes", audioFile.length().toString())
                text("--$boundary\r\n")
                text("Content-Disposition: form-data; name=\"sample\"; filename=\"sample.m4a\"\r\n")
                text("Content-Type: audio/mp4\r\n\r\n")
                audioFile.inputStream().use { it.copyTo(out) }
                text("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            if (code != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            parseResponse(body)
        } catch (e: Exception) {
            android.util.Log.e("AcrCloudClient", "Error identificando: ${e.message}")
            null
        }
    }

    private fun hmacSha1Base64(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun parseResponse(body: String): RecognizedTrack? {
        val json = JSONObject(body)
        if (json.optJSONObject("status")?.optInt("code") != 0) return null
        val music = json.optJSONObject("metadata")
            ?.optJSONArray("music")?.optJSONObject(0) ?: return null
        val title = music.optString("title").ifBlank { return null }
        val artistsArray = music.optJSONArray("artists")
        val artist = if (artistsArray != null) {
            (0 until artistsArray.length())
                .map { artistsArray.optJSONObject(it)?.optString("name").orEmpty() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        } else ""
        val album = music.optJSONObject("album")?.optString("name").orEmpty()
        val score = music.optInt("score")
        return RecognizedTrack(title, artist, album, score)
    }
}
