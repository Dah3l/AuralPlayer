// Isla dinámica (v1.0.7): píldora flotante sobre otras apps.
// Posición FIJA bajo el orificio de la cámara (sin arrastre).
// Transición colapsada <-> expandida con morph animado (spring).
package com.aural.player.ui.island

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


/**
 * Contenido del overlay: contenedor ÚNICO cuyos bordes se animan con spring
 * (morph tipo isla) y cuyo contenido hace crossfade + escala al alternar
 * entre píldora colapsada y tarjeta expandida.
 * Lo usa el MusicService dentro de un ComposeView sobre el WindowManager.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandOverlayContent(
    app: com.aural.player.AuralApp,
    onClose: () -> Unit,
    // La ventana sigue al morph cuadro por cuadro (tamaño de la caja);
    // MusicService la mantiene centrada sin saltos.
    onWindowSync: (wDp: Float, hDp: Float) -> Unit = { _, _ -> }
) {
    val playerState by app.playback.state.collectAsState()
    // La isla usa la metadata PERSONALIZADA de la app, no la original
    // del archivo (igual que NowPlaying y la biblioteca).
    val customMeta by app.settings.customMetadata
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val customCovers by app.settings.customCovers
        .collectAsStateWithLifecycle(initialValue = emptyMap())
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
    // Dimensiones adaptativas: la isla nunca supera el ancho/alto de la
    // pantalla (16dp de margen lateral, espacio para el status bar arriba).
    val screenWdp = LocalConfiguration.current.screenWidthDp.toFloat()
    val screenHdp = LocalConfiguration.current.screenHeightDp.toFloat()
    val expandedW = remember(screenWdp) {
        minOf(340f, (screenWdp - 32f).coerceAtLeast(200f))
    }
    val expandedH = remember(screenHdp) {
        // v1.0.8: 308→320 para darle aire al botón ^ de 48dp.
        // La ventana la sigue MusicService.syncIslandWindow() cuadro por
        // cuadro con estos mismos valores (vía onWindowSync).
        minOf(320f, (screenHdp - 140f).coerceAtLeast(220f))
    }

    fun doExpand() {
        if (expanded) return
        expanded = true
        animJob?.cancel()
        animJob = scope.launch {
            // La ventana sigue a la caja cuadro por cuadro: sin saltos
            // instantáneos (el salto descentraba la píldora unos ms en
            // algunos equipos: "panel desde la derecha / pill desde la
            // izquierda"). Se cancela sola con el morph.
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
            // Espejo de la expansión, con la ventana siguiendo a la caja.
            val windowSync = launch {
                snapshotFlow { widthA.value to heightA.value }
                    .collect { (w, h) -> onWindowSync(w, h) }
            }
            try {
                // Espejo de la expansión: primero el contenido se desvanece
                // (120ms), LUEGO la caja vacía se cierra por fases (sin nada
                // que aplastar), y al final entra la píldora con fade.
                panelAlpha.animateTo(0f, tween(120))
                heightA.animateTo(44f, tween(140, easing = ease))
                widthA.animateTo(135f, tween(140, easing = ease))
                cornerA.animateTo(22f, tween(140, easing = ease))
                launch { pillAlpha.animateTo(1f, tween(110)) }
            } finally {
                windowSync.cancel()
            }
            // Termina en el tamaño colapsado exacto: sin salto final.
            onWindowSync(135f, 44f)
        }
    }
    val song = playerState.currentSong ?: return
    val meta = customMeta[song.id]
    val title = meta?.first?.ifBlank { song.title } ?: song.title
    val artist = meta?.second?.ifBlank { song.artist } ?: song.artist
    val coverUri = customCovers[song.id] ?: song.albumArtUri
    // Abrir la app en el Home.
    fun openApp() {
        val ctx = app.applicationContext
        val intent = android.content.Intent(
            ctx, com.aural.player.MainActivity::class.java
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(com.aural.player.MainActivity.EXTRA_OPEN_HOME, true)
        }
        ctx.startActivity(intent)
    }
    // Aspecto de Dynamic Aural: "auto" sigue al tema, "classic" es negro
    // con los acentos en el color primario del tema.
    val appearance by app.settings.islandAppearance
        .collectAsStateWithLifecycle(initialValue = "auto")
    val scheme = MaterialTheme.colorScheme
    val classic = appearance == "classic"
    val islandBg = if (classic) Color(0xFF101014) else scheme.surface
    val islandAccent = scheme.primary
    val islandAccent2 = if (classic) Color(0xFF8B5CF6) else scheme.secondary
    val islandOnBg = if (classic) Color.White else scheme.onSurface
    val islandOnAccent = scheme.onPrimary

    // v1.0.8: favoritas para el corazón del panel + aura luminosa animada.
    val favorites by app.settings.favorites
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val auraOn by app.settings.auraEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val auraMode by app.settings.auraMode
        .collectAsStateWithLifecycle(initialValue = "auto")
    val auraHexes by app.settings.auraColors
        .collectAsStateWithLifecycle(initialValue = listOf("#2ED3B7"))
    val auraColors = remember(auraMode, auraHexes, scheme) {
        if (auraMode == "custom") auraHexes.map { hexToColor(it) }
        else listOf(scheme.primary, scheme.secondary)
    }
    val auraBreath = rememberAuraBreath()
    // El aura solo rodea la píldora colapsada, nunca el panel expandido
    // ni durante el morph (cuando la píldora aún no es visible).
    val auraVisible = auraOn && !expanded && pillAlpha.value > 0.5f

    // La caja morphéa por fases; el contenido solo se renderiza
    // en su fase (con fade), nunca durante el morph.
    val islandShape = RoundedCornerShape(cornerA.value.dp)
    // El padding exterior le da aire a la sombra (14dp): la ventana usa
    // WRAP_CONTENT y sin este aire la sombra se recorta en recto,
    // dejando "esquinas" visibles sobre fondos claros.
    // TopCenter: en expandida la isla queda arriba como antes
    // (la sombra respira abajo); en colapsada no cambia nada.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .size(widthA.value.dp, heightA.value.dp)
            // Faro: ANTES del clip y del fondo, para que la luz salga
            // hacia afuera sin que nada la recorte. Se funde con la
            // píldora (fade) en vez de aparecer de golpe.
            .islandFaroGlow(auraVisible, auraColors, cornerA.value, auraBreath, pillAlpha.value)
            .shadow(14.dp, islandShape)
            .clip(islandShape)
            .background(islandBg.copy(alpha = 0.97f))
            .border(1.dp, islandOnBg.copy(alpha = 0.12f), islandShape),
        contentAlignment = Alignment.TopCenter
    ) {
        if (pillAlpha.value > 0.01f) {
            Box(Modifier.alpha(pillAlpha.value)) {
                IslandCollapsedContent(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    appearance = appearance,
                    onTap = { doExpand() },
                    onPrevious = { app.playback.seekToPreviousSong() },
                    onNext = { app.playback.seekToNext() },
                    onTogglePlay = { app.playback.togglePlayPause() },
                    onHide = onClose
                )
            }
        }
        if (panelAlpha.value > 0.01f) {
            Box(Modifier.alpha(panelAlpha.value)) {
                IslandExpandedContent(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    appearance = appearance,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    onTogglePlay = { app.playback.togglePlayPause() },
                    onNext = { app.playback.seekToNext() },
                    onPrevious = { app.playback.seekToPrevious() },
                    onSeek = { app.playback.seekTo(it) },
                    onOpenApp = { openApp() },
                    onQuickAction = { action -> handleQuickAction(app, action) },
                    onCollapse = { doCollapse() },
                    // v1.0.8: favorito + modo de reproducción.
                    isFavorite = song.id in favorites,
                    onToggleFavorite = {
                        scope.launch { app.settings.toggleFavorite(song.id) }
                    },
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    onCyclePlayMode = { app.playback.cyclePlayMode() }
                )
            }
        }
    }
    } // caja exterior: aire para la sombra
}

/**
 * Píldora colapsada: portada en círculo + título con marquesina.
 * Posición fija — sin arrastre.
 * - Toque: expande a la vista detallada.
 * - Swipe izquierda: siguiente. Swipe derecha: anterior (directo,
 *   sin la regla de los 3 segundos). Long-press: ocultar temporal.
 * - Doble toque: pausa/play.
 * - Mantener presionado: oculta temporalmente.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandCollapsedContent(
    title: String,
    artist: String,
    coverUri: String?,
    appearance: String,
    onTap: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val islandAccent = scheme.primary
    val islandOnBg = if (appearance == "classic") Color.White else scheme.onSurface
    val density = LocalDensity.current
    val context = LocalContext.current
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onTogglePlay() },
                    // Long-press: ocultar temporal.
                    onLongPress = {
                        try {
                            val vib = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                context.getSystemService(
                                    android.os.VibratorManager::class.java
                                )?.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                context.getSystemService(
                                    android.os.Vibrator::class.java
                                )
                            }
                            vib?.vibrate(
                                android.os.VibrationEffect.createOneShot(
                                    100, 255
                                )
                            )
                        } catch (_: Exception) { }
                        onHide()
                    }
                )
            }
            .pointerInput(Unit) {
                // Swipes horizontales: ← siguiente, → anterior.
                var totalX = 0f
                var totalY = 0f
                val swipeDistPx = with(density) { 60.dp.toPx() }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalX) > swipeDistPx &&
                            kotlin.math.abs(totalX) > 2f * kotlin.math.abs(totalY)
                        ) {
                            if (totalX > 0f) onPrevious() else onNext()
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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.width(135.dp), // tamaño fijo ~2.1 cm
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Portada en círculo con la nota musical SIEMPRE de fondo:
            // aunque la imagen falle al cargar, nunca queda vacío.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(islandAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = islandAccent,
                    modifier = Modifier.size(16.dp)
                )
                if (!coverUri.isNullOrBlank()) {
                    AsyncImage(
                        model = android.net.Uri.parse(coverUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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

/** Acciones rápidas del swipe en la píldora. */
enum class QuickAction { YOUTUBE_SEARCH, RECOGNIZE }

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
            repeatMode = RepeatMode.Restart
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
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
        }
    }
}

