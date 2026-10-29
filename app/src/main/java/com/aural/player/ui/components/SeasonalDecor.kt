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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.random.Random
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
// TELARAÑA (Home): esquina superior derecha, diseño clásico de orbe —
// hilos radiales desde la esquina + espiral con leve comba, como la imagen
// de referencia. Con brillo y mecedora sutiles.
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
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )
    Canvas(modifier.size(170.dp)) {
        val anchor = Offset(size.width, 0f)
        rotate(swayDeg, anchor) {
            val col = Color.White.copy(alpha = shimmer)
            val lineStyle = Stroke(width = 1.8f, cap = StrokeCap.Round)
            val maxR = size.minDimension * 1.04f
            val spokes = 8
            // hilos radiales: de 90° (abajo) a 180° (izquierda), en abanico
            val spokeEnds = (0..spokes).map { i ->
                val a = Math.toRadians(90.0 + 90.0 * i / spokes)
                anchor + Offset(cos(a).toFloat() * maxR, sin(a).toFloat() * maxR)
            }
            for (end in spokeEnds) {
                drawLine(col, anchor, end, strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            // espiral: anillos unidos entre radiales consecutivos con comba
            // hacia el ancla (la seda cuelga un poco, como en la referencia)
            val ringFractions = listOf(0.20f, 0.34f, 0.48f, 0.62f, 0.76f, 0.90f)
            for (f in ringFractions) {
                for (i in 0 until spokeEnds.size - 1) {
                    val p0 = anchor + (spokeEnds[i] - anchor) * f
                    val p1 = anchor + (spokeEnds[i + 1] - anchor) * f
                    val mid = (p0 + p1) / 2f
                    val toAnchor = anchor - mid
                    val dist = toAnchor.getDistance()
                    val sag = (p1 - p0).getDistance() * 0.12f
                    val ctrl = if (dist > 0f) mid + toAnchor / dist * sag else mid
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        quadraticBezierTo(ctrl.x, ctrl.y, p1.x, p1.y)
                    }
                    drawPath(path, col, style = lineStyle)
                }
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
// MURCIÉLAGOS (Now Playing): caricatura — cuerpo regordete, alas anchas con
// borde ondeado de 3 ondas, orejas picudas, ojazos con pupila. Cruzan a
// distintas alturas, uno en dirección contraria.
// ===========================================================================

@Composable
private fun BatFlight(
    travelDurationMs: Int,
    delayMs: Int,
    yFraction: Float,
    sizeDp: Dp,
    maxAlpha: Float = 0.85f,
    fromRight: Boolean = false
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
        val dir = if (fromRight) -1f else 1f
        val x = if (fromRight) w + 100f - phase * (w + 200f) else -100f + phase * (w + 200f)
        val y = yFraction * h + sin(phase * 2f * PI.toFloat() * 3f) * 22f
        val alpha = sin(phase * PI.toFloat()).coerceIn(0f, 1f) * maxAlpha
        val flap = sin(phase * 2f * PI.toFloat() * 8f)
        val col = Color(0xFF201419).copy(alpha = alpha)
        val s = sizePx
        // cuerpo regordete
        drawOval(col, Offset(x - s * 0.16f, y - s * 0.20f), Size(s * 0.32f, s * 0.40f))
        // orejas picudas
        for (side in listOf(-1f, 1f)) {
            val ear = Path().apply {
                moveTo(x + side * s * 0.06f, y - s * 0.16f)
                lineTo(x + side * s * 0.13f, y - s * 0.34f)
                lineTo(x + side * s * 0.20f, y - s * 0.15f)
                close()
            }
            drawPath(ear, col)
        }
        // alas anchas con borde ondeado (3 ondas), batiendo
        for (side in listOf(-1f, 1f)) {
            val shoulder = Offset(x + side * s * 0.10f, y - s * 0.02f)
            val wingSpan = s * 0.55f
            val lift = flap * s * 0.18f
            val wing = Path().apply {
                moveTo(shoulder.x, shoulder.y)
                quadraticBezierTo(
                    shoulder.x + side * wingSpan * 0.5f, shoulder.y - s * 0.28f - lift,
                    shoulder.x + side * wingSpan, shoulder.y - s * 0.10f - lift
                )
                val waves = 3
                var px = shoulder.x + side * wingSpan
                var py = shoulder.y - s * 0.10f - lift
                for (k in 1..waves) {
                    val nx = shoulder.x + side * wingSpan * (1f - k / waves.toFloat())
                    val ny = shoulder.y + s * 0.02f
                    quadraticBezierTo(
                        (px + nx) / 2f + side * s * 0.02f, (py + ny) / 2f + s * 0.10f,
                        nx, ny
                    )
                    px = nx
                    py = ny
                }
                close()
            }
            drawPath(wing, col)
        }
        // ojazos blancos con pupila (miran hacia donde vuela)
        val eyeCol = Color(0xFFFFF6E0).copy(alpha = alpha)
        val pupCol = Color(0xFF14100E).copy(alpha = alpha)
        for (side in listOf(-1f, 1f)) {
            val ex = x + side * s * 0.07f
            val ey = y - s * 0.08f
            drawCircle(eyeCol, s * 0.055f, Offset(ex, ey))
            drawCircle(pupCol, s * 0.022f, Offset(ex + dir * s * 0.012f, ey + s * 0.008f))
        }
    }
}

@Composable
fun HalloweenBats(modifier: Modifier = Modifier) {
    Box(modifier) {
        BatFlight(
            travelDurationMs = 14000, delayMs = 0,
            yFraction = 0.14f, sizeDp = 84.dp
        )
        BatFlight(
            travelDurationMs = 19000, delayMs = 6000,
            yFraction = 0.48f, sizeDp = 64.dp, maxAlpha = 0.7f, fromRight = true
        )
        BatFlight(
            travelDurationMs = 16000, delayMs = 11000,
            yFraction = 0.74f, sizeDp = 76.dp, maxAlpha = 0.8f
        )
    }
}

// ===========================================================================
// CALAVERA ESPORÁDICA (global, cualquier vista): cada 25-45 s (aleatorio) se
// desliza desde un costado (izquierda o derecha, aleatorio), se asoma ~1/3,
// se queda 1.2 s con un leve meneo y se vuelve a esconder. Solo con el tema
// Halloween; nunca consume toques.
// ===========================================================================

@Composable
fun PeekingSkull(modifier: Modifier = Modifier) {
    var side by remember { mutableStateOf(if (Random.nextBoolean()) -1f else 1f) }
    var peek by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(60000L, 120001L))
            side = if (Random.nextBoolean()) -1f else 1f
            peek = true
            delay(1200L)
            peek = false
        }
    }
    val peekAnim by animateFloatAsState(
        targetValue = if (peek) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "peek"
    )
    val t = rememberInfiniteTransition(label = "skullwig")
    val wig by t.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wig"
    )
    if (peekAnim > 0.01f) {
        val density = LocalDensity.current
        val skullDp = 72.dp
        val skullPx = with(density) { skullDp.toPx() }
        Box(modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .size(skullDp)
                    .align(if (side < 0f) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset {
                        // escondida: toda fuera; asomada: ~1/3 visible
                        val off = side * skullPx * (1f - 0.33f * peekAnim)
                        IntOffset(off.toInt(), 0)
                    }
            ) {
                val s = size.width
                val bone = Color(0xFFE8E4D8).copy(alpha = peekAnim)
                val dark = Color(0xFF1A1512).copy(alpha = peekAnim)
                rotate(wig * peekAnim) {
                    // cráneo
                    drawOval(bone, Offset(s * 0.18f, s * 0.06f), Size(s * 0.64f, s * 0.58f))
                    // mandíbula
                    drawRoundRect(
                        bone, Offset(s * 0.28f, s * 0.52f), Size(s * 0.44f, s * 0.36f),
                        CornerRadius(s * 0.10f, s * 0.10f)
                    )
                    // cuencas de los ojos
                    drawOval(dark, Offset(s * 0.30f, s * 0.36f), Size(s * 0.15f, s * 0.19f))
                    drawOval(dark, Offset(s * 0.55f, s * 0.36f), Size(s * 0.15f, s * 0.19f))
                    // nariz triangular
                    val nose = Path().apply {
                        moveTo(s * 0.50f, s * 0.58f)
                        lineTo(s * 0.44f, s * 0.68f)
                        lineTo(s * 0.56f, s * 0.68f)
                        close()
                    }
                    drawPath(nose, dark)
                    // dientes
                    for (i in 0..3) {
                        val tx = s * (0.34f + i * 0.107f)
                        drawLine(
                            dark, Offset(tx, s * 0.70f), Offset(tx, s * 0.84f),
                            strokeWidth = s * 0.022f, cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
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
