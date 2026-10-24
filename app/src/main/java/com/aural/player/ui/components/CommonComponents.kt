// Componentes reutilizables de la interfaz de Aural
package com.aural.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border

/** Formatea milisegundos como "m:ss" o "h:mm:ss". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/**
 * Miniatura de carátula con degradado sutil; muestra un icono de nota
 * musical cuando no hay imagen disponible.
 */
@Composable
fun CoverArt(
    uri: String?,
    size: Dp,
    cornerRadius: Dp = size / 6,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Icono siempre de fondo: si la imagen falla, no queda el cuadro vacío
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(size / 2.5f)
        )
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = android.net.Uri.parse(uri),
                contentDescription = "Carátula del álbum",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Fila de canción para listas de la biblioteca. */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    coverUri: String?,
    durationMs: Long,
    isPlaying: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "song_press"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(uri = coverUri, size = 48.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            trailing()
        }
    }
}

/** Estado vacío minimalista con mensaje centrado. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Cuadrícula de tarjetas de álbum/artista con portada cuadrada. */
@Composable
fun MediaGridCard(
    coverUri: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "card_press"
    )
    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        // Portada cuadrada que ocupa todo el ancho de la celda
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            // Icono siempre de fondo: si la imagen falla, no queda vacío
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp)
            )
            if (!coverUri.isNullOrBlank()) {
                AsyncImage(
                    model = android.net.Uri.parse(coverUri),
                    contentDescription = "Portada",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Encabezado de sección al estilo de las listas de Aural web. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Barra lateral A-Z para salto rápido alfabético (ocupa 70% de la altura).
 * Muestra un indicador grande con la letra actual mientras se arrastra.
 * [keys] son las claves de ordenamiento (una por elemento de la lista).
 * [onJumpToIndex] recibe el índice del primer elemento con la letra elegida.
 */
@Composable
fun AlphabetFastScroller(
    keys: List<String>,
    onJumpToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val letterMap = remember(keys) {
        val map = mutableMapOf<Char, Int>()
        keys.forEachIndexed { index, key ->
            val first = key.trim().firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
            val letter = if (first in 'A'..'Z') first else '#'
            if (letter !in map) map[letter] = index
        }
        map
    }
    if (letterMap.isEmpty()) return

    var barHeightPx by remember { mutableStateOf(0) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    // Símbolos primero (#), luego A-Z — 27 elementos en total
    val letters = listOf('#') + ('A'..'Z').toList()

    fun letterAtY(y: Float): Char? {
        if (barHeightPx <= 0) return null
        val index = ((y / barHeightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    fun jumpTo(letter: Char) {
        val idx = letterMap[letter] ?: return
        activeLetter = letter
        onJumpToIndex(idx)
    }

    Box(modifier = modifier) {
        // ---- Barra de letras (derecha, 70% altura) ----
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.78f)
                .onGloballyPositioned { barHeightPx = it.size.height }
                .pointerInput(letterMap) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            letterAtY(offset.y)?.let { jumpTo(it) }
                        },
                        onDragEnd = {
                            isDragging = false
                            activeLetter = null
                        },
                        onDragCancel = {
                            isDragging = false
                            activeLetter = null
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            letterAtY(change.position.y)?.let { jumpTo(it) }
                        }
                    )
                }
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEach { letter ->
                val enabled = letter in letterMap
                val isActive = letter == activeLetter
                Text(
                    text = if (letter == '#') "•" else letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    },
                    modifier = Modifier
                        .clickable(enabled = enabled) { jumpTo(letter) }
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // ---- Indicador grande de la letra actual ----
        if (isDragging && activeLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeLetter == '#') "•" else activeLetter.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Pantalla de bienvenida: logo de la app con animación de entrada.
 */
@Composable
fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        visible = true
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "splash_scale"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "splash_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Bloquea toques durante el splash para que no lleguen a
            // la biblioteca detrás
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.aural.player.R.drawable.ic_launcher_foreground
                ),
                contentDescription = "Aural",
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Aural",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "El sonido, sin ruido",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
        }
    }
}

/**
 * Indicador de carga: tres puntos que rebotan (no un spinner).
 */
@Composable
fun AuralLoadingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(400, delayMillis = i * 150),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { translationY = offsetY }
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

