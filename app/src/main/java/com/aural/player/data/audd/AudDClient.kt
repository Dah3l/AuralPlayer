// Cliente de reconocimiento vía AudD (https://audd.io).
//
// API simple: se envía el audio por POST multipart y devuelve el resultado.
// Requiere API token gratuito de https://dashboard.audd.io/
package com.aural.player.data.audd

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Token de API de AudD. Dahel debe pegarlo aquí tras crear su cuenta. */
object AudDConfig {
    var apiToken: String = ""
}

/** Canción identificada por AudD. */
data class AudDTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null
)

/** Resultado del intento de reconocimiento. */
sealed interface AudDResult {
    data class Found(val track: AudDTrack) : AudDResult
    data class NoMatch(val detail: String) : AudDResult
    data class Error(val detail: String) : AudDResult
}

object AudDClient {

    private const val TAG = "AudDClient"
    private const val TIMEOUT_MS = 20_000
    private const val ENDPOINT = "https://api.audd.io/"

    /** Diagnóstico del último intento. */
    var lastDiagnostic: String = ""
        private set

    /**
     * Identifica la canción a partir de un archivo WAV.
     * @param wavBytes bytes del archivo WAV (PCM 16-bit mono)
     */
    suspend fun recognize(wavBytes: ByteArray): AudDResult = withContext(Dispatchers.IO) {
        val diag = StringBuilder()
        diag.appendLine("=== DIAGNÓSTICO AUDD ===")
        diag.appendLine("Audio: ${wavBytes.size} bytes")

        try {
            val token = AudDConfig.apiToken
            if (token.isBlank()) {
                lastDiagnostic = "Falta el API token de AudD"
                return@withContext AudDResult.Error(
                    "Falta configurar el API token de AudD"
                )
            }

            val boundary = "----AuralBoundary${System.currentTimeMillis()}"
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty(
                    "Content-Type", "multipart/form-data; boundary=$boundary"
                )
            }

            DataOutputStream(conn.outputStream).use { out ->
                // api_token
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"api_token\"\r\n\r\n")
                out.writeBytes("$token\r\n")
                // return (metadata adicional)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"return\"\r\n\r\n")
                out.writeBytes("apple_music,spotify\r\n")
                // archivo de audio
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; " +
                        "filename=\"recording.wav\"\r\n"
                )
                out.writeBytes("Content-Type: audio/wav\r\n\r\n")
                out.write(wavBytes)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            diag.appendLine("HTTP: $code")
            Log.d(TAG, "HTTP $code")

            if (code != 200) {
                val err = runCatching {
                    conn.errorStream?.bufferedReader()?.readText()?.take(300)
                }.getOrNull()
                diag.appendLine("Error: $err")
                lastDiagnostic = diag.toString()
                return@withContext AudDResult.Error("HTTP $code: ${err ?: "sin detalle"}")
            }

            val respText = conn.inputStream.bufferedReader().readText()
            diag.appendLine("Respuesta: ${respText.take(600)}")
            lastDiagnostic = diag.toString()
            parseResponse(respText, diag)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            diag.appendLine("Excepción: ${e.javaClass.simpleName}: ${e.message}")
            lastDiagnostic = diag.toString()
            AudDResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseResponse(json: String, diag: StringBuilder): AudDResult {
        return try {
            val root = JSONObject(json)
            val status = root.optString("status")
            if (status != "success") {
                val errMsg = root.optJSONObject("error")
                    ?.optString("error_message") ?: "Error desconocido"
                diag.appendLine("Status: $status - $errMsg")
                lastDiagnostic = diag.toString()
                return AudDResult.Error("AudD: $errMsg")
            }
            val result = root.optJSONObject("result")
                ?: run {
                    diag.appendLine("Sin resultado")
                    lastDiagnostic = diag.toString()
                    return AudDResult.NoMatch("AudD no encontró coincidencia")
                }
            val title = result.optString("title").ifBlank {
                diag.appendLine("Sin título")
                lastDiagnostic = diag.toString()
                return AudDResult.NoMatch("Resultado sin título")
            }
            val artist = result.optString("artist").ifBlank { "Artista misterioso" }
            val album = result.optString("album")
            // Intentar obtener carátula de apple_music o spotify
            var cover: String? = null
            val appleMusic = result.optJSONObject("apple_music")
            if (appleMusic != null) {
                cover = appleMusic.optJSONObject("artwork")
                    ?.optString("url")?.takeIf { it.isNotBlank() }
            }
            if (cover == null) {
                cover = result.optJSONObject("spotify")
                    ?.optJSONObject("album")
                    ?.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optString("url")?.takeIf { it.isNotBlank() }
            }

            diag.appendLine("Encontrado: $title - $artist")
            lastDiagnostic = diag.toString()
            AudDResult.Found(AudDTrack(title, artist, album, cover))
        } catch (e: Exception) {
            diag.appendLine("Error parseando: ${e.message}")
            lastDiagnostic = diag.toString()
            AudDResult.Error("Error parseando: ${e.message}")
        }
    }

    /**
     * Convierte muestras PCM 16-bit mono a un archivo WAV en memoria.
     */
    fun pcmToWav(pcm: ShortArray, sampleRate: Int = 16000): ByteArray {
        val dataSize = pcm.size * 2
        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, 36 + dataSize)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1) // PCM
        writeShortLE(header, 22, 1) // mono
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, sampleRate * 2) // byte rate
        writeShortLE(header, 32, 2) // block align
        writeShortLE(header, 34, 16) // bits per sample
        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataSize)

        val wav = ByteArray(44 + dataSize)
        header.copyInto(wav, 0)
        var pos = 44
        for (s in pcm) {
            wav[pos++] = (s.toInt() and 0xFF).toByte()
            wav[pos++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return wav
    }

    private fun writeIntLE(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xFF).toByte()
        arr[offset + 1] = ((value shr 8) and 0xFF).toByte()
        arr[offset + 2] = ((value shr 16) and 0xFF).toByte()
        arr[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xFF).toByte()
        arr[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
