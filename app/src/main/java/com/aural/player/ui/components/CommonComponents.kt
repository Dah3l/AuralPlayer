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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned

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
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Icono siempre de fondo: si la imagen falla, no queda el cuadro vacío
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(size / 3f)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(uri = coverUri, size = 48.dp)
        Spacer(Modifier.width(12.dp))
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
 * Barra lateral A-Z para salto rápido alfabético.
 * [keys] son las claves de ordenamiento (una por elemento de la lista).
 * [onJumpToIndex] recibe el índice del primer elemento con la letra elegida.
 */
@Composable
fun AlphabetFastScroller(
    keys: List<String>,
    onJumpToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Mapa letra -> primer índice con esa inicial
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

    fun letterAtY(y: Float): Char? {
        if (barHeightPx <= 0) return null
        val index = ((y / barHeightPx) * 27).toInt().coerceIn(0, 26)
        val all = ('A'..'Z').toList() + '#'
        return all[index]
    }

    fun jumpTo(letter: Char) {
        val idx = letterMap[letter] ?: return
        activeLetter = letter
        onJumpToIndex(idx)
    }

    val letters = ('A'..'Z').toList() + '#'
    Column(
        modifier = modifier
            .onGloballyPositioned { barHeightPx = it.size.height }
            .pointerInput(letterMap) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        letterAtY(offset.y)?.let { jumpTo(it) }
                    },
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        letterAtY(change.position.y)?.let { jumpTo(it) }
                    }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}
