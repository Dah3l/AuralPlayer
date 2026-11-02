// Isla dinámica (v1.0.7): píldora flotante sobre otras apps.
// Posición FIJA bajo el orificio de la cámara (sin arrastre).
// Transición colapsada <-> expandida con morph animado (spring).
package com.aural.player.ui.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import coil.compose.AsyncImage
import kotlin.math.sin

private val IslandBg = Color(0xFF101014)
private val IslandAccent = Color(0xFF2ED3B7)
private val IslandWave1 = Color(0xFF2ED3B7)
private val IslandWave2 = Color(0xFF8B5CF6)
private val IslandWave3 = Color(0xFFFF7A1A)
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
    var expanded by remember { mutableStateOf(false) }
    val song = playerState.currentSong ?: return

    // Al cambiar de canción, la isla vuelve a colapsada.
    androidx.compose.runtime.LaunchedEffect(song.id) {
        expanded = false
    }

    Box(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .shadow(14.dp, IslandShape)
            .clip(IslandShape)
            .background(IslandBg.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), IslandShape)
            // El morph: los bordes se estiran/contraen con rebote.
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f)
            )
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(200)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150)) +
                                scaleOut(targetScale = 0.9f, animationSpec = tween(150))
                    )
            },
            label = "island_morph"
        ) { targetExpanded ->
            if (targetExpanded) {
                IslandExpandedContent(
                    title = song.title,
                    artist = song.artist,
                    coverUri = song.albumArtUri,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    onTogglePlay = { app.playback.togglePlayPause() },
                    onNext = { app.playback.seekToNext() },
                    onPrevious = { app.playback.seekToPrevious() },
                    onSeek = { app.playback.seekTo(it) },
                    onOpenApp = {
                        val ctx = app.applicationContext
                        val intent = android.content.Intent(
                            ctx, com.aural.player.MainActivity::class.java
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    },
                    onQuickAction = { action -> handleQuickAction(app, action) },
                    onClose = onClose,
                    onCollapse = { expanded = false }
                )
            } else {
                IslandCollapsedContent(
                    title = song.title,
                    isPlaying = playerState.isPlaying,
                    onTap = { expanded = true },
                    onQuickAction = { action -> handleQuickAction(app, action) }
                )
            }
        }
    }
}

/**
 * Píldora colapsada: solo el título (marquesina si no cabe) + onda pequeña.
 * Posición fija — sin arrastre.
 * - Tap: expande.
 * - Fling horizontal rápido: acciones rápidas (→ buscar en YouTube,
 *   ← reconocer canción).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandCollapsedContent(
    title: String,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var actionHint by remember { mutableStateOf<QuickAction?>(null) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                // Solo fling: la posición es fija, ya no se arrastra.
                var totalX = 0f
                var totalY = 0f
                var startTime = 0L
                val flingDistPx = with(density) { 90.dp.toPx() }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        startTime = System.currentTimeMillis()
                    },
                    onDragEnd = {
                        val dt = System.currentTimeMillis() - startTime
                        if (dt in 1..600 &&
                            kotlin.math.abs(totalX) > flingDistPx &&
                            kotlin.math.abs(totalX) > 2f * kotlin.math.abs(totalY)
                        ) {
                            val action = if (totalX > 0f) QuickAction.YOUTUBE_SEARCH
                            else QuickAction.RECOGNIZE
                            actionHint = action
                            onQuickAction(action)
                        }
                    },
                    onDragCancel = { },
                    onDrag = { change, _ -> change.consume() }
                )
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.widthIn(max = 260.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Onda pequeña, del tamaño de la píldora.
            IslandWave(
                isPlaying = isPlaying,
                modifier = Modifier.size(width = 44.dp, height = 22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
        // Hint breve de la acción rápida ejecutada.
        actionHint?.let { hint ->
            Text(
                text = when (hint) {
                    QuickAction.YOUTUBE_SEARCH -> "Buscando en YouTube…"
                    QuickAction.RECOGNIZE -> "Escuchando…"
                },
                color = IslandAccent,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** Acciones rápidas del swipe en la píldora. */
enum class QuickAction { YOUTUBE_SEARCH, RECOGNIZE }

/** Onda animada de colores (indicador de reproducción). */
@Composable
fun IslandWave(isPlaying: Boolean, modifier: Modifier = Modifier) {
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
            IslandWave1 to 0f,
            IslandWave2 to 2.1f,
            IslandWave3 to 4.2f
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
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPos by remember(positionMs) { mutableStateOf(positionMs.toFloat()) }
    var seeking by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCollapse
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar isla",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = coverUri?.let { android.net.Uri.parse(it) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2129))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IslandWave(
                isPlaying = isPlaying,
                modifier = Modifier.size(width = 52.dp, height = 26.dp)
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
                thumbColor = IslandAccent,
                activeTrackColor = IslandAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(if (seeking) sliderPos.toLong() else positionMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Text(
                text = formatMs(durationMs),
                color = Color.White.copy(alpha = 0.6f),
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
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(60.dp)
                    .background(IslandAccent, CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.Black,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tint = Color.White,
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
                onClick = { onQuickAction(QuickAction.YOUTUBE_SEARCH) }
            )
            IslandActionButton(
                icon = Icons.Default.Mic,
                label = "Reconocer",
                onClick = { onQuickAction(QuickAction.RECOGNIZE) }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Abrir Aural",
            color = IslandAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenApp
                )
                .padding(8.dp)
        )
    }
}

@Composable
private fun IslandActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
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
