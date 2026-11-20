// Isla flotante de la PC: puerto de la isla de Android
// (ui/island/DynamicIsland.kt). Píldora con la canción que suena y
// panel expandido con morph animado, onda, transporte y acciones
// rápidas. Adaptaciones a Windows: la ventana se arrastra para
// moverla (el arrastre lo pone la ventana, no este contenido),
// doble clic = pausa/play, clic derecho = ocultar, y sin botón de
// micrófono (la PC no reconoce audio).
package com.aural.player.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Contenido de la isla: contenedor ÚNICO cuyos bordes se animan con
 * morph y cuyo contenido hace crossfade al alternar entre píldora
 * colapsada y tarjeta expandida. La ventana sigue al morph cuadro
 * por cuadro vía onWindowSync (ancho y alto en dp).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandContent(
    state: PlaybackState,
    actions: PlayerActions,
    loadCoverBytes: (Song) -> ByteArray?,
    onOpenApp: () -> Unit,
    onOpenYouTube: () -> Unit,
    onHide: () -> Unit,
    onWindowSync: (wDp: Float, hDp: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return
    val coverBitmap = rememberCoverImage(song, loadCoverBytes)
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Coreografía por fases (suave e ininterrumpida):
    //  expandir = ancho -> alto -> fade del panel
    //  colapsar = alto -> ancho con el contenido visible (wipe) -> desrender
    val ease = remember { CubicBezierEasing(0.32f, 0.72f, 0f, 1f) }
    val widthA = remember { Animatable(135f) }
    val heightA = remember { Animatable(44f) }
    val cornerA = remember { Animatable(22f) }
    val pillAlpha = remember { Animatable(1f) }
    val panelAlpha = remember { Animatable(0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    // La isla nunca supera 340x320 (como en Android).
    val expandedW = 340f
    val expandedH = 320f

    fun doExpand() {
        if (expanded) return
        expanded = true
        animJob?.cancel()
        animJob = scope.launch {
            val windowSync = launch {
                snapshotFlow { widthA.value to heightA.value }
                    .collect { (w, h) -> onWindowSync(w, h) }
            }
            try {
                // Fase 1: la píldora se desvanece mientras el ancho crece.
                launch { pillAlpha.animateTo(0f, tween(120)) }
                widthA.animateTo(expandedW, tween(150, easing = ease))
                cornerA.animateTo(28f, tween(150, easing = ease))
                // Fase 2: la altura, ya con el ancho objetivo.
                heightA.animateTo(expandedH, tween(150, easing = ease))
                // Fase 3: el contenido del panel entra con fade.
                panelAlpha.animateTo(1f, tween(120))
            } finally {
                windowSync.cancel()
            }
            onWindowSync(expandedW, expandedH)
        }
    }
    fun doCollapse() {
        if (!expanded) return
        expanded = false
        animJob?.cancel()
        animJob = scope.launch {
            val windowSync = launch {
                snapshotFlow { widthA.value to heightA.value }
                    .collect { (w, h) -> onWindowSync(w, h) }
            }
            try {
                panelAlpha.animateTo(0f, tween(120))
                heightA.animateTo(44f, tween(140, easing = ease))
                widthA.animateTo(135f, tween(140, easing = ease))
                cornerA.animateTo(22f, tween(140, easing = ease))
                launch { pillAlpha.animateTo(1f, tween(110)) }
            } finally {
                windowSync.cancel()
            }
            onWindowSync(135f, 44f)
        }
    }

    // Aspecto: "auto" sigue al tema, "classic" es negro con los
    // acentos en el color primario del tema.
    val scheme = MaterialTheme.colorScheme
    val classic = state.islandAppearance == "classic"
    val islandBg = if (classic) Color(0xFF101014) else scheme.surface
    val islandAccent = scheme.primary
    val islandAccent2 = if (classic) Color(0xFF8B5CF6) else scheme.secondary
    val islandOnBg = if (classic) Color.White else scheme.onSurface
    val islandOnAccent = scheme.onPrimary
    // Cometa de Aural Light: color del tema (sin selector de color en PC).
    val lightColor = scheme.primary

    val islandShape = RoundedCornerShape(cornerA.value.dp)
    // El padding exterior le da aire a la sombra (16dp): sin este
    // aire la sombra se recorta en recto.
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .size(widthA.value.dp, heightA.value.dp)
                // Cometa: ANTES de shadow/clip/fondo para que nada lo
                // recorte ni lo tape. Se funde con la píldora durante
                // el morph en vez de aparecer/desaparecer de golpe.
                .then(
                    if (state.cometEnabled) Modifier.cometBorder(
                        color = lightColor,
                        cornerRadius = cornerA.value.dp,
                        fade = pillAlpha.value
                    ) else Modifier
                )
                .shadow(14.dp, islandShape)
                .clip(islandShape)
                .background(islandBg.copy(alpha = 0.97f))
                .border(1.dp, islandOnBg.copy(alpha = 0.12f), islandShape),
            contentAlignment = Alignment.TopCenter
        ) {
            if (pillAlpha.value > 0.01f) {
                Box(Modifier.alpha(pillAlpha.value)) {
                    IslandCollapsedContent(
                        title = song.title,
                        artist = song.artist,
                        coverBitmap = coverBitmap,
                        islandAccent = islandAccent,
                        islandOnBg = islandOnBg,
                        onTap = { doExpand() },
                        onTogglePlay = { actions.togglePlay() },
                        onHide = onHide
                    )
                }
            }
            if (panelAlpha.value > 0.01f) {
                Box(Modifier.alpha(panelAlpha.value)) {
                    IslandExpandedContent(
                        title = song.title,
                        artist = song.artist,
                        coverBitmap = coverBitmap,
                        isPlaying = state.isPlaying,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isFavorite = song.filePath in state.favorites,
                        shuffleEnabled = state.shuffleEnabled,
                        repeatMode = state.repeatMode,
                        islandAccent = islandAccent,
                        islandAccent2 = islandAccent2,
                        islandOnBg = islandOnBg,
                        islandOnAccent = islandOnAccent,
                        classic = classic,
                        onTogglePlay = { actions.togglePlay() },
                        onNext = { actions.next() },
                        onPrevious = { actions.previous() },
                        onSeek = { actions.seek(it) },
                        onToggleFavorite = { actions.toggleFavorite(song) },
                        onCyclePlayMode = { actions.cyclePlaybackMode() },
                        onOpenApp = onOpenApp,
                        onOpenYouTube = onOpenYouTube,
                        onCollapse = { doCollapse() }
                    )
                }
            }
        }
    }
}

/**
 * Píldora colapsada: portada en círculo + título con marquesina.
 * - Clic: expande a la vista detallada.
 * - Doble clic: pausa/play.
 * - Clic derecho: ocultar temporal (vuelve con la siguiente canción).
 * - Arrastrar: mueve la ventana (lo detecta la ventana, no aquí).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandCollapsedContent(
    title: String,
    artist: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    islandAccent: Color,
    islandOnBg: Color,
    onTap: () -> Unit,
    onTogglePlay: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onTogglePlay() }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed
                        ) {
                            onHide()
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.width(135.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                bitmap = coverBitmap,
                modifier = Modifier.size(28.dp),
                cornerRadius = 14.dp,
                showBorder = false
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (artist.isBlank()) title else "$title — $artist",
                color = islandOnBg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

/** Onda animada de colores (indicador de reproducción). */
@Composable
fun IslandWave(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color1: Color = Color(0xFF2ED3B7),
    color2: Color = Color(0xFF8B5CF6),
    color3: Color = Color(0xFFFF7A1A)
) {
    val t = rememberInfiniteTransition(label = "island_wave")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1600 else 4000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amp = if (isPlaying) h * 0.38f else h * 0.12f
        listOf(
            color1 to 0f,
            color2 to 2.1f,
            color3 to 4.2f
        ).forEach { (color, off) ->
            val path = Path()
            val steps = 40
            for (i in 0..steps) {
                val x = w * i / steps
                val y = midY + sin(phase + off + x / w * 6.28f).toFloat() * amp
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = color.copy(alpha = 0.85f),
                style = Stroke(width = 3f)
            )
        }
    }
}