/**
 * Isla expandida compacta: portada + título/artista + onda, progreso
 * arrastrable (seek con gestos), controles ⏮ ⏯ ⏭ y acciones rápidas.
 */
@Composable
private fun IslandExpandedContent(
    title: String,
    artist: String,
    coverUri: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenApp: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onCollapse: () -> Unit,
    appearance: String,
    // v1.0.8: modo de reproducción + favorito en la fila de transporte.
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: com.aural.player.playback.RepeatMode,
    onCyclePlayMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Colores según el aspecto (auto = tema, classic = negro + primario).
    val scheme = MaterialTheme.colorScheme
    val classic = appearance == "classic"
    val islandAccent = scheme.primary
    val islandAccent2 = if (classic) Color(0xFF8B5CF6) else scheme.secondary
    val islandOnBg = if (classic) Color.White else scheme.onSurface
    val islandOnAccent = scheme.onPrimary
    var sliderPos by remember(positionMs) { mutableStateOf(positionMs.toFloat()) }
    var seeking by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Column(
        modifier = modifier
            // Swipe hacia arriba (solo en la vista detallada): compactar.
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
                    onDrag = { _, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2129)),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder siempre de fondo (patrón CoverArt de la app):
                // si no hay portada, no queda el cuadro vacío.
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = islandAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                if (!coverUri.isNullOrBlank()) {
                    AsyncImage(
                        model = android.net.Uri.parse(coverUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
        // Progreso arrastrable (seek con gestos: en la notificación no se puede).
        val dur = durationMs.coerceAtLeast(1L)
        Slider(
            value = if (seeking) sliderPos else positionMs.toFloat().coerceIn(0f, dur.toFloat()),
            onValueChange = {
                seeking = true
                sliderPos = it
            },
            onValueChangeFinished = {
                onSeek(sliderPos.toLong())
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
                text = formatMs(if (seeking) sliderPos.toLong() else positionMs),
                color = islandOnBg.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Text(
                text = formatMs(durationMs),
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
            // v1.0.8: el botón de modos ocupa el slot de YouTube.
            // Muestra el modo actual: 🔀 aleatorio, 🔂 repetir una, 🔁 repetir todo.
            val modeIcon = when {
                shuffleEnabled -> Icons.Default.Shuffle
                repeatMode == com.aural.player.playback.RepeatMode.ONE -> Icons.Default.RepeatOne
                else -> Icons.Default.Repeat
            }
            val modeActive = shuffleEnabled || repeatMode != com.aural.player.playback.RepeatMode.OFF
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
                    Icons.Default.SkipPrevious,
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
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = islandOnAccent,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tint = islandOnBg,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            // v1.0.8: el corazón ocupa el slot del micrófono.
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Añadir a favoritas",
                    tint = if (isFavorite) Color(0xFFE5484D)
                    else islandOnBg.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // v1.0.8: YouTube y micrófono flanquean "Abrir Aural" como botones
        // circulares con el mismo estilo tonal del pill.
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
                    .clickable { onQuickAction(QuickAction.YOUTUBE_SEARCH) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Search,
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
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Abrir Aural",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onQuickAction(QuickAction.RECOGNIZE) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Reconocer",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // Compactar: flecha ^ estilizada, centrada al pie del panel.
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Compactar isla",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

/** Ejecuta una acción rápida: abre la app en la pantalla correspondiente. */
private fun handleQuickAction(app: com.aural.player.AuralApp, action: QuickAction) {
    val ctx = app.applicationContext
    val intent = android.content.Intent(ctx, com.aural.player.MainActivity::class.java).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        when (action) {
            QuickAction.YOUTUBE_SEARCH ->
                putExtra(com.aural.player.MainActivity.EXTRA_OPEN_YOUTUBE_TAB, true)
            QuickAction.RECOGNIZE ->
                putExtra(
                    com.aural.player.AudioSearchTileService.EXTRA_OPEN_AUDIO_SEARCH,
                    true
                )
        }
    }
    ctx.startActivity(intent)
}

/** Convierte un hex "#RRGGBB" a Color (opaco). */
private fun hexToColor(hex: String): Color =
    Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)

/**
 * v1.0.8: pincel animado del aura — un "cometa" de luz que recorre el
 * borde en giro fluido e infinito. Los stops del degradado cónico se
 * recalculan cada frame con la fase, así la luz viaja alrededor de la
 * píldora sin rotar la geometría (funciona con cualquier forma).
 */
/**
 * Respiración del glow faro (0..1, ida y vuelta ~2.4s). Devuelve el State
 * en vez del Float: el draw lo lee sin recomponer, solo redibuja → 60fps.
 */
@Composable
private fun rememberAuraBreath(): State<Float> {
    return rememberInfiniteTransition(label = "auraBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "auraBreath"
    )
}

/**
 * Glow FARO: la luz sale DIRECTO del borde de la píldora, HACIA AFUERA
 * (como su sombra, pero con color), y RESPIRA: el grosor y el alpha pulsan
 * de forma continua y fluida. Se dibuja con drawBehind ANTES del clip y
 * del fondo: el fondo de la píldora tapa la parte interior y solo se ve
 * hacia afuera, sin que nada lo recorte. 3 capas = luz difuminada, no
 * línea. Colores sólidos, sin shaders por cuadro → 60fps.
 * El alcance máximo (~15dp) cabe en el aire de 16dp de la ventana.
 */
private fun Modifier.islandFaroGlow(
    enabled: Boolean,
    colors: List<Color>,
    cornerDp: Float,
    breath: State<Float>,
    fade: Float
): Modifier = this.then(
    Modifier.drawBehind {
        val a = fade.coerceIn(0f, 1f)
        if (!enabled || a <= 0.01f) return@drawBehind
        val c1 = colors.getOrElse(0) { Color(0xFF2ED3B7) }
        val b = breath.value // 0..1, ida y vuelta
        val corner = cornerDp.dp.toPx()
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        // Cada capa se expande su propio ancho/2 + 1dp: el trazo entero
        // queda FUERA del borde de la píldora.
        fun layer(widthPx: Float, alphaScale: Float) {
            val expand = widthPx / 2f + 1.dp.toPx()
            drawRoundRect(
                color = c1.copy(alpha = alphaScale * a),
                topLeft = Offset(-expand, -expand),
                size = Size(size.width + expand * 2, size.height + expand * 2),
                cornerRadius = CornerRadius(corner + expand),
                style = Stroke(widthPx)
            )
        }
        // Halo → medio → núcleo, todos respirando con b.
        layer(10.dp.toPx() + 4.dp.toPx() * b, 0.10f + 0.10f * b)
        layer(6.dp.toPx() + 4.dp.toPx() * b, 0.20f + 0.20f * b)
        layer(3.dp.toPx() + 3.dp.toPx() * b, 0.40f + 0.30f * b)
    }
)
