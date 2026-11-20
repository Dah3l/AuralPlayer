// Imágenes de internet en memoria (miniaturas de YouTube, portadas
// de iTunes): se cargan bajo demanda y se cachean por URL.
package com.aural.player.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

/** Caché en memoria (máx 40): se cargan bajo demanda. */
object UrlImageCache {
    private val client by lazy { createHttpClient() }
    private val cache = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        return try {
            val bytes = client.get(url).bodyAsBytes()
            decodeImageBitmap(bytes)?.also {
                if (cache.size >= 40) cache.remove(cache.keys.first())
                cache[url] = it
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberUrlImage(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url == null) null else UrlImageCache.load(url)
    }
    return bitmap
}
