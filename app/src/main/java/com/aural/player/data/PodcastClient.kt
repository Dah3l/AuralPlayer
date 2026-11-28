// Cliente de podcasts: búsqueda en el directorio público de
// iTunes (sin clave) y parser RSS de feeds. Solo lectura; las
// descargas viven en PodcastDownloadManager.
package com.aural.player.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

object PodcastClient {

    private const val SEARCH_URL = "https://itunes.apple.com/search"
    private const val USER_AGENT = "AuralPlayer/1.3.0 (Android)"

    suspend fun searchPodcasts(query: String, limit: Int = 30): List<PodcastShow> =
        withContext(Dispatchers.IO) {
            val term = URLEncoder.encode(query, "UTF-8")
            val body = get(
                "$SEARCH_URL?media=podcast&entity=podcast&limit=${limit.coerceIn(1, 50)}&term=$term"
            ) ?: return@withContext emptyList()
            parseSearchResults(body)
        }

    private fun parseSearchResults(body: String): List<PodcastShow> = try {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val feedUrl = obj.optString("feedUrl")
                if (feedUrl.isBlank()) continue
                add(
                    PodcastShow(
                        id = feedUrl,
                        title = obj.optString("collectionName"),
                        feedUrl = feedUrl,
                        imageUrl = obj.optString("artworkUrl600")
                            .ifBlank { obj.optString("artworkUrl100") }
                            .ifBlank { null },
                        author = obj.optString("artistName")
                    )
                )
            }
        }.distinctBy { it.feedUrl }
    } catch (e: Exception) {
        emptyList()
    }

    /** Episodios de un feed, del más reciente al más viejo.
     *  Con [settings], la petición es condicional: si el
     *  servidor responde 304 a los validadores guardados,
     *  se sirven los episodios cacheados sin bajar el
     *  cuerpo del feed. Sin validadores del servidor, el
     *  comportamiento es el de siempre. */
    suspend fun fetchEpisodes(
        feedUrl: String,
        limit: Int = 100,
        settings: SettingsStore? = null
    ): List<PodcastEpisode> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 200)
            val cached = settings?.let {
                runCatching { it.podcastFeedCache(feedUrl) }.getOrNull()
            }
            // La caché sirve si trae episodios y cubre lo
            // pedido: o es el feed completo, o trae al menos
            // tantos como el límite de esta petición.
            fun usable(cache: PodcastFeedCache?): Boolean =
                cache != null && cache.episodes.isNotEmpty() &&
                    (cache.complete || cache.episodes.size >= safeLimit)
            val hasValidators = cached != null &&
                (!cached.etag.isNullOrBlank() ||
                    !cached.lastModified.isNullOrBlank())
            if (hasValidators) {
                val conditional = fetchFeed(
                    feedUrl, safeLimit, cached?.etag, cached?.lastModified
                )
                if (conditional.notModified) {
                    if (usable(cached)) {
                        return@withContext cached!!.episodes.take(safeLimit)
                    }
                    // 304 sin caché utilizable: se reintenta
                    // sin condicionales, como antes.
                } else if (conditional.episodes.isNotEmpty()) {
                    saveFeedCache(settings, feedUrl, conditional, safeLimit)
                    return@withContext conditional.episodes
                } else {
                    return@withContext emptyList()
                }
            }
            val fresh = fetchFeed(feedUrl, safeLimit, null, null)
            if (fresh.episodes.isNotEmpty()) {
                saveFeedCache(settings, feedUrl, fresh, safeLimit)
            }
            fresh.episodes
        }

    private data class FeedFetch(
        val episodes: List<PodcastEpisode> = emptyList(),
        val etag: String? = null,
        val lastModified: String? = null,
        val notModified: Boolean = false
    )

    private suspend fun saveFeedCache(
        settings: SettingsStore?,
        feedUrl: String,
        fetch: FeedFetch,
        safeLimit: Int
    ) {
        if (settings == null || fetch.episodes.isEmpty()) return
        runCatching {
            settings.savePodcastFeedCache(
                feedUrl,
                PodcastFeedCache(
                    etag = fetch.etag,
                    lastModified = fetch.lastModified,
                    // Si el parser paró por el límite, la lista
                    // puede estar cortada: no es el feed entero.
                    complete = fetch.episodes.size < safeLimit,
                    episodes = fetch.episodes
                )
            )
        }
    }

    private fun fetchFeed(
        feedUrl: String,
        safeLimit: Int,
        etag: String?,
        lastModified: String?
    ): FeedFetch {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(feedUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty(
                "Accept",
                "application/rss+xml, application/xml, text/xml, */*"
            )
            if (!etag.isNullOrBlank()) {
                connection.setRequestProperty("If-None-Match", etag)
            }
            if (!lastModified.isNullOrBlank()) {
                connection.setRequestProperty("If-Modified-Since", lastModified)
            }
            when {
                connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED ->
                    FeedFetch(notModified = true)
                connection.responseCode !in 200..299 -> FeedFetch()
                else -> FeedFetch(
                    episodes = connection.inputStream.use { input ->
                        parseFeed(input, safeLimit)
                    },
                    etag = connection.getHeaderField("ETag"),
                    lastModified = connection.getHeaderField("Last-Modified")
                )
            }
        } catch (e: Exception) {
            FeedFetch()
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseFeed(input: java.io.InputStream, limit: Int): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val episodes = mutableListOf<PodcastEpisode>()
        var inItem = false
        var title = ""
        var guid = ""
        var publishedMs = 0L
        var durationMs = 0L
        var audioUrl = ""
        var audioBytes = 0L
        var audioMimeType = ""
        var imageUrl: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && episodes.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase(Locale.US)
                    when {
                        name == "item" -> {
                            inItem = true
                            title = ""
                            guid = ""
                            publishedMs = 0L
                            durationMs = 0L
                            audioUrl = ""
                            audioBytes = 0L
                            audioMimeType = ""
                            imageUrl = null
                        }
                        inItem && name == "title" -> title = parser.nextText().trim()
                        inItem && name == "guid" -> guid = parser.nextText().trim()
                        inItem && name == "pubdate" ->
                            publishedMs = parseDate(parser.nextText())
                        inItem && name.endsWith("duration") ->
                            durationMs = parseDuration(parser.nextText())
                        inItem && name == "enclosure" -> {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (url.isNotBlank() &&
                                (type.startsWith("audio") || audioUrl.isBlank())
                            ) {
                                audioUrl = url
                                audioBytes = parser
                                    .getAttributeValue(null, "length")
                                    ?.toLongOrNull() ?: 0L
                                audioMimeType = type
                            }
                        }
                        inItem && name.endsWith("image") -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) imageUrl = href
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true) && inItem) {
                        if (audioUrl.isNotBlank()) {
                            episodes += PodcastEpisode(
                                guid = guid.ifBlank { audioUrl },
                                title = title.ifBlank { "Episodio" },
                                publishedMs = publishedMs,
                                durationMs = durationMs,
                                audioUrl = audioUrl,
                                imageUrl = imageUrl,
                                audioBytes = audioBytes,
                                audioMimeType = audioMimeType
                            )
                        }
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return episodes.distinctBy { it.guid }
    }

    private fun parseDate(raw: String): Long {
        val value = raw.trim()
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = true
                return format.parse(value)?.time ?: continue
            } catch (e: Exception) {
                // Probar el siguiente formato.
            }
        }
        return 0L
    }

    /** Duración iTunes: segundos, mm:ss o hh:mm:ss. */
    private fun parseDuration(raw: String): Long {
        val value = raw.trim()
        if (value.isBlank()) return 0L
        value.toLongOrNull()?.let { return it * 1000L }
        val parts = value.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var seconds = 0L
        for (part in parts) {
            seconds = seconds * 60L + part
        }
        return seconds * 1000L
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
