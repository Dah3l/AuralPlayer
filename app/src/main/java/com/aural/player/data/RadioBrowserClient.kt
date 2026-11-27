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

    /** Búsqueda de descubrimiento: nombre, género y país en
     *  una sola lista sin duplicados, con Cuba primero. */
    suspend fun search(query: String, limit: Int = 50): List<RadioStation> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        val merged = LinkedHashMap<String, RadioStation>()
        searchByName(term, limit).forEach { merged[it.id] = it }
        searchByTag(term, limit).forEach { merged.putIfAbsent(it.id, it) }
        searchByCountry(term, limit).forEach { merged.putIfAbsent(it.id, it) }
        return merged.values
            .sortedWith(compareBy { station ->
                if (station.country.equals("Cuba", ignoreCase = true)) 0 else 1
            })
            .take(limit)
    }

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

    /** «Sonando ahora» del stream (metadatos ICY), o null si
     *  la emisora no los ofrece. Lectura acotada y con
     *  timeouts: abre el stream, salta el primer bloque de
     *  audio y lee el bloque de metadatos. */
    suspend fun fetchNowPlaying(streamUrl: String): String? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(streamUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Icy-MetaData", "1")
                if (connection.responseCode !in 200..299) return@withContext null
                val metaInt = connection.getHeaderField("icy-metaint")
                    ?.toIntOrNull() ?: return@withContext null
                if (metaInt <= 0 || metaInt > 1_000_000) return@withContext null
                connection.inputStream.use { input ->
                    var skipped = 0
                    val buffer = ByteArray(8192)
                    while (skipped < metaInt) {
                        val read = input.read(
                            buffer, 0, minOf(buffer.size, metaInt - skipped)
                        )
                        if (read < 0) return@withContext null
                        skipped += read
                    }
                    val lengthByte = input.read()
                    if (lengthByte <= 0) return@withContext null
                    val metadata = ByteArray(lengthByte * 16)
                    var offset = 0
                    while (offset < metadata.size) {
                        val read = input.read(metadata, offset, metadata.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                    val text = String(metadata, 0, offset, Charsets.UTF_8)
                    val marker = "StreamTitle='"
                    val start = text.indexOf(marker)
                    if (start < 0) return@withContext null
                    val end = text.indexOf("';", start + marker.length)
                    if (end < 0) return@withContext null
                    text.substring(start + marker.length, end)
                        .trim()
                        .ifBlank { null }
                }
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
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
