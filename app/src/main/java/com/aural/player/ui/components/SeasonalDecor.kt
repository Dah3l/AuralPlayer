// Decoración de temporada dibujada en código (Canvas/vector, SIN imágenes).
//
// Halloween (27-oct..1-nov): fantasmas, telaraña, araña, murciélagos,
// calabaza, tumbas con mano zombi. Todo ANIMADO de verdad con
// infiniteTransition (barato: pocos valores animados, dibujo vectorial).
// Elegante y sutil: ambiente, no carnaval. Ningún decorado consume toques.
//
// Navidad (20-26 dic): STUB — ver punto de enganche al final del archivo.
package com.aural.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aural.player.ui.theme.HalloweenGhost
import com.aural.player.ui.theme.HalloweenPumpkin
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ===========================================================================
// FANTASMAS (Home): cruzan la pantalla flotando, aparecen y se desvanecen.
// ===========================================================================

private fun DrawScope.drawGhostFigure(cx: Float, cy: Float, s: Float, color: Color, alpha: Float) {
    val w = s
    val h = s * 1.35f
    val left = cx - w / 2f
    val top = cy - h / 2f
    val c = color.copy(alpha = alpha)
    val body = Path().apply {
        val hemY = top + h - w * 0.14f
        moveTo(left, hemY)
        lineTo(left, top + w / 2f)
        arcTo(Rect(left, top, left + w, top + w), 180f, 180f, false)
        lineTo(left + w, hemY)
        // dobladillo ondulado (3 ondas)
        val sw = w / 3f
        for (i in 0 until 3) {
            val x0 = left + w - sw * i
            quadraticBezierTo(x0 - sw / 2f, top + h + w * 0.08f, x0 - sw, hemY)
        }
        close()
    }
    drawPath(body, c)
    val dark = Color(0xFF0A0A0D).copy(alpha = (alpha * 0.9f).coerceIn(0f, 1f))
    // ojos
    drawOval(dark, Offset(cx - w * 0.24f, top + h * 0.30f), Size(w * 0.11f, h * 0.10f))
    drawOval(dark, Offset(cx + w * 0.13f, top + h * 0.30f), Size(w * 0.11f, h * 0.10f))
    // boca
    drawOval(dark, Offset(cx - w * 0.05f, top + h * 0.46f), Size(w * 0.10f, h * 0.08f))
}

@Composable
private fun GhostDrift(
    travelDurationMs: Int,
    delayMs: Int,
    sizeDp: Dp,
    baseYFraction: Float,
    direction: Float,
    maxAlpha: Float
) {
    val t = rememberInfiniteTransition(label = "ghost")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(travelDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gp"
    )
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    val delayFrac = delayMs / travelDurationMs.toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val phase = (p + delayFrac) % 1f
        val w = size.width
        val h = size.height
        val x = if (direction > 0) -140f + phase * (w + 280f)
        else w + 140f - phase * (w + 280f)
        val y = baseYFraction * h + sin(phase * 2f * PI.toFloat() * 2.5f) * 30f
        val alpha = sin(phase * PI.toFloat()).coerceIn(0f, 1f) * maxAlpha
        drawGhostFigure(x, y, sizePx, HalloweenGhost, alpha)
    }
}

// ===========================================================================
// TELARAÑA (Home): esquina superior, con brillo y mecedora sutiles.
// ===========================================================================

@Composable
private fun SpiderWeb(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "web")
    val shimmer by t.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    val swayDeg by t.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )
    Canvas(modifier.size(140.dp)) {
        val anchor = Offset(size.width, 0f)
        rotate(swayDeg, anchor) {
            val col = Color.White.copy(alpha = shimmer)
            val maxR = size.minDimension
            val spokes = 6
            // hilos radiales: de 90° (abajo) a 180° (izquierda)
            for (i in 0..spokes) {
                val aDeg = 90f + 90f * i / spokes
                val a = Math.toRadians(aDeg.toDouble())
                val end = anchor + Offset(
                    cos(a).toFloat() * maxR,
                    sin(a).toFloat() * maxR
                )
                drawLine(col, anchor, end, strokeWidth = 1.6f)
            }
            // espiral: arcos concéntricos
            for (k in 1..4) {
                val r = maxR * k / 4.5f
                drawArc(
                    color = col,
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = anchor - Offset(r, r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 1.4f)
                )
            }
        }
    }
}

