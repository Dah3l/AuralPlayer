// Cliente de Radio Browser (radio-browser.info): directorio
// público de emisoras, sin clave. Solo lectura/búsqueda; la
// reproducción usa la URL directa de cada emisora.
package com.aural.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RadioBrowserClient {

    private const val BASE_URL = "https://de1.api.radio-browser.info/json/stations/search"
    private const val USER_AGENT = "AuralPlayer/1.3.0 (Android)"

    suspend fun searchByName(name: String, limit: Int = 50): List<RadioStation> =
        search(mapOf("name" to name), limit)

    suspend fun searchByCountry(country: String = "Cuba", limit: Int = 50): List<RadioStation> =
        search(mapOf("country" to country), limit)

    suspend fun searchByTag(tag: String, limit: Int = 50): List<RadioStation> =
        search(mapOf("tag" to tag), limit)

    private suspend fun search(params: Map<String, String>, limit: Int): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val query = (params + mapOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "hidebroken" to "true",
                "order" to "votes",
                "reverse" to "true"
            )).entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            val body = get("$BASE_URL?$query") ?: return@withContext emptyList()
            parseStations(body)
        }

    private fun parseStations(body: String): List<RadioStation> = try {
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url_resolved").ifBlank { obj.optString("url") }
                val uuid = obj.optString("stationuuid")
                if (url.isBlank() || uuid.isBlank()) continue
                add(
                    RadioStation(
                        id = uuid,
                        name = obj.optString("name").trim(),
                        streamUrl = url,
                        country = obj.optString("country"),
                        genre = obj.optString("tags")
                            .split(",")
                            .firstOrNull()
                            ?.trim()
                            .orEmpty(),
                        bitrateKbps = obj.optInt("bitrate", 0),
                        logoUrl = obj.optString("favicon").ifBlank { null }
                    )
                )
            }
        }.distinctBy { it.id }
    } catch (e: Exception) {
        emptyList()
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
