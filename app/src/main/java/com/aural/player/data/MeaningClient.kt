// Significado de las canciones (letras.com — texto en español generado automáticamente)
package com.aural.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer

object MeaningClient {

    private const val TIMEOUT_MS = 12000
    private const val NEGATIVE_TTL_MS = 24L * 60 * 60 * 1000

    /**
     * Devuelve el significado de la canción o null si no existe / no se pudo leer.
     * Caché en archivos: los aciertos quedan guardados; las ausencias confirmadas
     * (página sin significado o 404) 24 h. Los fallos de red NO se cachean, para
     * no dejar una canción marcada como "sin significado" por un fallo pasajero.
     */
    suspend fun getMeaning(context: Context, artist: String, track: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = cacheFile(context, artist, track)
                if (cacheFile.exists()) {
                    val cached = cacheFile.readText()
                    if (cached.isNotBlank()) return@withContext cached
                    // Archivo vacío = ausencia confirmada; respetar el TTL antes de reintentar
                    if (System.currentTimeMillis() - cacheFile.lastModified() < NEGATIVE_TTL_MS) {
                        return@withContext null
                    }
                }
                val (meaning, cacheNegative) = fetchMeaning(artist, track)
                if (meaning != null || cacheNegative) {
                    try {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(meaning ?: "")
                    } catch (e: Exception) { }
                }
                meaning
            } catch (e: Exception) { null }
        }

    private sealed interface PageResult {
        data class Found(val text: String) : PageResult
        data object NoMeaning : PageResult // 200 pero sin significado parseable
        data object NotFound : PageResult // 404
        data object Failed : PageResult // red / otro código
    }

    private data class HttpResult(val code: Int, val body: String?)

    /** Devuelve (significado, si la ausencia es cacheable). */
    private fun fetchMeaning(artist: String, track: String): Pair<String?, Boolean> {
        val cleanA = cleanName(artist)
        val cleanT = cleanName(track)
        val variants = linkedSetOf(
            cleanA to cleanT,
            artist.trim() to track.trim(),
            firstArtist(artist) to cleanT
        ).filter { it.first.isNotBlank() && it.second.isNotBlank() }
        val results = mutableListOf<PageResult>()
        for ((a, t) in variants) {
            when (val r = fetchPage(a, t)) {
                is PageResult.Found -> return r.text to true
                else -> results.add(r)
            }
        }
        val cacheable = results.any { it == PageResult.NoMeaning } ||
            (results.isNotEmpty() && results.all { it == PageResult.NotFound })
        return null to cacheable
    }

    private fun fetchPage(artist: String, track: String): PageResult {
        val a = slugify(artist)
        val t = slugify(track)
        if (a.isEmpty() || t.isEmpty()) return PageResult.Failed
        val url = "https://www.letras.com/$a/$t/significado.html"
        val res = httpGet(url)
        if (res.code != 200 || res.body == null) {
            return if (res.code == 404) PageResult.NotFound else PageResult.Failed
        }
        val text = parseMeaning(res.body)
        return if (text != null) PageResult.Found(text) else PageResult.NoMeaning
    }

    // ---------- Parseo (dos estrategias) ----------

    private fun parseMeaning(html: String): String? {
        parseParagraphs(html)?.let { return it }
        return parseStrippedText(html)
    }

    /** Estrategia 1: párrafos <p> largos, sin boilerplate. */
    private fun parseParagraphs(html: String): String? {
        val h = stripScriptsAndStyles(html)
        val paras = Regex("(?is)<p[^>]*>(.*?)</p>").findAll(h)
            .map { cleanHtmlText(it.groupValues[1]) }.toList()
        val picked = paras.filter { isMeaningParagraph(it) }.take(4)
        if (picked.isEmpty()) return null
        return picked.joinToString("\n\n")
    }

    /** Estrategia 2: texto plano de la página, recortado antes del marcador final. */
    private fun parseStrippedText(html: String): String? {
        var t = stripScriptsAndStyles(html)
        t = t.replace(Regex("(?is)<br\\s*/?>"), "\n")
        t = t.replace(Regex("(?is)</p>"), "\n")
            .replace(Regex("(?is)</div>"), "\n")
            .replace(Regex("(?is)</h[1-6]>"), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = unescapeEntities(t)
        var end = t.length
        for (m in listOf("El significado de esta letra fue generado", "Escrita por")) {
            val idx = t.indexOf(m)
            if (idx >= 0 && idx < end) end = idx
        }
        val chunks = t.substring(0, end).split("\n")
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { isMeaningParagraph(it) }
        if (chunks.isEmpty()) return null
        return chunks.take(4).joinToString("\n\n")
    }

    private fun isMeaningParagraph(p: String): Boolean {
        if (p.length < 80) return false
        val low = p.lowercase()
        if (low.contains("generado automáticamente")) return false
        if (low.contains("letras.com") || low.contains("premium") ||
            low.contains("anuncios") || low.contains("inicia sesión") ||
            low.contains("compara los planes") || low.contains("escrita por") ||
            low.contains("edición y administración")
        ) return false
        return true
    }

    private fun stripScriptsAndStyles(html: String): String {
        var h = html.replace(Regex("(?is)<script.*?</script>"), " ")
        h = h.replace(Regex("(?is)<style.*?</style>"), " ")
        h = h.replace(Regex("(?is)<noscript.*?</noscript>"), " ")
        h = h.replace(Regex("(?is)<!--.*?-->"), " ")
        return h
    }

    private fun cleanHtmlText(s: String): String {
        var t = s.replace(Regex("(?is)<br\\s*/?>"), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = unescapeEntities(t)
        return t.replace(Regex("[ \\t]+"), " ").trim()
    }

    private fun unescapeEntities(s: String): String {
        var t = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
        t = t.replace(Regex("&#(\\d+);")) {
            it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        return t
    }

    // ---------- Nombres ----------

    private val parenSuffix = Regex(
        """\s*[\(\[][^)\]]*(remix|remaster|live|acoustic|version|edit|feat\.?|ft\.?|deluxe|demo|radio|single|explicit)[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val featSuffix = Regex(
        """\s+[\(\[]?(feat\.?|ft\.?|featuring)\s+.+$""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanName(s: String): String =
        featSuffix.replace(parenSuffix.replace(s.trim(), ""), "").trim()

    private fun firstArtist(artist: String): String {
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
        return a.substring(0, cut).trim()
    }

    /** coldplay, yellow, beyonce: minúsculas, sin acentos, guiones. */
    private fun slugify(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    // ---------- Caché ----------

    private fun cacheFile(context: Context, artist: String, track: String): File {
        val key = "${artist.trim().lowercase()}|${track.trim().lowercase()}"
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(File(context.filesDir, "meanings"), "$hash.txt")
    }

    // ---------- HTTP ----------

    private fun httpGet(urlStr: String): HttpResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            conn.setRequestProperty("Referer", "https://www.letras.com/")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val code = conn.responseCode
            if (code == 200) {
                HttpResult(code, conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            } else {
                android.util.Log.e("MeaningClient", "HTTP $code en $urlStr")
                HttpResult(code, null)
            }
        } catch (e: Exception) {
            android.util.Log.e("MeaningClient", "Error leyendo $urlStr: ${e.message}")
            HttpResult(-1, null)
        } finally {
            conn?.disconnect()
        }
    }
}
