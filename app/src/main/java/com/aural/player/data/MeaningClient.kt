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

    private const val TIMEOUT_MS = 8000
    private const val NEGATIVE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Devuelve el significado de la canción o null si no existe / no se pudo leer.
     * Con caché en archivos: los aciertos quedan guardados, las ausencias 7 días.
     */
    suspend fun getMeaning(context: Context, artist: String, track: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = cacheFile(context, artist, track)
                if (cacheFile.exists()) {
                    val cached = cacheFile.readText()
                    if (cached.isNotBlank()) return@withContext cached
                    // Archivo vacío = ausencia conocida; respetar el TTL antes de reintentar
                    if (System.currentTimeMillis() - cacheFile.lastModified() < NEGATIVE_TTL_MS) {
                        return@withContext null
                    }
                }
                val meaning = fetchMeaning(artist, track)
                try {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(meaning ?: "")
                    if (meaning == null) cacheFile.setLastModified(System.currentTimeMillis())
                } catch (e: Exception) { }
                meaning
            } catch (e: Exception) { null }
        }

    private fun fetchMeaning(artist: String, track: String): String? {
        val cleanA = cleanName(artist)
        val cleanT = cleanName(track)
        fetchPage(cleanA, cleanT)?.let { return it }
        // Reintentar con los nombres tal cual (por si la limpieza quitó algo propio del título)
        if (cleanA != artist.trim() || cleanT != track.trim()) {
            fetchPage(artist, track)?.let { return it }
        }
        // Último intento: solo el primer artista (colaboraciones)
        val firstArtist = firstArtist(artist)
        if (firstArtist != cleanA && firstArtist.isNotBlank()) {
            fetchPage(firstArtist, cleanT)?.let { return it }
        }
        return null
    }

    private fun fetchPage(artist: String, track: String): String? {
        val a = slugify(artist)
        val t = slugify(track)
        if (a.isEmpty() || t.isEmpty()) return null
        val url = "https://www.letras.com/$a/$t/significado.html"
        val html = httpGet(url) ?: return null
        return parseMeaning(html)
    }

    /**
     * Extrae los párrafos del significado del HTML: los párrafos largos
     * anteriores a la nota "generado automáticamente", sin boilerplate.
     */
    private fun parseMeaning(html: String): String? {
        var h = html.replace(Regex("(?is)<script.*?</script>"), " ")
        h = h.replace(Regex("(?is)<style.*?</style>"), " ")
        val paras = Regex("(?is)<p[^>]*>(.*?)</p>").findAll(h)
            .map { cleanHtmlText(it.groupValues[1]) }.toList()
        val picked = mutableListOf<String>()
        for (p in paras) {
            if (p.contains("generado automáticamente", ignoreCase = true)) break
            if (p.length < 80) continue
            val low = p.lowercase()
            if (low.contains("letras.com") || low.contains("premium") ||
                low.contains("anuncios") || low.contains("inicia sesión") ||
                low.contains("compara los planes")
            ) continue
            picked.add(p)
            if (picked.size >= 4) break
        }
        if (picked.isEmpty()) return null
        return picked.joinToString("\n\n")
    }

    private fun cleanHtmlText(s: String): String {
        var t = s.replace(Regex("(?is)<br\\s*/?>"), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
        t = t.replace(Regex("&#(\\d+);")) {
            it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        return t.replace(Regex("[ \\t]+"), " ").trim()
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
        val separators = listOf(",", " & ", " feat. ", " feat ", " ft. ", " ft ",
            " featuring ", " x ", " X ", " + ", ";", " / ", " with ", " con ")
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

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty("Accept-Language", "es")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
