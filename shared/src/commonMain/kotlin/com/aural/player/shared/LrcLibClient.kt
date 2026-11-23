// Cliente multiplataforma para buscar letras en línea (LRCLIB +
// respaldo lyrics.ovh — gratis, sin API key). Misma estrategia que
// la versión Android (Fase A): exacto con duración, exacto sin
// duración, nombres originales, búsqueda con puntuación y texto
// plano al final. La red va por Ktor y el JSON por la API de árbol
// de kotlinx-serialization (sin plugin, sin @Serializable).
package com.aural.player.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

object LrcLibClient {

    private const val CACHE_MAX_ENTRIES = 200
    private const val TTL_POSITIVE_MS = 24L * 60 * 60 * 1000
    private const val TTL_NEGATIVE_MS = 30L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient by lazy { createHttpClient() }

    private data class CacheEntry(val result: String?, val timeMs: Long)
    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheMutex = Mutex()

    /**
     * Busca la letra de una canción. Estrategia (Fase A, transparente
     * para el usuario):
     *  1. LRCLIB exacto con duración, luego sin duración, luego con
     *     los nombres originales.
     *  2. LRCLIB búsqueda general con puntuación de candidatos y
     *     filtro por duración.
     *  3. lyrics.ovh como texto plano (sin fingir sincronización).
     * Devuelve el contenido .lrc o null si nada funcionó.
     */
    suspend fun searchLyrics(artist: String, track: String, durationSec: Int? = null): String? {
        return try {
            val cleanArtist = cleanArtist(artist)
            val cleanTrack = cleanTrack(track)
            val key = cacheKey(cleanArtist, cleanTrack, durationSec)
            getCached(key)?.let { return it.result }

            val result = searchUncached(artist, track, cleanArtist, cleanTrack, durationSec)
            putCached(key, result)
            result
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun searchUncached(
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

    private suspend fun fetchExact(artist: String, track: String, durationSec: Int?): String? {
        var url = "https://lrclib.net/api/get?" +
                "artist_name=${enc(artist)}" +
                "&track_name=${enc(track)}"
        if (durationSec != null && durationSec > 0) url += "&duration=$durationSec"
        val body = httpGet(url) ?: return null
        return try {
            pickFromObject(json.parseToJsonElement(body).jsonObject)
        } catch (e: Exception) { null }
    }

    private suspend fun fetchSearch(artist: String, track: String, durationSec: Int?): String? {
        val url = "https://lrclib.net/api/search?q=${enc("$artist $track")}"
        val body = httpGet(url) ?: return null
        return try {
            val arr: JsonArray = json.parseToJsonElement(body).jsonArray
            var best: String? = null
            var bestScore = Int.MIN_VALUE
            for (element in arr) {
                val obj = element as? JsonObject ?: continue
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
    private fun pickFromObject(obj: JsonObject): String? {
        val synced = optLyricsString(obj, "syncedLyrics")
        if (synced != null && isValidSynced(synced)) return synced
        val plain = optLyricsString(obj, "plainLyrics") ?: return null
        val lrc = plainToLrc(plain)
        return if (lrc.isNotBlank()) lrc else null
    }

    /**
     * Extrae un campo de letra de un JsonObject: null si falta, si es
     * JSON null, si está en blanco o si es el literal "null" (algunas
     * respuestas traen el null como texto y eso terminaba guardado
     * como letra y mostrándose en la pantalla).
     */
    private fun optLyricsString(obj: JsonObject, key: String): String? {
        val el = obj[key] as? JsonPrimitive ?: return null
        if (el is JsonNull) return null
        val value = el.contentOrNull?.trim() ?: return null
        if (value.isBlank()) return null
        if (value.equals("null", ignoreCase = true)) return null
        return value
    }

    /**
     * Puntúa un candidato de /api/search. Devuelve null si se rechaza
     * (duración con diferencia mayor de 8 s sin match exacto de nombre).
     */
    private fun scoreCandidate(
        obj: JsonObject, artist: String, track: String, durationSec: Int?
    ): Int? {
        val candTrack = (obj["trackName"] as? JsonPrimitive)?.contentOrNull ?: ""
        val candArtist = (obj["artistName"] as? JsonPrimitive)?.contentOrNull ?: ""
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
        val candDuration = (obj["duration"] as? JsonPrimitive)?.doubleOrNull
        if (durationSec != null && durationSec > 0 && candDuration != null) {
            val diff = abs(candDuration - durationSec)
            when {
                diff <= 2.0 -> score += 20
                diff <= 8.0 -> score += 10
                else -> if (!(exactTrack && exactArtist)) return null
            }
        }
        val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
        if (!synced.isNullOrBlank()) score += 10
        return score
    }

    private fun partial(a: String, b: String): Boolean =
        a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))

    // Espacios en blanco: un solo Regex compilado, no uno por llamada.
    private val whitespaceRegex = Regex("\\s+")

    private fun norm(s: String): String =
        s.lowercase().trim().replace(whitespaceRegex, " ")

    private suspend fun fetchLyricsOvh(artist: String, track: String): String? {
        val url = "https://api.lyrics.ovh/v1/${enc(artist)}/${enc(track)}"
        val body = httpGet(url) ?: return null
        return try {
            val lyrics = optLyricsString(json.parseToJsonElement(body).jsonObject, "lyrics") ?: return null
            val lrc = plainToLrc(lyrics)
            if (lrc.isNotBlank()) lrc else null
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
        return t.replace(whitespaceRegex, " ").trim()
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
        return a.substring(0, cut).replace(whitespaceRegex, " ").trim()
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
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .joinToString("\n") { "[00:00.00]$it" }
    }

    // ---------- Caché en memoria (incluye negativos, con TTL) ----------

    private fun cacheKey(artist: String, track: String, durationSec: Int?): String =
        "${norm(artist)}|${norm(track)}|${durationSec ?: -1}"

    private suspend fun getCached(key: String): CacheEntry? {
        return cacheMutex.withLock {
            val entry = cache[key] ?: return@withLock null
            val ttl = if (entry.result != null) TTL_POSITIVE_MS else TTL_NEGATIVE_MS
            if (SystemDates.nowEpochMillis() - entry.timeMs > ttl) {
                cache.remove(key)
                null
            } else {
                entry
            }
        }
    }

    private suspend fun putCached(key: String, result: String?) {
        cacheMutex.withLock {
            if (cache.size >= CACHE_MAX_ENTRIES) {
                val oldest = cache.entries.minByOrNull { it.value.timeMs }?.key
                if (oldest != null) cache.remove(oldest)
            }
            cache[key] = CacheEntry(result, SystemDates.nowEpochMillis())
        }
    }

    // ---------- HTTP ----------

    private fun enc(s: String): String = s.encodeURLParameter()

    private suspend fun httpGet(urlStr: String): String? {
        return try {
            val response = client.get(urlStr)
            if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
        } catch (e: Exception) {
            null
        }
    }
}