/**
 * Isla expandida: portada + título/artista + onda, progreso
 * arrastrable, controles ⏮ ⏯ ⏭ y acciones rápidas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IslandExpandedContent(
    title: String,
    artist: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    islandAccent: Color,
    islandAccent2: Color,
    islandOnBg: Color,
    islandOnAccent: Color,
    classic: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenYouTube: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var sliderPos by remember(positionMs) { mutableStateOf(positionMs.toFloat()) }
    var seeking by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Column(
        modifier = modifier
            // Arrastrar hacia arriba (solo en la vista detallada): compactar.
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                val swipeDistPx = with(density) { 60.dp.toPx() }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        if (totalY < -swipeDistPx &&
                            kotlin.math.abs(totalY) > 2f * kotlin.math.abs(totalX)
                        ) {
                            onCollapse()
                        }
                    },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(
                bitmap = coverBitmap,
                modifier = Modifier.size(56.dp),
                cornerRadius = 14.dp,
                showBorder = false
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = islandOnBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = islandOnBg.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IslandWave(
                isPlaying = isPlaying,
                modifier = Modifier.size(width = 52.dp, height = 26.dp),
                color1 = islandAccent,
                color2 = islandAccent2,
                color3 = if (classic) Color(0xFFFF7A1A) else scheme.tertiary
            )
        }
        Spacer(Modifier.height(12.dp))
        // Progreso arrastrable (busca solo al soltar).
        val dur = durationMs.coerceAtLeast(1L)
        Slider(
            value = if (seeking) sliderPos else positionMs.toFloat().coerceIn(0f, dur.toFloat()),
            onValueChange = {
                seeking = true
                sliderPos = it
            },
            onValueChangeFinished = {
                onSeek(sliderPos / dur.toFloat())
                seeking = false
            },
            valueRange = 0f..dur.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = islandAccent,
                activeTrackColor = islandAccent,
                inactiveTrackColor = islandOnBg.copy(alpha = 0.2f)
            ),
            // Barra fina estilizada (menos gruesa que el Slider por defecto).
            track = { sliderState ->
                val range = sliderState.valueRange
                val fraction =
                    if (range.endInclusive > range.start)
                        ((sliderState.value - range.start) /
                                (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(islandOnBg.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(islandAccent)
                    )
                }
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .shadow(
                            4.dp, CircleShape,
                            spotColor = islandAccent.copy(alpha = 0.6f)
                        )
                        .clip(CircleShape)
                        .background(islandAccent)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(if (seeking) sliderPos.toLong() else positionMs),
                color = islandOnBg.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Text(
                text = formatTime(durationMs),
                color = islandOnBg.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Botón de modos: muestra el modo actual (aleatorio,
            // repetir una, repetir todo).
            val modeIcon = when {
                shuffleEnabled -> AuralIcons.Shuffle
                repeatMode == RepeatMode.ONE -> AuralIcons.RepeatOne
                else -> AuralIcons.Repeat
            }
            val modeActive = shuffleEnabled || repeatMode != RepeatMode.OFF
            IconButton(
                onClick = onCyclePlayMode,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    modeIcon,
                    contentDescription = "Modo de reproducción",
                    tint = islandOnBg.copy(alpha = if (modeActive) 1f else 0.45f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(
                    AuralIcons.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = islandOnBg,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(60.dp)
                    .background(islandAccent, CircleShape)
            ) {
                Icon(
                    if (isPlaying) AuralIcons.Pause else AuralIcons.Play,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = islandOnAccent,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    AuralIcons.SkipNext,
                    contentDescription = "Siguiente",
                    tint = islandOnBg,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    if (isFavorite) AuralIcons.Favorite else AuralIcons.FavoriteBorder,
                    contentDescription = "Añadir a favoritas",
                    tint = if (isFavorite) Color(0xFFE5484D)
                    else islandOnBg.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // YouTube flanquea "Abrir Aural" como botón circular con el
        // mismo estilo tonal de la píldora (sin micrófono en PC).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onOpenYouTube() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AuralIcons.Search,
                    contentDescription = "Buscar en YouTube",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onOpenApp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Abrir Aural",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            // Compactar: flecha ^ estilizada, centrada al pie del panel.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AuralIcons.ArrowUp,
                    contentDescription = "Compactar isla",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cometa de la isla (puerto de DynamicIsland.kt): una luz que recorre
// el borde de la píldora en movimiento continuo. Sin shaders por
// cuadro (los Path se reutilizan, el contorno solo se recalcula al
// cambiar el tamaño) y sin costura (el segmento que cruza el inicio
// se parte en dos). Parámetro fade para que se funda con la píldora
// durante el morph, en vez de aparecer/desaparecer de golpe.
// ---------------------------------------------------------------------------

private const val COMET_STEPS = 40 // segmentos de la cola: más segmentos = caída más suave, sin bandas

private class CometGeometry {
    private val outline = Path()
    private val measure = PathMeasure()
    val segments = Array(COMET_STEPS) { Path() }
    var length = 0f; private set
    private var w = -1f; private var h = -1f; private var r = -1f; private var inset = -1f

    fun update(w: Float, h: Float, r: Float, inset: Float) {
        if (w == this.w && h == this.h && r == this.r && inset == this.inset) return
        this.w = w; this.h = h; this.r = r; this.inset = inset
        outline.rewind()
        outline.addRoundRect(
            RoundRect(inset, inset, w - inset, h - inset, CornerRadius((r - inset).coerceAtLeast(0f)))
        )
        measure.setPath(outline, false)
        length = measure.length
    }

    fun position(d: Float): Offset = measure.getPosition(d)

    fun build(head: Float, tail: Float) {
        for (i in 0 until COMET_STEPS) {
            val s = head - tail * (1f - i / COMET_STEPS.toFloat())
            val e = head - tail * (1f - (i + 1) / COMET_STEPS.toFloat())
            fill(segments[i], s, e)
        }
    }

    // Parte el segmento si cruza el punto de inicio del path: sin costura.
    private fun fill(out: Path, s0: Float, e0: Float) {
        out.rewind()
        var s = s0; var e = e0
        if (e <= 0f) { s += length; e += length }
        if (s < 0f) {
            measure.getSegment(s + length, length, out, true)
            measure.getSegment(0f, e, out, true)
        } else {
            measure.getSegment(s, e, out, true)
        }
    }
}

@Composable
fun Modifier.cometBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 2.5.dp, // solo la línea: más gruesa y visible
    tailFraction: Float = 0.35f, // largo de la cola respecto al perímetro
    periodMillis: Int = 4800, // recorrido lento, como un brillo
    fade: Float = 1f,
): Modifier {
    val progress = rememberInfiniteTransition(label = "comet").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
        label = "cometProgress",
    )
    val geo = remember { CometGeometry() }

    return drawWithContent {
        drawContent() // primero la píldora; el cometa va encima y sin recorte
        val f = fade.coerceIn(0f, 1f)
        if (f <= 0.01f) return@drawWithContent
        val stroke = strokeWidth.toPx()
        geo.update(size.width, size.height, cornerRadius.toPx(), stroke / 2f)

        val head = progress.value * geo.length // se lee solo en fase de dibujo
        geo.build(head, geo.length * tailFraction)

        // Solo la línea: sin capas de glow ni halos, solo el núcleo
        // con la cola desvanecida. Los segmentos usan Butt para que las
        // juntas no se vean (con Round cada junta dejaba un bulto y la
        // cola se veía por secciones); la cabeza se redondea con un
        // círculo del mismo ancho que la línea (radio = stroke/2).
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)
        for (i in 0 until COMET_STEPS) {
            val t = (i + 0.5f) / COMET_STEPS
            val a = t * t * f // cola con caída suave + fade
            if (a < 0.01f) continue
            drawPath(geo.segments[i], color.copy(alpha = a), style = style)
        }
        // Cabeza redondeada: mismo ancho que la línea, sin punto.
        drawCircle(color.copy(alpha = f), radius = stroke / 2f, center = geo.position(head))
    }
}
