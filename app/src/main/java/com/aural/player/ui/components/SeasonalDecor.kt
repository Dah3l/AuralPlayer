// Decoración de temporada dibujada en código (SIN imágenes externas).
//
// Halloween (27-oct..1-nov): telaraña + araña en el home, fantasmas en el
// Now Playing, calavera esporádica global. Cada personaje se DIBUJA UNA SOLA
// (a 2x para nitidez) y todo el movimiento va en Modifier.graphicsLayer
// (transforms resueltos por el GPU/RenderThread): cero redibujo por frame,
// fluido incluso en gamas medias. Ningún decorado consume toques.
//
// Navidad (20-26 dic): STUB — ver punto de enganche al final del archivo.
package com.aural.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import com.aural.player.ui.theme.HalloweenGhost
import com.aural.player.ui.theme.HalloweenPumpkin
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ===========================================================================
// PRE-RENDER: dibuja UNA VEZ a un bitmap offscreen (2x para nitidez).
// El movimiento posterior es solo graphicsLayer: cero redibujo por frame.
// ===========================================================================

@Composable
fun rememberRenderedBitmap(widthPx: Int, heightPx: Int, draw: DrawScope.() -> Unit): ImageBitmap {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(widthPx, heightPx) {
        ImageBitmap(widthPx, heightPx).also { bmp ->
            CanvasDrawScope().draw(
                density, layoutDirection, Canvas(bmp),
                Size(widthPx.toFloat(), heightPx.toFloat())
            ) { draw() }
        }
    }
}

// ===========================================================================
// FANTASMA: figura vectorial (espacio 100x100, alfa 1 — el fade va en capa).
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

private fun DrawScope.drawGhostAssembly() {
    // espacio 100x100
    drawGhostFigure(50f, 50f, 66f, HalloweenGhost, 1f)
}

// ===========================================================================
// TELARAÑA: diseño clásico de orbe (espacio 170x170, alfa 1 — el brillo y la
// mecedora van en capa). Hilos radiales + espiral con leve comba.
// ===========================================================================