// ===========================================================================
// ARAÑA (Home): baja por su hilo, se balancea, mueve las patas y sube.
// ===========================================================================

@Composable
private fun SpiderDrop(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spider")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sp"
    )
    val density = LocalDensity.current
    val dropMaxPx = with(density) { 150.dp.toPx() }
    Canvas(modifier.size(96.dp, 220.dp)) {
        val w = size.width
        // fases: baja 0→0.32, cuelga y se mece 0.32→0.68, sube 0.68→1
        val dropPx = dropMaxPx * when {
            p < 0.32f -> p / 0.32f
            p < 0.68f -> 1f
            else -> 1f - (p - 0.68f) / 0.32f
        }
        val alpha = when {
            p < 0.04f -> p / 0.04f
            p > 0.96f -> (1f - p) / 0.04f
            else -> 1f
        }.coerceIn(0f, 1f)
        val hanging = p in 0.32f..0.68f
        val swayX =
            if (hanging) sin((p - 0.32f) / 0.36f * 2f * PI.toFloat() * 2f) * 18f else 0f
        val cx = w / 2f + swayX
        val bodyY = 8f + dropPx
        val col = Color(0xFF17121F).copy(alpha = alpha)
        // hilo
        drawLine(col, Offset(cx, 0f), Offset(cx, bodyY - 12f), strokeWidth = 2f)
        // 8 patas con meneo
        val wig = sin(p * 2f * PI.toFloat() * 12f)
        for (side in listOf(-1f, 1f)) {
            for (i in 0..3) {
                val wob = wig * (5f + i * 2.5f)
                val hipX = cx + side * 7f
                val hipY = bodyY - 10f + i * 6.5f
                val kneeX = cx + side * (14f + wob * 0.4f)
                val kneeY = hipY - 8f + i * 1.5f
                val footX = cx + side * (26f + i * 5f + wob)
                val footY = kneeY + 13f
                drawLine(
                    col, Offset(hipX, hipY), Offset(kneeX, kneeY),
                    strokeWidth = 2.6f, cap = StrokeCap.Round
                )
                drawLine(
                    col, Offset(kneeX, kneeY), Offset(footX, footY),
                    strokeWidth = 2.6f, cap = StrokeCap.Round
                )
            }
        }
        // cuerpo: abdomen + cabeza
        drawOval(col, Offset(cx - 11f, bodyY - 2f), Size(22f, 30f))
        drawOval(col, Offset(cx - 8f, bodyY - 18f), Size(16f, 18f))
        // ojitos naranja
        val eyeA = (alpha * 0.95f).coerceIn(0f, 1f)
        drawCircle(HalloweenPumpkin.copy(alpha = eyeA), 2.4f, Offset(cx - 4f, bodyY - 22f))
        drawCircle(HalloweenPumpkin.copy(alpha = eyeA), 2.4f, Offset(cx + 4f, bodyY - 22f))
    }
}

// ===========================================================================
// AMBIENTE HOME: fantasmas + telaraña + araña (dibujo puro, sin toques).
// ===========================================================================

@Composable
fun HalloweenHomeAmbient(modifier: Modifier = Modifier) {
    Box(modifier) {
        GhostDrift(
            travelDurationMs = 26000, delayMs = 0, sizeDp = 58.dp,
            baseYFraction = 0.20f, direction = 1f, maxAlpha = 0.42f
        )
        GhostDrift(
            travelDurationMs = 33000, delayMs = 11000, sizeDp = 42.dp,
            baseYFraction = 0.52f, direction = -1f, maxAlpha = 0.32f
        )
        GhostDrift(
            travelDurationMs = 29000, delayMs = 20000, sizeDp = 50.dp,
            baseYFraction = 0.78f, direction = 1f, maxAlpha = 0.36f
        )
        SpiderWeb(Modifier.align(Alignment.TopEnd))
        SpiderDrop(Modifier.align(Alignment.TopCenter).offset(x = 110.dp))
    }
}

