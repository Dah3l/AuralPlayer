// Pantalla "Now Playing": carátula grande, controles y cola
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aural.player.playback.PlayerUiState
import com.aural.player.playback.RepeatMode
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.formatDuration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.MusicNote

/**
 * Pantalla completa de reproducción con degradado sutil de fondo,
 * carátula grande, barra de seek, controles, favorito y cola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: com.aural.player.AuralViewModel,
    state: PlayerUiState,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lrcMapState by viewModel.lrcMap.collectAsStateWithLifecycle()
    val song = state.currentSong
    var showQueue by remember { mutableStateOf(false) }
    val queueListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Al abrir la cola, posicionar en la canción actual
    androidx.compose.runtime.LaunchedEffect(showQueue) {
        if (showQueue) {
            queueListState.scrollToItem(state.currentIndex.coerceAtLeast(0))
        }
    }

    // Fondo oscuro con un halo de acento muy suave en la parte superior
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            // Bloquea toques para que no lleguen a la lista detrás
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // ---- Barra superior: cerrar + indicador ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "REPRODUCIENDO AHORA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    NowPlayingMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        song = song,
                        viewModel = viewModel,
                        state = state,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onShowLyrics = { showLyrics = true }
                    )
                }
            }

            if (song == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "Nada en reproducción",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // ---- Carátula grande (responsiva) ----
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val artSize = (maxWidth * 0.88f).coerceAtMost(380.dp).coerceAtLeast(200.dp)
                CoverArt(
                    uri = song.albumArtUri,
                    size = artSize,
                    modifier = Modifier.shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(artSize / 6),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    cornerRadius = 16.dp
                )
            }

            // Empuja el bloque inferior (título/controles) hacia abajo
            Spacer(Modifier.weight(1f))

            // ---- Título + artista + estrella ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.artist.ifBlank { "Artista desconocido" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star
                        else Icons.Rounded.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---- Barra de progreso ----
            val duration = state.durationMs.coerceAtLeast(1L)
            Slider(
                value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            // ---- Controles ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repetir
                IconButton(onClick = onCycleRepeat) {
                    when (state.repeatMode) {
                        RepeatMode.ONE -> Icon(Icons.Rounded.RepeatOne, "Repetir una",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                        RepeatMode.ALL -> Icon(Icons.Rounded.Repeat, "Repetir todas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                        else -> Icon(Icons.Rounded.Repeat, "Sin repetir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp))
                    }
                }
                // Anterior
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                // Play/Pausa (botón principal grande)
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                // Siguiente
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                // Cola
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, "Cola",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp))
                }
            }

            Spacer(
                Modifier
                    .height(48.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }

    // ---- Cola como hoja inferior ----
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            // Altura fija: el modal no crece al hacer scroll
            Column(Modifier.fillMaxHeight(0.6f)) {
                Text(
                    "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
                LazyColumn(
                    state = queueListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                itemsIndexed(state.queue) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayQueueItem(index) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (index == state.currentIndex)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == state.currentIndex)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onRemoveFromQueue(index) }) {
                            Icon(Icons.Rounded.Close, "Quitar de la cola",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (state.queue.isEmpty()) {
                    item {
                        Text(
                            "La cola está vacía",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            } // fin LazyColumn
        } // fin Column altura fija
    }
    }

    // Overlay de letras sincronizadas
    if (showLyrics) {
        val lrcPath = song?.let { lrcMapState[it.id] }
        if (lrcPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LyricsScreen(
                    lrcPath = lrcPath,
                    currentPositionMs = { state.positionMs },
                    onBack = { showLyrics = false }
                )
            }
        } else {
            showLyrics = false
        }
    }
}

/**
 * Menú de opciones del Now Playing: agregar a playlist, temporizador,
 * velocidad, compartir y eliminar de la cola.
 */
@Composable
private fun NowPlayingMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: com.aural.player.data.Song?,
    viewModel: com.aural.player.AuralViewModel,
    state: com.aural.player.playback.PlayerUiState,
    onRemoveFromQueue: (Int) -> Unit,
    onShowLyrics: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()

    var showPlaylists by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showTimer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSpeed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLrcPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var lrcFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var lrcLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lrcMap by viewModel.lrcMap.collectAsStateWithLifecycle()
    val hasLrc = song?.let { lrcMap.containsKey(it.id) } ?: false
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        DropdownMenuItem(
            text = { Text("Agregar a la lista de reproducción") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
            onClick = { onDismiss(); showPlaylists = true }
        )
        DropdownMenuItem(
            text = { Text(if (sleepMinutes != null) "Temporizador: ${sleepMinutes} min" else "Temporizador de suspensión") },
            leadingIcon = { Icon(Icons.Rounded.Timer, null) },
            onClick = { onDismiss(); showTimer = true }
        )
        DropdownMenuItem(
            text = { Text("Velocidad de reproducción") },
            leadingIcon = { Icon(Icons.Rounded.Speed, null) },
            onClick = { onDismiss(); showSpeed = true }
        )
        DropdownMenuItem(
            text = { Text("Compartir") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = {
                onDismiss()
                song?.let {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(it.uri))
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, "Compartir canción")
                    )
                }
            }
        )
        DropdownMenuItem(
            text = { Text(if (hasLrc) "Ver letra" else "Agregar letra") },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
            onClick = {
                onDismiss()
                if (hasLrc) {
                    onShowLyrics()
                } else {
                    showLrcPicker = true
                    lrcLoading = true
                    scope.launch {
                        lrcFiles = viewModel.scanLrcFiles()
                        lrcLoading = false
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Eliminar de la cola") },
            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
            onClick = {
                onDismiss()
                song?.let { s ->
                    val idx = state.queue.indexOfFirst { it.id == s.id }
                    if (idx >= 0) onRemoveFromQueue(idx)
                }
            }
        )
    }

    // Diálogo: elegir playlist
    if (showPlaylists && song != null) {
        AlertDialog(
            onDismissRequest = { showPlaylists = false },
            title = { Text("Agregar a playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                viewModel.addToPlaylist(pl.id, song.id)
                                showPlaylists = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pl.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (playlists.isEmpty()) {
                        Text("No tienes playlists. Créalas en la pestaña Playlists.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylists = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo: temporizador
    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Temporizador de suspensión") },
            text = {
                Column {
                    listOf(15, 30, 60, 90).forEach { mins ->
                        TextButton(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins minutos", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (sleepMinutes != null) {
                        TextButton(
                            onClick = {
                                viewModel.cancelSleepTimer()
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar temporizador",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimer = false }) { Text("Cerrar") }
            }
        )
    }

    // Diálogo: velocidad
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Velocidad de reproducción") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(
                            onClick = {
                                viewModel.playback.setPlaybackSpeed(speed)
                                showSpeed = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = if (speed == 1f) "1x (Normal)" else "${speed}x"
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeed = false }) { Text("Cerrar") }
            }
        )
    }

    // Diálogo selector de .lrc
    if (showLrcPicker) {
        com.aural.player.ui.components.LrcPickerDialog(
            lrcFiles = lrcFiles,
            isLoading = lrcLoading,
            onDismiss = { showLrcPicker = false },
            onSelect = { lrcPath ->
                song?.let { viewModel.setLrcForSong(it.id, lrcPath) }
                showLrcPicker = false
            }
        )
    }
}