private fun DrawScope.drawWebAssembly() {
    val boxPx = 170f
    val anchor = Offset(boxPx, 0f)
    val col = Color.White
    val lineStyle = Stroke(width = 1.8f, cap = StrokeCap.Round)
    val maxR = boxPx * 1.04f
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

// ===========================================================================
// ARAÑA: conjunto hilo + cuerpo (espacio 96x220). El hilo ocupa todo el
// alto; el cuerpo cuelga al final. Dos poses de patas (bitmaps A/B).
// ===========================================================================

private fun DrawScope.drawSpiderAssembly(legsUp: Boolean) {
    val w = 96f
    val h = 220f
    val cx = w / 2f
    val bodyY = h - 32f
    // Gris medio: contrasta con el fondo oscuro del tema (el negro se perdía)
    val col = Color(0xFF9E9E9E)
    // hilo
    drawLine(col, Offset(cx, 0f), Offset(cx, bodyY - 12f), strokeWidth = 2f)
    // 8 patas: dos poses (arriba/abajo)
    val legSign = if (legsUp) 1f else -1f
    for (side in listOf(-1f, 1f)) {
        for (i in 0..3) {
            val wob = legSign * (5f + i * 2.5f)
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
    drawCircle(HalloweenPumpkin, 2.4f, Offset(cx - 4f, bodyY - 22f))
    drawCircle(HalloweenPumpkin, 2.4f, Offset(cx + 4f, bodyY - 22f))
}

// ===========================================================================
// CALAVERA: cráneo + mandíbula + dientes (espacio 100x100, alfa 1).
// ===========================================================================

private fun DrawScope.drawSkullAssembly() {
    val s = 100f
    val bone = Color(0xFFE8E4D8)
    val dark = Color(0xFF1A1512)
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

// ===========================================================================
// AMBIENTE HOME: telaraña + araña.
// Movimiento 100% en graphicsLayer (GPU): la pantalla nunca se redibuja.
// ===========================================================================

@Composable
fun HalloweenHomeAmbient(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "hw")
    // Los valores animados solo alimentan graphicsLayer (cero redibujo).
    val webRot by t.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "webRot")
    val webAl by t.animateFloat(0.14f, 0.30f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "webAl")
    val spP by t.animateFloat(0f, 1f, infiniteRepeatable(tween(15000), RepeatMode.Restart), label = "spP")
    val legFlip by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Restart), label = "legFlip")

    val density = LocalDensity.current
    val u = density.density // px por dp

    // ---- Bitmaps: se renderizan UNA VEZ ----
    val webPx = (170f * u * 2f).toInt()
    val webBmp = rememberRenderedBitmap(webPx, webPx) {
        scale(webPx / 170f, webPx / 170f, pivot = Offset.Zero) { drawWebAssembly() }
    }
    val spiderWPx = (96f * u * 2f).toInt()
    val spiderHPx = (220f * u * 2f).toInt()
    val spiderBmpA = rememberRenderedBitmap(spiderWPx, spiderHPx) {
        scale(spiderWPx / 96f, spiderHPx / 220f, pivot = Offset.Zero) { drawSpiderAssembly(legsUp = true) }
    }
    val spiderBmpB = rememberRenderedBitmap(spiderWPx, spiderHPx) {
        scale(spiderWPx / 96f, spiderHPx / 220f, pivot = Offset.Zero) { drawSpiderAssembly(legsUp = false) }
    }

    BoxWithConstraints(modifier) {
        // ---- Telaraña: fija arriba a la derecha; mecedora + brillo en capa ----
        Image(
            bitmap = webBmp,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(170.dp)
                .graphicsLayer {
                    rotationZ = webRot
                    transformOrigin = TransformOrigin(1f, 0f)
                    alpha = webAl
                }
        )
        // ---- Araña: baja/sube por el hilo (el bitmap lleva el hilo completo;
        // se traslada en Y para simular la bajada), balanceo + patas A/B ----
        // Los dos bitmaps (patas A/B) se apilan y la visibilidad se controla
        // con alpha DENTRO de graphicsLayer: cero recomposición.
        Image(
            bitmap = spiderBmpA,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 110.dp)
                .size(96.dp, 220.dp)
                .graphicsLayer {
                    val dropMaxPx = 150f * u
                    val dropPx = dropMaxPx * when {
                        spP < 0.32f -> spP / 0.32f
                        spP < 0.68f -> 1f
                        else -> 1f - (spP - 0.68f) / 0.32f
                    }
                    val spAlpha = when {
                        spP < 0.04f -> spP / 0.04f
                        spP > 0.96f -> (1f - spP) / 0.04f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    val hanging = spP in 0.32f..0.68f
                    val swayX =
                        if (hanging) sin((spP - 0.32f) / 0.36f * 2f * PI.toFloat() * 2f) * 18f else 0f
                    // el cuerpo está dibujado a 188dp del borde superior del bitmap
                    val transY = (8f * u + dropPx) - (188f * u)
                    translationY = transY
                    translationX = swayX
                    alpha = spAlpha * (if (legFlip < 0.5f) 1f else 0f)
                }
        )
        Image(
            bitmap = spiderBmpB,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 110.dp)
                .size(96.dp, 220.dp)
                .graphicsLayer {
                    val dropMaxPx = 150f * u
                    val dropPx = dropMaxPx * when {
                        spP < 0.32f -> spP / 0.32f
                        spP < 0.68f -> 1f
                        else -> 1f - (spP - 0.68f) / 0.32f
                    }
                    val spAlpha = when {
                        spP < 0.04f -> spP / 0.04f
                        spP > 0.96f -> (1f - spP) / 0.04f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    val hanging = spP in 0.32f..0.68f
                    val swayX =
                        if (hanging) sin((spP - 0.32f) / 0.36f * 2f * PI.toFloat() * 2f) * 18f else 0f
                    val transY = (8f * u + dropPx) - (188f * u)
                    translationY = transY
                    translationX = swayX
                    alpha = spAlpha * (if (legFlip < 0.5f) 0f else 1f)
                }
        )
    }
}