// ===========================================================================
// MURCIÉLAGOS (Now Playing): cruzan de vez en cuando batiendo las alas.
// ===========================================================================

@Composable
private fun BatFlight(
    travelDurationMs: Int,
    delayMs: Int,
    yFraction: Float,
    sizeDp: Dp,
    maxAlpha: Float = 0.8f
) {
    val t = rememberInfiniteTransition(label = "bat")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(travelDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bp"
    )
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    val delayFrac = delayMs / travelDurationMs.toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val phase = (p + delayFrac) % 1f
        val w = size.width
        val h = size.height
        val x = -80f + phase * (w + 160f)
        val y = yFraction * h + sin(phase * 2f * PI.toFloat() * 3f) * 18f
        val alpha = sin(phase * PI.toFloat()).coerceIn(0f, 1f) * maxAlpha
        // aleteo rápido (2 frames)
        val flap = sin(phase * 2f * PI.toFloat() * 9f)
        val col = Color(0xFF2E2440).copy(alpha = alpha)
        val s = sizePx
        // cuerpo + cabeza
        drawOval(col, Offset(x - s * 0.07f, y - s * 0.10f), Size(s * 0.14f, s * 0.20f))
        drawCircle(col, s * 0.055f, Offset(x, y - s * 0.11f))
        // alas
        for (side in listOf(-1f, 1f)) {
            val tipX = x + side * s * 0.5f
            val tipY = y - s * 0.06f - flap * s * 0.22f
            val wing = Path().apply {
                moveTo(x + side * s * 0.05f, y - s * 0.04f)
                quadraticBezierTo(
                    x + side * s * 0.28f, y - s * 0.16f - flap * s * 0.12f,
                    tipX, tipY
                )
                quadraticBezierTo(
                    x + side * s * 0.30f, y + s * 0.02f,
                    x + side * s * 0.10f, y + s * 0.06f
                )
                close()
            }
            drawPath(wing, col)
        }
    }
}

@Composable
fun HalloweenBats(modifier: Modifier = Modifier) {
    Box(modifier) {
        BatFlight(
            travelDurationMs = 12000, delayMs = 0,
            yFraction = 0.10f, sizeDp = 44.dp
        )
        BatFlight(
            travelDurationMs = 17000, delayMs = 8000,
            yFraction = 0.18f, sizeDp = 34.dp, maxAlpha = 0.65f
        )
    }
}

// ===========================================================================
// SPLASH: tumbas + mano zombi animada + niebla sutil (abajo).
// ===========================================================================

