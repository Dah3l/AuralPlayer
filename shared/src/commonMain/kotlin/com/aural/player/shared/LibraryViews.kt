// Vistas de la biblioteca compartidas: fila de canción, tarjeta de
// cuadrícula, vista de detalle (álbum/artista/playlist), sección
// "Para ti" y helpers (formato de tiempo, carpeta, playlist del día).
// Mismo aspecto y gestos que la app Android: clic, pulsación larga
// para multiselección y clic derecho para el menú en escritorio.
package com.aural.player.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Milisegundos → "m:ss" (o "h:mm:ss" si pasa de una hora). */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** ¿La canción vive dentro de esta carpeta? (tolerante a / y \, sin importar mayúsculas). */
fun isInFolder(song: Song, folderPath: String): Boolean {
    val folder = folderPath.replace('\\', '/').trimEnd('/')
    val path = song.filePath.replace('\\', '/')
    return path.startsWith("$folder/", ignoreCase = true)
}

/**
 * Playlist del día: 30 canciones al azar, sembradas por la fecha, así
 * que todo el día sale la misma lista (igual que en Android).
 */
fun dailyPlaylist(songs: List<Song>): List<Song> {
    if (songs.isEmpty()) return emptyList()
    val today = SystemDates.today()
    val seed = (today.year * 10000 + today.month * 100 + today.day).toLong()
    return songs.shuffled(Random(seed)).take(30)
}

/**
 * Fila de canción como la de Android: portada 48dp, título + artista,
 * duración y ⋯. Clic = reproducir (o marcar en multiselección),
 * pulsación larga = entrar a multiselección, clic derecho = menú.
 */
@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    loadCoverBytes: (Song) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            // El clic derecho va ANTES del combinedClickable para que el
            // menú abra sin pelear con el clic normal.
            .pointerInput(song.filePath) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed
                        ) {
                            onMenu()
                        }
                    }
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(40.dp)
            )
        }
        CoverArt(
            bitmap = rememberCoverImage(song, loadCoverBytes),
            modifier = Modifier.size(48.dp),
            cornerRadius = 8.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPlaying) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTime(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = AuralIcons.MoreVert,
                contentDescription = "Opciones de ${song.title}",
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

/** Lista de canciones con estado de scroll propio (la barra A-Z salta en él). */
@Composable
fun SongList(
    songs: List<Song>,
    listState: LazyListState,
    playingSongId: String?,
    selectionMode: Boolean,
    selectedSongs: Set<String>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    loadCoverBytes: (Song) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(songs, key = { it.filePath }) { song ->
            SongRow(
                song = song,
                isPlaying = song.filePath == playingSongId,
                selectionMode = selectionMode,
                selected = song.filePath in selectedSongs,
                onClick = { onSongClick(song) },
                onLongPress = { onSongLongPress(song) },
                onMenu = { onSongMenu(song) },
                loadCoverBytes = loadCoverBytes
            )
        }
    }
}

/** Tarjeta cuadrada de álbum/artista: portada + título + subtítulo. */
@Composable
fun MediaGridCard(
    title: String,
    subtitle: String,
    coverSong: Song?,
    loadCoverBytes: (Song) -> ByteArray?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        CoverArt(
            bitmap = rememberCoverImage(coverSong, loadCoverBytes),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            cornerRadius = 12.dp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Vista de detalle (álbum, artista, playlist, favoritas…): portada
 * grande con degradados, título, botones Reproducir/Aleatorio y la
 * lista de canciones debajo.
 */
@Composable
fun DrillView(
    title: String,
    subtitle: String,
    songs: List<Song>,
    coverSong: Song?,
    loadCoverBytes: (Song) -> ByteArray?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    playingSongId: String?,
    selectionMode: Boolean,
    selectedSongs: Set<String>,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            // ---- Cabecera: portada + título sobre degradado ----
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                CoverArt(
                    bitmap = rememberCoverImage(coverSong, loadCoverBytes),
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    showBorder = false
                )
                // Degradado superior para que la flecha se lea
                Box(
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(
                        imageVector = AuralIcons.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                // Degradado inferior + título
                Box(
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // ---- Botones: reproducir, aleatorio, extras ----
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayAll,
                    modifier = Modifier.size(52.dp)
                        .background(scheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = AuralIcons.Play,
                        contentDescription = "Reproducir todo",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = onShuffle,
                    modifier = Modifier.size(52.dp)
                        .background(scheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = AuralIcons.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(52.dp)
                            .background(scheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = AuralIcons.Plus,
                            contentDescription = "Agregar canciones",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                extraActions()
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyTabMessage(
                    message = "No hay canciones aquí todavía.",
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        } else {
            items(songs, key = { it.filePath }) { song ->
                SongRow(
                    song = song,
                    isPlaying = song.filePath == playingSongId,
                    selectionMode = selectionMode,
                    selected = song.filePath in selectedSongs,
                    onClick = { onSongClick(song) },
                    onLongPress = { onSongLongPress(song) },
                    onMenu = { onSongMenu(song) },
                    loadCoverBytes = loadCoverBytes
                )
            }
        }
    }
}

/** Una tarjeta de la sección "Para ti". */
data class MostPlayedCard(
    val title: String,
    val subtitle: String,
    val coverSong: Song?,
    val onOpen: () -> Unit,
    val onPlay: () -> Unit
)

/**
 * Sección "Para ti": dos tarjetas cuadradas con la portada, el título
 * sobre degradado negro y el botón ▶. Igual que en Android.
 */
@Composable
fun MostPlayedSection(
    cards: List<MostPlayedCard>,
    loadCoverBytes: (Song) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Para ti",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            cards.take(2).forEach { card ->
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { card.onOpen() }
                ) {
                    CoverArt(
                        bitmap = rememberCoverImage(card.coverSong, loadCoverBytes),
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 12.dp,
                        showBorder = false
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(12.dp)
                            .padding(end = 44.dp)
                    ) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = card.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { card.onPlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AuralIcons.Play,
                            contentDescription = "Reproducir ${card.title}",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Mensaje centrado para una pestaña o lista vacía. */
@Composable
fun EmptyTabMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
