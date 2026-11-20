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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
    modifier: Modifier = Modifier,
    /** Encabezado que se desplaza con la lista (en PC: la sección "Para ti"). */
    header: (@Composable () -> Unit)? = null
) {
    // La barra de desplazamiento de escritorio va a la derecha; la
    // barra A-Z (que vive fuera) deja 14 dp libres para no taparla.
    // El contenido deja 24 dp libres a la derecha para que la barra
    // respire y no tape los botones de acción de las filas.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(end = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (header != null) {
                item { header() }
            }
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
        LazyListScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
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

/** Periodo del filtro de Agregadas recientemente (igual que en Android, v1.0.9). */
enum class RecentPeriod(val label: String, val days: Long?) {
    WEEK("Esta semana", 7),
    MONTH("Este mes", 30),
    YEAR("Este año", 365),
    ALL("Todas", null);

    /** Milisegundos epoch a partir de los cuales cuenta una canción (null = sin límite). */
    fun cutoffMs(): Long? = days?.let {
        SystemDates.nowEpochMillis() - it * 86_400_000L
    }
}

/** Selector de periodo (dropdown) para Agregadas recientemente. */
@Composable
private fun PeriodFilterDropdown(
    period: RecentPeriod,
    onChange: (RecentPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            shape = CircleShape,
            modifier = Modifier.background(scheme.surfaceVariant, CircleShape)
        ) {
            Text(period.label, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Icon(
                imageVector = AuralIcons.ChevronDown,
                contentDescription = "Periodo",
                tint = scheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecentPeriod.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label, color = scheme.onSurface) },
                    onClick = { onChange(p); expanded = false }
                )
            }
        }
    }
}

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
    recentPeriod: RecentPeriod? = null,
    onRecentPeriodChange: (RecentPeriod) -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // Paleta de la portada de la cabecera para el fondo mesh.
    val coverBitmap = rememberCoverImage(coverSong, loadCoverBytes)
    val palette = remember(coverBitmap) { extractPalette(coverBitmap, scheme.primary) }
    // Colapso del encabezado: al subir, la barra superior fija muestra
    // retroceder + portada + info de la sección (igual que en el móvil).
    // La altura del encabezado se lee del primer ítem visible.
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else {
                val headerPx = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == 0 }?.size ?: 1
                (listState.firstVisibleItemScrollOffset / headerPx.toFloat())
                    .coerceIn(0f, 1f)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(end = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // La fila de retroceso va plana, sin mesh.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = AuralIcons.ArrowBack,
                        contentDescription = "Volver",
                        tint = scheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            // ---- Mesh SOLO en la zona de portada + título: la lista,
            // la fila de retroceso y los botones quedan planos como
            // antes. El clipToBounds recorta las manchas a esta zona
            // (sin él, los círculos se veían detrás de la lista). ----
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                NowPlayingAmbientBackground(
                    palette = palette,
                    modifier = Modifier.matchParentSize()
                )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    bitmap = coverBitmap,
                    modifier = Modifier.size(200.dp),
                    cornerRadius = 16.dp,
                    showBorder = false
                )
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                if (recentPeriod != null) {
                    PeriodFilterDropdown(
                        period = recentPeriod,
                        onChange = onRecentPeriodChange
                    )
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
        // Barra superior fija: aparece con fundido cuando el encabezado
        // se pierde al scrollear (igual que en el móvil).
        if (collapseFraction > 0.01f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(scheme.surface.copy(alpha = collapseFraction))
                    .alpha(collapseFraction)
                    .padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = AuralIcons.ArrowBack,
                        contentDescription = "Volver",
                        tint = scheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
                CoverArt(
                    bitmap = rememberCoverImage(coverSong, loadCoverBytes),
                    modifier = Modifier.size(40.dp),
                    cornerRadius = 8.dp,
                    showBorder = false
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
        }
        LazyListScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
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
 * Sección "Para ti": tarjetas pequeñas, tipo Spotify — caben las
 * que entren en una fila; si hay menos, solo las disponibles.
 */
@Composable
fun MostPlayedSection(
    cards: List<MostPlayedCard>,
    loadCoverBytes: (Song) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val cardSize = 148.dp
        val spacing = 12.dp
        // Cuántas caben en una fila (al menos 1).
        val count = ((maxWidth + spacing) / (cardSize + spacing)).toInt().coerceAtLeast(1)
        val shown = cards.take(count)
        Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Para ti",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
        ) {
            shown.forEach { card ->
                Box(
                    modifier = Modifier.size(cardSize)
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
                            .padding(8.dp)
                            .padding(end = 40.dp)
                    ) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleSmall,
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
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { card.onPlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AuralIcons.Play,
                            contentDescription = "Reproducir ${card.title}",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