private fun DrawScope.drawTombstone(
    cx: Float,
    baseY: Float,
    w: Float,
    h: Float,
    tiltDeg: Float,
    color: Color
) {
    rotate(tiltDeg, Offset(cx, baseY)) {
        val left = cx - w / 2f
        val top = baseY - h
        val path = Path().apply {
            moveTo(left, baseY)
            lineTo(left, top + w / 2f)
            arcTo(Rect(left, top, left + w, top + w), 180f, 180f, false)
            lineTo(left + w, baseY)
            close()
        }
        drawPath(path, color)
        // filo superior más claro
        drawArc(
            color = color.copy(alpha = 0.5f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(w, w),
            style = Stroke(width = 2.5f)
        )
    }
}

private fun DrawScope.drawZombieHand(
    cx: Float,
    groundY: Float,
    risePx: Float,
    wig: Float,
    s: Float = 1f
) {
    val green = Color(0xFF6F9B3F)
    val gDark = Color(0xFF557A2E)
    translate(0f, -risePx) {
        // La mano se dibuja escalada (s) con pivote en la base: la subida
        // (risePx) no se escala, solo el tamaño de la mano.
        scale(s, s, Offset(cx, groundY)) {
            // antebrazo
            drawRoundRect(green, Offset(cx - 15f, groundY - 70f), Size(30f, 80f), CornerRadius(14f, 14f))
            drawRoundRect(
                gDark, Offset(cx - 15f, groundY - 70f), Size(10f, 80f),
                CornerRadius(5f, 5f)
            )
            // palma
            drawRoundRect(green, Offset(cx - 22f, groundY - 92f), Size(44f, 28f), CornerRadius(12f, 12f))
            // 4 dedos con meneo individual
            val fingerDX = listOf(-15f, -5f, 5f, 15f)
            fingerDX.forEachIndexed { i, dx ->
                val ang = dx * 0.9f + wig * (7f + i * 3f)
                rotate(ang, Offset(cx + dx, groundY - 92f)) {
                    drawRoundRect(
                        green, Offset(cx + dx - 5f, groundY - 124f), Size(10f, 36f),
                        CornerRadius(5f, 5f)
                    )
                }
            }
            // pulgar
            rotate(38f + wig * 6f, Offset(cx - 20f, groundY - 78f)) {
                drawRoundRect(
                    green, Offset(cx - 31f, groundY - 100f), Size(10f, 26f),
                    CornerRadius(5f, 5f)
                )
            }
        } // scale(s)
    }
}

@Composable
fun HalloweenSplashScene(modifier: Modifier = Modifier) {
    var rise by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        rise = true
    }
    val density = LocalDensity.current
    val maxRisePx = with(density) { 170.dp.toPx() }
    val risePx by animateFloatAsState(
        targetValue = if (rise) maxRisePx else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "handrise"
    )
    val t = rememberInfiniteTransition(label = "splashfx")
    val wig by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wig"
    )
    val fogP by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fog"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val groundY = h * 0.98f
        // niebla a la deriva (muy sutil)
        for (i in 0..2) {
            val fx = ((fogP + i * 0.37f) % 1f) * (w + 560f) - 280f
            drawOval(
                Color.White.copy(alpha = 0.05f),
                Offset(fx - 260f, groundY - 70f + i * 18f),
                Size(520f, 62f)
            )
        }
        // montículo
        drawOval(Color(0xFF0C0C10), Offset(-w * 0.15f, groundY - 44f), Size(w * 1.3f, 130f))
        // tumbas laterales (más grandes)
        drawTombstone(w * 0.16f, groundY, 115f, 172f, -7f, Color(0xFF4C4C55))
        drawTombstone(w * 0.86f, groundY, 105f, 152f, 6f, Color(0xFF43434C))
        // mano zombi saliendo detrás de la tumba central (más grande)
        drawZombieHand(w * 0.52f, groundY + 8f, risePx, wig, s = 1.6f)
        // tumba central (delante de la muñeca)
        drawTombstone(w * 0.52f, groundY, 132f, 192f, 0f, Color(0xFF55555E))
    }
}

// ---------------------------------------------------------------------------
// NAVIDAD (20-26 dic): PUNTO DE ENGANCHE (mismo patrón que Halloween).
//
// El pack navideño se construye en diciembre:
//   - AppTheme.CHRISTMAS("Navidad", icono) + ChristmasColorScheme en Theme.kt
//     (ver "PATRÓN NAVIDAD" en ui/theme/Seasonal.kt).
//   - En ThemesScreen: mostrarlo solo si isChristmasWindow().
//   - Auto-aplicado: flag "christmas_applied_<año>" + "theme_before_christmas"
//     (mismo runSeasonalAutoTheme de SettingsStore, generalizado).
//   - Aquí mismo:
//       ChristmasHomeAmbient(): nieve cayendo, luces de colores parpadeantes.
//       ChristmasSplashScene(): gorro navideño / árbol / nieve.
//   - En cada pantalla, el decorado se muestra cuando el tema activo ==
//     CHRISTMAS (if (themeName == AppTheme.CHRISTMAS.name)).
// ---------------------------------------------------------------------------
