// Cliente de reconocimiento vía Shazam (sin cuenta ni API key).
//
// Genera la firma acústica en el dispositivo y la envía al endpoint público
// de Shazam. Basado en opentune (https://github.com/justshivv/opentune),
// que sigue el protocolo documentado por ShazamIO y SongRec.
package com.aural.player.data.shazam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.UUID

/** Canción identificada por Shazam. */
data class ShazamTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val shazamUrl: String? = null
)

/** Resultado detallado del intento de reconocimiento (para depuración). */
sealed interface ShazamResult {
    data class Found(val track: ShazamTrack) : ShazamResult
    data class NoMatch(val detail: String) : ShazamResult
    data class Error(val detail: String) : ShazamResult
}

object ShazamClient {

    private const val TAG = "ShazamClient"
    private const val TIMEOUT_MS = 15_000

    // Endpoint de Android (como opentune), no de iPhone
    private const val BASE = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag"
    private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240205.004)"

    /** Diagnóstico del último intento (para depuración). */
    var lastDiagnostic: String = ""
        private set

    /**
     * Identifica la canción a partir de muestras PCM mono de 16 bits a 16 kHz.
     * Genera la firma y hace una sola consulta (para compatibilidad).
     */
    suspend fun recognize(pcm16khz: ShortArray): ShazamResult = withContext(Dispatchers.IO) {
        val gen = ShazamSignatureGenerator()
        gen.feedSamples(pcm16khz)
        lookup(gen)
    }

    /**
     * Consulta a Shazam con un generador de firma ya alimentado.
     * Permite búsquedas incrementales: alimentar más muestras y volver a llamar.
     */
    suspend fun lookup(gen: ShazamSignatureGenerator): ShazamResult = withContext(Dispatchers.IO) {
        try {
            val uri = gen.encodeToUri()
            val sampleMs = gen.sampleMs()
            Log.d(TAG, "Firma: ${uri.length} chars, ${sampleMs}ms")

            val diag = StringBuilder()
            diag.appendLine("=== DIAGNÓSTICO SHAZAM ===")
            diag.appendLine("Duración: ${sampleMs}ms")
            diag.appendLine("Firma: ${uri.length} chars")
            diag.appendLine("URI inicio: ${uri.take(80)}")

            val uuid1 = UUID.randomUUID().toString().uppercase()
            val uuid2 = UUID.randomUUID().toString().uppercase()
            val urlStr = "$BASE/$uuid1/$uuid2" +
                "?sync=true&webv3=true&sampling=true&connected=" +
                "&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1" +
                "&hidelb=true&video=v3"

            val body = JSONObject().apply {
                put("timezone", TimeZone.getDefault().id)
                put("signature", JSONObject().apply {
                    put("uri", uri)
                    put("samplems", sampleMs)
                })
                put("timestamp", System.currentTimeMillis())
                put("context", JSONObject())
                put("geolocation", JSONObject())
            }.toString()

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Content-Language", "en_US")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "HTTP $code")
            diag.appendLine("HTTP: $code")

            if (code != 200) {
                val errBody = runCatching {
                    conn.errorStream?.bufferedReader()?.readText()?.take(500)
                }.getOrNull()
                diag.appendLine("Error: $errBody")
                lastDiagnostic = diag.toString()
                return@withContext ShazamResult.Error("HTTP $code: ${errBody ?: "sin detalle"}")
            }

            val respText = conn.inputStream.bufferedReader().readText()
            diag.appendLine("Respuesta: ${respText.take(500)}")
            lastDiagnostic = diag.toString()

            parseResponse(respText, diag)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            lastDiagnostic = "Excepción: ${e.javaClass.simpleName}: ${e.message}"
            ShazamResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseResponse(json: String, diag: StringBuilder): ShazamResult {
        return try {
            val root = JSONObject(json)
            val track = root.optJSONObject("track")
                ?: return ShazamResult.NoMatch("Sin track en respuesta")

            val title = track.optString("title").takeIf { it.isNotBlank() }
                ?: return ShazamResult.NoMatch("Sin título en respuesta")

            // Artista está en "subtitle" según opentune
            val artist = track.optString("subtitle").ifBlank { "Artista misterioso" }

            // Álbum: buscar en sections -> metadata donde title == "Album"
            var album = ""
            val sections = track.optJSONArray("sections")
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val metadata = sections.optJSONObject(i)?.optJSONArray("metadata")
                        ?: continue
                    for (j in 0 until metadata.length()) {
                        val item = metadata.optJSONObject(j) ?: continue
                        if (item.optString("title").equals("Album", ignoreCase = true)) {
                            album = item.optString("text")
                            break
                        }
                    }
                    if (album.isNotBlank()) break
                }
            }

            val images = track.optJSONObject("images")
            val cover = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                ?: images?.optString("coverart")?.takeIf { it.isNotBlank() }

            val url = track.optString("url")?.takeIf { it.isNotBlank() }

            diag.appendLine("Encontrado: $title - $artist")
            lastDiagnostic = diag.toString()
            ShazamResult.Found(ShazamTrack(title, artist, album, cover, url))
        } catch (e: Exception) {
            diag.appendLine("Error parseando: ${e.message}")
            lastDiagnostic = diag.toString()
            ShazamResult.Error("Error parseando: ${e.message}")
        }
    }
}
