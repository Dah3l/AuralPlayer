@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
// Pantallas de detalle: álbum, artista, playlist y favoritas
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Song
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.components.DragHandle
import com.aural.player.ui.components.DragAutoScroll
import com.aural.player.ui.components.draggedItemVisual
import com.aural.player.ui.components.rememberDragReorderState
import com.aural.player.ui.components.formatDuration
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * Cabecera común de detalle con portada grande, título, subtítulo
 * y botones de reproducir/aleatorio.
 */
@Composable
private fun DetailHeader(
    coverUri: String?,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CoverArt(uri = coverUri, size = 190.dp, cornerRadius = 16.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Botón principal redondo de acento: reproducir todo
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Rounded.PlayArrow, "Reproducir todo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp))
            }
            // Botón secundario redondo: reproducción aleatoria
            IconButton(
                onClick = onShuffle,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Rounded.Shuffle, "Aleatorio",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Pantalla genérica de detalle que muestra una lista de canciones con
 * cabecera. Se reutiliza para Álbum, Artista, Playlist y Favoritas.
 */
@Composable
fun DetailScreen(
    viewModel: AuralViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    coverUri: String?,
    onBack: () -> Unit,
    onSongMenu: (Song) -> Unit,
    removableFromPlaylistId: String? = null
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // ---- Barra superior con volver ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Volver")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (songs.isEmpty()) {
            var showPicker by remember { mutableStateOf(false) }
            // Placeholder premium para listas vacías
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Todo tranquilo por aquí...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta lista está esperando su primera canción. ¿Le damos vida?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (removableFromPlaylistId != null) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar canciones", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (showPicker && removableFromPlaylistId != null) {
                SongPickerDialog(
                    songs = viewModel.library.collectAsStateWithLifecycle().value.songs,
                    onDismiss = { showPicker = false },
                    onConfirm = { selected ->
                        selected.forEach { song ->
                            viewModel.addToPlaylist(removableFromPlaylistId, song.id)
                        }
                        showPicker = false
                    }
                )
            }
            return@Column
        }

        DetailHeader(
            coverUri = coverUri,
            title = title,
            subtitle = "${songs.size} canciones · ${formatDuration(songs.sumOf { it.durationMs })}",
            onPlay = { viewModel.playSongs(songs, 0) },
            onShuffle = { viewModel.playSongs(songs.shuffled(), 0, shuffle = true) }
        )

        Spacer(Modifier.height(8.dp))

        // ---- Lista de canciones (reordenable en playlists) ----
        val listState = rememberLazyListState()
        val dragState = rememberDragReorderState()
        // En playlists el reorden es LOCAL e instantáneo, como la cola (que vive
        // en memoria): el orden definitivo se guarda una sola vez, al soltar.
        val reorderable = removableFromPlaylistId != null
        var displaySongs by remember(removableFromPlaylistId) { mutableStateOf(songs) }
        // Seguir a la fuente cuando cambia por fuera (altas/bajas), nunca a mitad de arrastre
        LaunchedEffect(songs) {
            if (!dragState.isDragging && displaySongs.map { it.id } != songs.map { it.id }) {
                displaySongs = songs
            }
        }
        // Al soltar, persistir el orden local si difiere del guardado
        LaunchedEffect(dragState) {
            snapshotFlow { dragState.isDragging }.collect { dragging ->
                val pid = removableFromPlaylistId
                if (!dragging && pid != null &&
                    displaySongs.map { it.id } != songs.map { it.id }
                ) {
                    viewModel.setPlaylistOrder(pid, displaySongs.map { it.id })
                }
            }
        }
        val moveLocal: (Int, Int) -> Unit = { from, to ->
            val current = displaySongs
            if (from in current.indices && to in current.indices && from != to) {
                val reordered = current.toMutableList()
                reordered.add(to, reordered.removeAt(from))
                displaySongs = reordered
            }
        }
        val listSongs = if (reorderable) displaySongs else songs
        // Auto-scroll al arrastrar hacia los bordes (igual que en la cola)
        DragAutoScroll(
            listState = listState,
            dragState = dragState,
            itemCount = listSongs.size,
            onMove = moveLocal,
            enabled = reorderable
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            itemsIndexed(listSongs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    title = customMetadata[song.id]?.first?.ifBlank { song.title } ?: song.title,
                    subtitle = customMetadata[song.id]?.second?.ifBlank { song.artist } ?: song.artist,
                    coverUri = customCovers[song.id] ?: song.albumArtUri,
                    durationMs = song.durationMs,
                    isPlaying = song.id == playerState.currentSong?.id,
                    onClick = { viewModel.playSongs(listSongs, index) },
                    modifier = if (reorderable) {
                        (if (dragState.draggedIndex != index) Modifier.animateItem() else Modifier)
                            .onSizeChanged {
                                if (it.height > 0) {
                                    dragState.draggedItemSizePx = it.height.toFloat()
                                }
                            }
                            .draggedItemVisual(dragState, index)
                    } else Modifier,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tres puntos: el mismo menú de la biblioteca; abierto
                            // desde una playlist trae "Eliminar de la lista" en
                            // vez de "Añadir a playlist"
                            TextButton(onClick = { onSongMenu(song) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                            // Agarradera para reordenar (solo en playlists)
                            if (reorderable) {
                                DragHandle(
                                    index = index,
                                    dragState = dragState,
                                    itemCount = listSongs.size,
                                    onMove = moveLocal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
private fun SongPickerDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar canciones") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (song.id in selected) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = song.id in selected,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(songs.filter { it.id in selected }) },
                enabled = selected.isNotEmpty()
            ) { Text("Agregar (${selected.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}