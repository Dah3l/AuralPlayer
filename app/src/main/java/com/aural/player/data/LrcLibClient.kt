// Cliente para buscar letras en línea (LRCLIB + respaldo lyrics.ovh — gratis, sin API key)
package com.aural.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

object LrcLibClient {

    private const val TIMEOUT_MS = 5000
    private const val CACHE_MAX_ENTRIES = 200
    private const val TTL_POSITIVE_MS = 24L * 60 * 60 * 1000
    private const val TTL_NEGATIVE_MS = 30L * 60 * 1000

    private data class CacheEntry(val result: String?, val timeMs: Long)
    private val cache = LinkedHashMap<String, CacheEntry>()

    /**
     * Busca la letra de una canción. Estrategia (Fase A, transparente para el usuario):
     *  1. LRCLIB exacto con duración, luego sin duración, luego con los nombres originales.
     *  2. LRCLIB búsqueda general con puntuación de candidatos y filtro por duración.
     *  3. lyrics.ovh como texto plano (sin fingir sincronización).
     * Devuelve el contenido .lrc o null si nada funcionó.
     */
    suspend fun searchLyrics(artist: String, track: String, durationSec: Int? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val cleanArtist = cleanArtist(artist)
                val cleanTrack = cleanTrack(track)
                val key = cacheKey(cleanArtist, cleanTrack, durationSec)
                getCached(key)?.let { return@withContext it.result }

                val result = searchUncached(artist, track, cleanArtist, cleanTrack, durationSec)
                putCached(key, result)
                result
            } catch (e: Exception) {
                android.util.Log.e("LrcLibClient", "Error buscando letra: ${e.message}")
                null
            }
        }

    private fun searchUncached(
        origArtist: String, origTrack: String,
        cleanArtist: String, cleanTrack: String,
        durationSec: Int?
    ): String? {
        // 1. Exacto con duración (LRCLIB tolera ±2 s)
        if (durationSec != null && durationSec > 0) {
            fetchExact(cleanArtist, cleanTrack, durationSec)?.let { return it }
        }
        // 2. Exacto sin duración (por si la duración del archivo no cuadra)
        fetchExact(cleanArtist, cleanTrack, null)?.let { return it }
        // 3. Exacto con los nombres originales, si la limpieza cambió algo
        val namesChanged = origArtist != cleanArtist || origTrack != cleanTrack
        if (namesChanged) {
            fetchExact(origArtist, origTrack, null)?.let { return it }
        }
        // 4. Búsqueda general con puntuación
        fetchSearch(cleanArtist, cleanTrack, durationSec)?.let { return it }
        if (namesChanged) {
            fetchSearch(origArtist, origTrack, durationSec)?.let { return it }
        }
        // 5. Respaldo: texto plano, sin fingir sincronización
        fetchLyricsOvh(cleanArtist, cleanTrack)?.let { return it }
        if (namesChanged) {
            fetchLyricsOvh(origArtist, origTrack)?.let { return it }
        }
        return null
    }

    private fun fetchExact(artist: String, track: String, durationSec: Int?): String? {
        var url = "https://lrclib.net/api/get?" +
                "artist_name=${enc(artist)}" +
                "&track_name=${enc(track)}"
        if (durationSec != null && durationSec > 0) url += "&duration=$durationSec"
        val json = httpGet(url) ?: return null
        return try {
            pickFromObject(JSONObject(json))
        } catch (e: Exception) { null }
    }

    private fun fetchSearch(artist: String, track: String, durationSec: Int?): String? {
        val url = "https://lrclib.net/api/search?q=${enc("$artist $track")}"
        val json = httpGet(url) ?: return null
        return try {
            val arr = JSONArray(json)
            var best: String? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val score = scoreCandidate(obj, artist, track, durationSec) ?: continue
                if (score <= bestScore) continue
                val lrc = pickFromObject(obj) ?: continue
                best = lrc
                bestScore = score
            }
            best
        } catch (e: Exception) { null }
    }

    /** Elige letra sincronizada válida o texto plano de un resultado de LRCLIB. */
    private fun pickFromObject(obj: JSONObject): String? {
        val synced = obj.optString("syncedLyrics", "")
        if (synced.isNotBlank() && isValidSynced(synced)) return synced
        val plain = obj.optString("plainLyrics", "")
        if (plain.isNotBlank()) return plainToLrc(plain)
        return null
    }

    /**
     * Puntúa un candidato de /api/search. Devuelve null si se rechaza
     * (duración con diferencia mayor de 8 s sin match exacto de nombre).
     */
    private fun scoreCandidate(
        obj: JSONObject, artist: String, track: String, durationSec: Int?
    ): Int? {
        val candTrack = obj.optString("trackName", "")
        val candArtist = obj.optString("artistName", "")
        val nTrack = norm(track)
        val nArtist = norm(artist)
        val nCandTrack = norm(candTrack)
        val nCandArtist = norm(candArtist)
        val exactTrack = nCandTrack.isNotEmpty() && nCandTrack == nTrack
        val exactArtist = nCandArtist.isNotEmpty() && nCandArtist == nArtist
        var score = 0
        score += when {
            exactTrack -> 50
            partial(nCandTrack, nTrack) -> 25
            else -> 0
        }
        score += when {
            exactArtist -> 30
            partial(nCandArtist, nArtist) -> 15
            else -> 0
        }
        val candDuration = obj.optDouble("duration", Double.NaN)
        if (durationSec != null && durationSec > 0 && !candDuration.isNaN()) {
            val diff = abs(candDuration - durationSec)
            when {
                diff <= 2.0 -> score += 20
                diff <= 8.0 -> score += 10
                else -> if (!(exactTrack && exactArtist)) return null
            }
        }
        if (obj.optString("syncedLyrics", "").isNotBlank()) score += 10
        return score
    }

    private fun partial(a: String, b: String): Boolean =
        a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))

    private fun norm(s: String): String =
        s.lowercase().trim().replace(Regex("\\s+"), " ")

    private fun fetchLyricsOvh(artist: String, track: String): String? {
        val url = "https://api.lyrics.ovh/v1/${encPath(artist)}/${encPath(track)}"
        val json = httpGet(url) ?: return null
        return try {
            val lyrics = JSONObject(json).optString("lyrics", "")
            if (lyrics.isNotBlank()) plainToLrc(lyrics) else null
        } catch (e: Exception) { null }
    }

    // ---------- Limpieza de nombres ----------

    private val parenSuffix = Regex(
        """\s*[\(\[][^)\]]*(remix|remaster|remastered|re-master|live|acoustic|version|edit|feat\.?|ft\.?|featuring|deluxe|expanded|instrumental|demo|radio|single|album|bonus|explicit|sped\s*up|slowed|reverb|nightcore|tiktok)[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val dashSuffix = Regex(
        """\s+[-–—]\s+.*(remix|remaster|remastered|live|acoustic|version|edit|feat\.?|ft\.?|deluxe|expanded|instrumental|demo|radio|single|bonus|explicit|sped\s*up|slowed).*""",
        RegexOption.IGNORE_CASE
    )
    private val featSuffix = Regex(
        """\s+[\(\[]?(feat\.?|ft\.?|featuring)\s+.+$""",
        RegexOption.IGNORE_CASE
    )

    /** Quita coletillas del título: (Remix), - Remastered, feat. X, etc. */
    private fun cleanTrack(track: String): String {
        var t = track.trim()
        t = parenSuffix.replace(t, "")
        t = dashSuffix.replace(t, "")
        t = featSuffix.replace(t, "")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Se queda con el primer artista: corta en ",", "&", "feat.", "x", etc. */
    private fun cleanArtist(artist: String): String {
        val a = artist.trim()
        val separators = listOf(
            ",", " & ", " feat. ", " feat ", " ft. ", " ft ",
            " featuring ", " x ", " X ", " + ", ";", " / ", " with ", " con "
        )
        var cut = a.length
        for (sep in separators) {
            val idx = a.indexOf(sep, ignoreCase = true)
            if (idx > 0 && idx < cut) cut = idx
        }
        return a.substring(0, cut).replace(Regex("\\s+"), " ").trim()
    }

    // ---------- Validación de LRC ----------

    private val lrcTime = Regex("""\[(\d+):(\d{2})([.:](\d{1,3}))?\]""")

    /**
     * Un LRC sincronizado válido tiene al menos 3 líneas con timestamps
     * crecientes (se permiten repetidos, pero debe haber 3 distintos).
     */
    private fun isValidSynced(lrc: String): Boolean {
        val times = mutableListOf<Long>()
        for (line in lrc.lines()) {
            val m = lrcTime.find(line) ?: continue
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val frac = m.groupValues[4]
            val ms = when (frac.length) {
                3 -> frac.toLongOrNull() ?: 0L
                2 -> (frac.toLongOrNull() ?: 0L) * 10
                1 -> (frac.toLongOrNull() ?: 0L) * 100
                else -> 0L
            }
            times.add(min * 60_000 + sec * 1000 + ms)
        }
        if (times.size < 3) return false
        var prev = -1L
        var distinct = 0
        for (t in times) {
            if (t < prev) return false
            if (t != prev) distinct++
            prev = t
        }
        return distinct >= 3
    }

    /** Convierte letra plana a formato .lrc básico (sin tiempos, solo texto). */
    private fun plainToLrc(plain: String): String {
        return plain.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "[00:00.00]" + it.trim() }
    }

    // ---------- Caché en memoria (incluye negativos, con TTL) ----------

    private fun cacheKey(artist: String, track: String, durationSec: Int?): String =
        "${norm(artist)}|${norm(track)}|${durationSec ?: -1}"

    private fun getCached(key: String): CacheEntry? {
        synchronized(cache) {
            val entry = cache[key] ?: return null
            val ttl = if (entry.result != null) TTL_POSITIVE_MS else TTL_NEGATIVE_MS
            if (System.currentTimeMillis() - entry.timeMs > ttl) {
                cache.remove(key)
                return null
            }
            return entry
        }
    }

    private fun putCached(key: String, result: String?) {
        synchronized(cache) {
            if (cache.size >= CACHE_MAX_ENTRIES) {
                val oldest = cache.entries.minByOrNull { it.value.timeMs }?.key
                if (oldest != null) cache.remove(oldest)
            }
            cache[key] = CacheEntry(result, System.currentTimeMillis())
        }
    }

    // ---------- HTTP ----------

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun encPath(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuralPlayer/1.0")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
