// Portadas en la UI compartida: carga perezosa por canción (caché en
// memoria), tarjeta con degradado + nota musical si no hay imagen, y
// paleta de colores extraída de la portada para el fondo ambiental.
package com.aural.player.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Caché de portadas decodificadas (las filas visibles vuelven a pedirlas al desplazar). */
private object CoverImageCache {
    private const val MAX_ENTRIES = 120
    private val cache = LinkedHashMap<String, ImageBitmap>()

    @Synchronized
    fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
        while (cache.size > MAX_ENTRIES) {
            cache.remove(cache.keys.first())
        }
    }

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }
}

/** Olvida la portada cacheada de una canción (tras cambiarla o quitarla). */
fun forgetCoverImage(song: Song) {
    CoverImageCache.remove(song.filePath)
}

/** Portada decodificada de una canción, o null mientras carga / si no hay. */
@Composable
fun rememberCoverImage(song: Song?, loadBytes: (Song) -> ByteArray?): ImageBitmap? {
    val key = song?.filePath
    var bitmap by remember(key) { mutableStateOf(CoverImageCache.get(key ?: "")) }
    LaunchedEffect(key) {
        if (song == null) {
            bitmap = null
            return@LaunchedEffect
        }
        val songKey = key ?: return@LaunchedEffect
        CoverImageCache.get(songKey)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { loadBytes(song) }.getOrNull()
        } ?: return@LaunchedEffect
        val decoded = runCatching { decodeImageBitmap(bytes) }.getOrNull() ?: return@LaunchedEffect
        CoverImageCache.put(songKey, decoded)
        bitmap = decoded
    }
    return bitmap
}

/**
 * Tarjeta de portada como la de Android: degradado suave + nota musical
 * de fondo, la imagen encima si existe, borde fino. El tamaño lo pone
 * quien llama (size fijo, fillMaxWidth cuadrado, ancho completo…).
 */
@Composable
fun CoverArt(
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    showBorder: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .then(
                if (showBorder) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = shape
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Icono siempre de fondo: si la imagen falla, no queda el cuadro vacío
        Icon(
            imageVector = AuralIcons.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(maxWidth / 2.5f)
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Portada",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Paleta de la portada: hasta 3 colores dominantes saturados, para el
 * fondo ambiental del Now Playing. Sin portada, devuelve el color base.
 */
fun extractPalette(bitmap: ImageBitmap?, fallback: Color): List<Color> {
    if (bitmap == null) return listOf(fallback)
    return try {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return listOf(fallback)
        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels)
        // 12 cubos de matiz; puntúan por cantidad × saturación.
        val buckets = Array(12) { mutableListOf<Triple<Float, Float, Float>>() }
        val step = max(1, min(width, height) / 48)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = pixels[y * width + x]
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha >= 128) {
                    val r = ((pixel ushr 16) and 0xFF) / 255f
                    val g = ((pixel ushr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val delta = maxC - minC
                    val saturation = if (maxC <= 0f) 0f else delta / maxC
                    if (saturation >= 0.25f && maxC in 0.15f..0.95f) {
                        val hue = when {
                            delta <= 0f -> 0f
                            maxC == r -> (60f * ((g - b) / delta)).let { if (it < 0f) it + 360f else it }
                            maxC == g -> 60f * ((b - r) / delta) + 120f
                            else -> 60f * ((r - g) / delta) + 240f
                        }
                        buckets[(hue / 30f).toInt().coerceIn(0, 11)].add(Triple(hue, saturation, maxC))
                    }
                }
                x += step
            }
            y += step
        }
        val ranked = buckets.mapIndexed { index, samples ->
            val avgSat = if (samples.isEmpty()) 0f else samples.sumOf { it.second.toDouble() }.toFloat() / samples.size
            index to (samples.size * (0.5f + avgSat))
        }.filter { buckets[it.first].isNotEmpty() }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return listOf(fallback)
        ranked.take(3).map { (index, _) ->
            val samples = buckets[index]
            val hue = samples.sumOf { it.first.toDouble() }.toFloat() / samples.size
            val sat = (samples.sumOf { it.second.toDouble() }.toFloat() / samples.size).coerceIn(0.55f, 0.95f)
            val value = (samples.sumOf { it.third.toDouble() }.toFloat() / samples.size).coerceIn(0.45f, 0.9f)
            hsvToColor(hue, sat, value)
        }
    } catch (e: Exception) {
        listOf(fallback)
    }
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = value - c
    return Color(r + m, g + m, b + m)
}