// ===========================================================================
// FANTASMAS (Now Playing): 2 fantasmas a la deriva detrás del contenido.
// Movimiento 100% en graphicsLayer (GPU): la pantalla nunca se redibuja.
// ===========================================================================

@Composable
fun HalloweenGhosts(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "hwghosts")
    // Los valores animados SOLO se leen dentro de graphicsLayer
    // (invalida la capa GPU, no recompone).
    val g1p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26000), RepeatMode.Restart), label = "g1p")
    val g2p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(33000), RepeatMode.Restart), label = "g2p")

    val density = LocalDensity.current
    val u = density.density // px por dp

    // ---- Bitmap compartido: se renderiza UNA VEZ ----
    val ghostPx = (58f * u * 2f).toInt()
    val ghostBmp = rememberRenderedBitmap(ghostPx, ghostPx) {
        scale(ghostPx / 100f, ghostPx / 100f, pivot = Offset.Zero) { drawGhostAssembly() }
    }

    BoxWithConstraints(modifier) {
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }

        // ---- Fantasma 1: deriva continua, aparece y se desvanece ----
        Image(
            bitmap = ghostBmp,
            contentDescription = null,
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer {
                    val phase = g1p % 1f
                    val gx = -140f + phase * (maxWpx + 280f)
                    val gy = 0.25f * maxHpx + sin(phase * 2f * PI.toFloat() * 2.5f) * 30f
                    val ga = sin(phase * PI.toFloat()).coerceIn(0f, 1f) * 0.42f
                    val dispPx = 58f * u
                    translationX = gx - dispPx / 2f
                    translationY = gy - dispPx / 2f
                    alpha = ga
                }
        )
        // ---- Fantasma 2 ----
        Image(
            bitmap = ghostBmp,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer {
                    val phase = (g2p + 11000f / 33000f) % 1f
                    val gx = maxWpx + 140f - phase * (maxWpx + 280f)
                    val gy = 0.60f * maxHpx + sin(phase * 2f * PI.toFloat() * 2.5f) * 30f
                    val ga = sin(phase * PI.toFloat()).coerceIn(0f, 1f) * 0.32f
                    val dispPx = 42f * u
                    translationX = gx - dispPx / 2f
                    translationY = gy - dispPx / 2f
                    alpha = ga
                }
        )
    }
}

// ===========================================================================
// CALAVERA ESPORÁDICA (global, cualquier vista): cada 1-2 min (aleatorio) se
// desliza desde un costado (izquierda o derecha, aleatorio), se asoma ~60%,
// se queda 1.6 s con un leve meneo y se vuelve a esconder. Solo con el tema
// Halloween; nunca consume toques. Asomo y meneo en graphicsLayer.
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
            delay(1600L)
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
        val u = density.density
        val skullPx = (88f * u * 2f).toInt()
        val skullBmp = rememberRenderedBitmap(skullPx, skullPx) {
            scale(skullPx / 100f, skullPx / 100f, pivot = Offset.Zero) { drawSkullAssembly() }
        }
        val dispPx = 88f * u
        Box(modifier.fillMaxSize()) {
            Image(
                bitmap = skullBmp,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .align(if (side < 0f) Alignment.CenterStart else Alignment.CenterEnd)
                    .graphicsLayer {
                        // escondida: toda fuera; asomada: ~60% visible
                        translationX = side * dispPx * (1f - 0.60f * peekAnim)
                        rotationZ = wig * peekAnim
                        alpha = peekAnim
                    }
            )
        }
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
//       ChristmasHomeAmbient(): cada motivo navideño se pre-renderiza con
//       rememberRenderedBitmap y se anima SOLO con graphicsLayer
//       (translation/rotation/alpha), igual que el Halloween.
//   - En cada pantalla, el decorado se muestra cuando el tema activo ==
//     CHRISTMAS (if (themeName == AppTheme.CHRISTMAS.name)).
// ---------------------------------------------------------------------------
