// Isla dinámica (v1.0.7): píldora flotante sobre otras apps.
// Posición FIJA bajo el orificio de la cámara (sin arrastre).
// Transición colapsada <-> expandida con morph animado (spring).
package com.aural.player.ui.island

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.sin

private val IslandShape = RoundedCornerShape(28.dp)

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
    onClose: () -> Unit
) {
    val playerState by app.playback.state.collectAsState()
    // La isla usa la metadata PERSONALIZADA de la app, no la original
    // del archivo (igual que NowPlaying y la biblioteca).
    val customMeta by app.settings.customMetadata
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val customCovers by app.settings.customCovers
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    var expanded by remember { mutableStateOf(false) }
    val song = playerState.currentSong ?: return
    val meta = customMeta[song.id]
    val title = meta?.first?.ifBlank { song.title } ?: song.title
    val artist = meta?.second?.ifBlank { song.artist } ?: song.artist
    val coverUri = customCovers[song.id] ?: song.albumArtUri
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
    // Swipe hacia abajo: marcar/desmarcar favorita.
    fun toggleFavorite() {
        scope.launch { app.settings.toggleFavorite(song.id) }
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

    Box(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .shadow(14.dp, IslandShape)
            .clip(IslandShape)
            .background(islandBg.copy(alpha = 0.97f))
            .border(1.dp, islandOnBg.copy(alpha = 0.12f), IslandShape)
            // Sin animateContentSize: el SizeTransform de la transición
            // anima los bordes de forma coordinada (los dos mecanismos
            // peleando por el tamaño era lo que se veía roto).
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                // Opción A: morph en dos tiempos estrictos. El contenido
                // viejo se va rápido (100ms), el contenedor se morphéa
                // VACÍO — solo el fondo negro, 300ms — y el contenido nuevo
                // entra con fade DESPUÉS (300ms). Nunca se ve contenido
                // deformándose. Total ~480ms: rápida pero legible.
                val sizeSpec: FiniteAnimationSpec<IntSize> =
                    tween(300, easing = FastOutSlowInEasing)
                (fadeIn(tween(180, delayMillis = 300)) togetherWith
                        fadeOut(tween(100))).using(
                    SizeTransform(clip = true) { _, _ -> sizeSpec }
                )
            },
            label = "island_morph"
        ) { targetExpanded ->
            if (targetExpanded) {
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
                    onCollapse = { expanded = false }
                )
            } else {
                IslandCollapsedContent(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    appearance = appearance,
                    onOpenApp = { openApp() },
                    onToggleFavorite = { toggleFavorite() },
                    onTap = { expanded = true },
                    onPrevious = { app.playback.seekToPreviousSong() },
                    onNext = { app.playback.seekToNext() },
                    onTogglePlay = { app.playback.togglePlayPause() },
                    onHide = onClose
                )
            }
        }
    }
}

/**
 * Píldora colapsada: portada en círculo + título con marquesina.
 * Posición fija — sin arrastre.
 * - Toque: expande a la vista detallada.
 * - Swipe izquierda: siguiente. Swipe derecha: anterior (directo,
 *   sin la regla de los 3 segundos). Swipe arriba: ocultar temporal.
 *   Swipe abajo: marcar/desmarcar favorita. Long-press: abrir Aural.
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
    onOpenApp: () -> Unit,
    onToggleFavorite: () -> Unit,
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
                    // Long-press: abre la app de Aural.
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
                        onOpenApp()
                    }
                )
            }
            .pointerInput(Unit) {
                // Swipes: ← siguiente, → anterior, ↑ ocultar, ↓ favorita.
                var totalX = 0f
                var totalY = 0f
                val swipeDistPx = with(density) { 60.dp.toPx() }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        val ax = kotlin.math.abs(totalX)
                        val ay = kotlin.math.abs(totalY)
                        if (ax > swipeDistPx && ax > 2f * ay) {
                            if (totalX > 0f) onPrevious() else onNext()
                        } else if (ay > swipeDistPx && ay > 2f * ax) {
                            if (totalY < 0f) onHide() else onToggleFavorite()
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
            // Los toques en vacío no hacen nada; se colapsa con la X
            // o con swipe hacia arriba dentro de la vista.
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
                        val ay = kotlin.math.abs(totalY)
                        if (totalY < -swipeDistPx &&
                            ay > 2f * kotlin.math.abs(totalX)
                        ) {
                            onCollapse()
                        }
                    },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                    }
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Compactar isla",
                    tint = islandOnBg.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
        }
        Spacer(Modifier.height(12.dp))
        // Acciones rápidas.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IslandActionButton(
                icon = Icons.Default.Search,
                label = "YouTube",
                onClick = { onQuickAction(QuickAction.YOUTUBE_SEARCH) },
                onBg = islandOnBg
            )
            IslandActionButton(
                icon = Icons.Default.Mic,
                label = "Reconocer",
                onClick = { onQuickAction(QuickAction.RECOGNIZE) },
                onBg = islandOnBg
            )
        }
        Spacer(Modifier.height(8.dp))
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
    }
}

@Composable
private fun IslandActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onBg: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(onBg.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = onBg,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = onBg.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

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
