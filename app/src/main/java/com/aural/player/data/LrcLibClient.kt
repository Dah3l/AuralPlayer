// Cliente para buscar letras en línea (LRCLIB - gratis, sin API key)
package com.aural.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LrcLibClient {

    /**
     * Busca la letra sincronizada de una canción.
     * Devuelve el contenido .lrc o null si no se encuentra.
     */
    suspend fun searchLyrics(artist: String, track: String): String? = withContext(Dispatchers.IO) {
        try {
            // Primero intentar búsqueda exacta
            var result = fetchExact(artist, track)
            if (result != null) return@withContext result

            // Si no, búsqueda general
            result = fetchSearch(artist, track)
            return@withContext result
        } catch (e: Exception) {
            android.util.Log.e("LrcLibClient", "Error buscando letra: ${e.message}")
            null
        }
    }

    private fun fetchExact(artist: String, track: String): String? {
        val url = "https://lrclib.net/api/get?" +
                "artist_name=${URLEncoder.encode(artist, "UTF-8")}" +
                "&track_name=${URLEncoder.encode(track, "UTF-8")}"
        val json = httpGet(url) ?: return null
        return try {
            val obj = JSONObject(json)
            // Preferir syncedLyrics, si no hay usar plainLyrics convertido
            val synced = obj.optString("syncedLyrics", "")
            if (synced.isNotBlank()) synced
            else {
                val plain = obj.optString("plainLyrics", "")
                if (plain.isNotBlank()) plainToLrc(plain) else null
            }
        } catch (e: Exception) { null }
    }

    private fun fetchSearch(artist: String, track: String): String? {
        val query = "$artist $track"
        val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val json = httpGet(url) ?: return null
        return try {
            val arr = org.json.JSONArray(json)
            if (arr.length() == 0) return null
            // Tomar el primer resultado con syncedLyrics
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val synced = obj.optString("syncedLyrics", "")
                if (synced.isNotBlank()) return synced
            }
            // Si ninguno tiene synced, usar el primero con plain
            val obj = arr.getJSONObject(0)
            val plain = obj.optString("plainLyrics", "")
            if (plain.isNotBlank()) plainToLrc(plain) else null
        } catch (e: Exception) { null }
    }

    /** Convierte letra plana a formato .lrc básico (sin tiempos, solo texto). */
    private fun plainToLrc(plain: String): String {
        return plain.lines()
            .filter { it.isNotBlank() }
            .joinToString("
") { "[00:00.00]${it.trim()}" }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuralPlayer/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else null
        } catch (e: Exception) { null }
    }
}
