// Decodificación de imágenes en escritorio: Skia (el motor de Compose).
// Las portadas se bajan a 512 px como máximo: una portada incrustada
// de 1500 px son ~9 MB en RAM y otro tanto en textura de GPU, y el
// caché guarda hasta 60 — sin tope la app se iba a 1 GB+.
package com.aural.player.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

/** Lado mayor máximo de una portada decodificada (px). */
private const val MAX_COVER_PX = 512

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        val image = Image.makeFromEncoded(bytes)
        val maxSide = maxOf(image.width, image.height)
        val scaled = if (maxSide > MAX_COVER_PX) {
            val scale = MAX_COVER_PX.toFloat() / maxSide
            val w = (image.width * scale).roundToInt().coerceAtLeast(1)
            val h = (image.height * scale).roundToInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(w, h)
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(w.toFloat(), h.toFloat())
            )
            surface.makeImageSnapshot()
        } else image
        scaled.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
