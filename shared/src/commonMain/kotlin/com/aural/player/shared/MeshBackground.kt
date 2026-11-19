// Fondo ambiental del Now Playing (puerto simplificado del de Android):
// manchas radiales con los colores de la portada, deriva lenta, y velo
// del fondo del tema para que el texto se lea en temas oscuros y claros.
// Sin blur por hardware: igual en todas las máquinas.
package com.aural.player.shared

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** ¿El fondo es oscuro? (luminancia relativa sRGB, igual que Color.luminance). */
fun isDarkBackground(color: Color): Boolean {
    fun lin(c: Float) =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val luminance = 0.2126f * lin(color.red) + 0.7152f * lin(color.green) + 0.0722f * lin(color.blue)
    return luminance < 0.5f
}

@Composable
fun NowPlayingAmbientBackground(
    palette: List<Color>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "mesh")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(56000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift1"
    )
    val phase2 by transition.animateFloat(
        initialValue = (2f * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(68000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift2"
    )
    val colors = listOf(
        palette.getOrElse(0) { scheme.primary },
        palette.getOrElse(1) { scheme.primary },
        palette.getOrElse(2) { scheme.tertiary },
        scheme.primary
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(scheme.background)
        val w = size.width
        val h = size.height
        val radius = minOf(w, h) * 0.55f
        val drift = minOf(w, h) * 0.08f
        val centers = listOf(
            Offset(w * 0.22f + cos(phase1) * drift, h * 0.25f + sin(phase1) * drift),
            Offset(w * 0.8f + cos(phase2) * drift, h * 0.2f + sin(phase2) * drift),
            Offset(w * 0.75f + sin(phase1) * drift, h * 0.8f + cos(phase1) * drift),
            Offset(w * 0.2f + sin(phase2) * drift, h * 0.75f + cos(phase2) * drift)
        )
        centers.forEachIndexed { index, center ->
            val color = colors[index]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.85f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        // Velo del fondo del tema: el texto y los botones se leen igual
        // sobre manchas claras u oscuras, en temas oscuros y claros.
        drawRect(scheme.background.copy(alpha = 0.72f))
    }
}
