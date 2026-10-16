// Cliente de reconocimiento vía Shazam (sin cuenta ni API key).
//
// Genera la firma acústica en el dispositivo y la envía al endpoint público
// de Shazam. Basado en el protocolo documentado por ShazamIO:
// https://github.com/shazamio/ShazamIO
package com.aural.player.data.shazam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Canción identificada por Shazam. */
data class ShazamTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val shazamId: String? = null
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

    /** Diagnóstico del último intento (para depuración). */
    var lastDiagnostic: String = ""
        private set

    /**
     * Identifica la canción a partir de muestras PCM mono de 16 bits a 16 kHz.
     * Se recomiendan ~12 segundos (192000 muestras).
     */
    suspend fun recognize(pcm16khz: ShortArray): ShazamResult = withContext(Dispatchers.IO) {
        try {
            if (pcm16khz.size < ShazamSignatureGenerator.SAMPLE_RATE_HZ * 3) {
                lastDiagnostic = "Audio muy corto (${pcm16khz.size} muestras)"
                return@withContext ShazamResult.Error(
                    "Audio muy corto (${pcm16khz.size} muestras)"
                )
            }

            // 1. Generar firma
            val gen = ShazamSignatureGenerator()
            gen.feedSamples(pcm16khz)
            val uri = gen.encodeToUri()
            val sampleMs = gen.sampleMs()
            Log.d(TAG, "Firma: ${uri.length} chars, ${sampleMs}ms")
            val diag = StringBuilder()
            diag.appendLine("=== DIAGNÓSTICO SHAZAM ===")
            diag.appendLine("Muestras: ${pcm16khz.size} (${sampleMs}ms)")
            diag.appendLine("Firma: ${uri.length} chars")
            diag.appendLine("URI inicio: ${uri.take(80)}")

            // 2. Construir request
            val uuid1 = UUID.randomUUID().toString().uppercase()
            val uuid2 = UUID.randomUUID().toString().uppercase()
            val urlStr = "https://amp.shazam.com/discovery/v5/en-US/US/iphone/-/" +
                "tag/$uuid1/$uuid2?sync=true&webv3=true&sampling=true&connected=" +
                "&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1" +
                "&hidelb=true&video=v3"

            val body = JSONObject().apply {
                put("timezone", "America/Havana")
                put("signature", JSONObject().apply {
                    put("uri", uri)
                    put("samplems", sampleMs)
                })
                put("timestamp", System.currentTimeMillis())
                put("context", JSONObject())
                put("geolocation", JSONObject())
            }.toString()

            // 3. Enviar
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Shazam-Platform", "IPHONE")
                setRequestProperty("X-Shazam-AppVersion", "14.1.0")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "en-US")
                // User-Agent de iPhone (el endpoint espera clientes móviles)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                        "Mobile/15E148 Safari/604.1"
                )
            }

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "HTTP $code")
            if (code != 200) {
                val errBody = runCatching {
                    conn.errorStream?.bufferedReader()?.readText()?.take(300)
                }.getOrNull()
                diag.appendLine("HTTP: $code ERROR: ${errBody ?: "sin detalle"}")
                lastDiagnostic = diag.toString()
                return@withContext ShazamResult.Error("HTTP $code: ${errBody ?: "sin detalle"}")
            }

            val respText = conn.inputStream.bufferedReader().readText()
            diag.appendLine("HTTP: $code OK")
            diag.appendLine("Respuesta: ${respText.take(500)}")
            lastDiagnostic = diag.toString()
            parseResponse(respText, diag)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            lastDiagnostic = "Error: ${e.javaClass.simpleName}: ${e.message}"
            ShazamResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseResponse(json: String, diag: StringBuilder): ShazamResult {
        return try {
            val root = JSONObject(json)
            val matches = root.optJSONArray("matches")
            if (matches == null || matches.length() == 0) {
                diag.appendLine("Resultado: sin coincidencias"); lastDiagnostic = diag.toString()
                return ShazamResult.NoMatch("Shazam no encontró coincidencia")
            }
            val trackObj = root.optJSONObject("track")
            if (trackObj == null) {
                diag.appendLine("Resultado: sin objeto track")
                lastDiagnostic = diag.toString()
                return ShazamResult.NoMatch("Sin objeto track en respuesta")
            }
            val title = trackObj.optString("title").ifBlank {
                diag.appendLine("Resultado: track sin título"); lastDiagnostic = diag.toString()
                return ShazamResult.NoMatch("Track sin título")
            }
            val artist = trackObj.optString("subtitle").ifBlank { "Artista misterioso" }
            // Intentar extraer álbum e imagen
            var album = ""
            var cover: String? = null
            val sections = trackObj.optJSONArray("sections")
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val s = sections.optJSONObject(i) ?: continue
                    if (s.optString("type") == "SONG") {
                        val meta = s.optJSONArray("metadata")
                        if (meta != null) {
                            for (j in 0 until meta.length()) {
                                val m = meta.optJSONObject(j) ?: continue
                                if (m.optString("title") == "Album") {
                                    album = m.optString("text")
                                }
                            }
                        }
                    }
                }
            }
            cover = trackObj.optString("images")
                .let { runCatching { JSONObject(it).optString("coverart") }.getOrNull() }
                ?: trackObj.optJSONObject("images")?.optString("coverart")
                    ?.takeIf { it.isNotBlank() }
            val shazamId = trackObj.optString("key").takeIf { it.isNotBlank() }

            diag.appendLine("Resultado: ENCONTRADO: $title - $artist")
            lastDiagnostic = diag.toString()
            ShazamResult.Found(
                ShazamTrack(
                    title = title,
                    artist = artist,
                    album = album,
                    coverUrl = cover,
                    shazamId = shazamId
                )
            )
        } catch (e: Exception) {
            diag.appendLine("Resultado: error parseando: ${e.message}")
            lastDiagnostic = diag.toString()
            ShazamResult.Error("Error parseando respuesta: ${e.message}")
        }
    }
}
