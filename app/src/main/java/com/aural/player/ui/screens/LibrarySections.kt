// Secciones de la biblioteca separadas de LibraryScreen (mismo paquete).
package com.aural.player.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Playlist
import com.aural.player.data.Song
import com.aural.player.data.TopArtist
import com.aural.player.data.WeekStats
import com.aural.player.data.birthdayDisplayName
import com.aural.player.data.greetingDisplayName
import com.aural.player.data.isBirthdayToday
import com.aural.player.data.todayDateKey
import com.aural.player.ui.components.BirthdayModal
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.FeatureDot
import com.aural.player.ui.components.FeatureKeys
import com.aural.player.ui.components.HalloweenHomeAmbient
import com.aural.player.ui.components.ChristmasHomeAmbient
import com.aural.player.ui.components.MediaGridCard
import com.aural.player.ui.components.NewsModal
import com.aural.player.ui.components.ProfileFormDialog
import com.aural.player.ui.theme.HalloweenFontFamily
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.components.AlphabetFastScroller
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistAdd
import com.aural.player.youtube.YouTubeManager
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import com.aural.player.data.tabLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.aural.player.data.SettingsStore
import com.aural.player.data.UpdateCheckResult
import com.aural.player.data.UpdateInfo
import com.aural.player.data.checkForUpdate
import com.aural.player.data.installedVersionName
import com.aural.player.ui.components.UpdateAvailableDialog
import androidx.compose.material.icons.filled.Download
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.RadioButton


// --------------------------- Búsqueda por pills (1.3.0) ---------------------------

/**
 * Selector de fuente de la búsqueda: Locales (por defecto) u Online.
 * Las pills superiores mandan el tipo (Canciones/Artistas/Álbumes/
 * Playlists); este selector solo decide de dónde salen los resultados.
 */
@Composable
internal fun SearchSourceSelector(
    online: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to "Locales", true to "Online").forEach { (value, label) ->
            val selected = online == value
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onChange(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Aviso de estado online (sin conexión / error) sobre la pestaña activa. */
@Composable
internal fun OnlineSearchNotice(hasInternet: Boolean, onlineError: String?) {
    val text = when {
        !hasInternet -> "Sin conexión: resultados en línea no disponibles"
        onlineError != null -> "No se pudieron cargar resultados en línea"
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Esqueletos de carga con la misma fila de las canciones. */
@Composable
internal fun OnlineSkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(5) { SearchSkeletonRow() }
    }
}

/**
 * Artistas online con el mismo diseño de la pestaña local:
 * cuadrícula de tarjetas con portada, nombre y subtítulo.
 */
@Composable
internal fun OnlineArtistsTab(
    artists: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    if (artists.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(artists, key = { it.url }) { artist ->
                MediaGridCard(
                    coverUri = artist.thumbnailUrl,
                    title = artist.title,
                    subtitle = artist.subtitle.ifBlank { "Artista" },
                    onClick = { onOpen(artist) }
                )
            }
        }
        val artistNames = remember(artists) { artists.map { it.title } }
        AlphabetFastScroller(
            keys = artistNames,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Álbumes online con el mismo diseño de la pestaña local:
 * cuadrícula de tarjetas con portada, título y artista.
 */
@Composable
internal fun OnlineAlbumsTab(
    albums: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    if (albums.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(albums, key = { it.url }) { album ->
                MediaGridCard(
                    coverUri = album.thumbnailUrl,
                    title = album.title,
                    subtitle = album.subtitle,
                    onClick = { onOpen(album) }
                )
            }
        }
        val albumTitles = remember(albums) { albums.map { it.title } }
        AlphabetFastScroller(
            keys = albumTitles,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Playlists online con el mismo molde de fila de Playlists:
 * miniatura cuadrada, nombre, subtítulo y botón de reproducir.
 */
@Composable
internal fun OnlinePlaylistsTab(
    playlists: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    onPlay: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (playlists.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(playlists, key = { it.url }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = playlist.thumbnailUrl, size = 48.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playlist.subtitle.isNotBlank()) {
                        Text(
                            playlist.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { onPlay(playlist) }) {
                    Icon(
                        Icons.Rounded.PlayArrow, "Reproducir",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Aviso de consumo de datos del primer streaming (una sola vez). */
@Composable
internal fun StreamingNoticeDialog(viewModel: AuralViewModel) {
    val streamingNoticePending by viewModel.streamingNoticePending.collectAsStateWithLifecycle()
    if (streamingNoticePending) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelStreamingNotice() },
            title = { Text("Consumo de datos") },
            text = {
                Text("Escuchar en línea usa datos móviles. Puedes cambiar la calidad o limitarlo a Wi-Fi en Ajustes → Sonido.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmStreamingNotice() }) {
                    Text("Escuchar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStreamingNotice() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Menú ⋮ de una canción online: el mismo en búsqueda y en el detalle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineSongMenuSheet(
    result: com.aural.player.youtube.YTResult,
    playlists: List<Playlist>,
    viewModel: AuralViewModel,
    youTubeManager: YouTubeManager?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 4.dp),
            maxLines = 1
        )
        TextButton(
            onClick = {
                onDismiss()
                scope.launch {
                    viewModel.enqueueOnlineSong(
                        viewModel.registerOnlineSong(result), next = true
                    )
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Reproducir siguiente") }
        TextButton(
            onClick = {
                onDismiss()
                scope.launch {
                    viewModel.enqueueOnlineSong(
                        viewModel.registerOnlineSong(result), next = false
                    )
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Añadir a la cola") }
        TextButton(
            onClick = {
                onDismiss()
                youTubeManager?.download(result) { fileName, mediaId, tagsEmbedded ->
                    if (fileName != null) {
                        viewModel.addDownloadToPlaylist(
                            fileName, mediaId, result, tagsEmbedded
                        )
                        if (!tagsEmbedded) {
                            viewModel.applyYouTubeMetadataFallback(
                                mediaId, result.title, result.artist,
                                result.thumbnailUrl
                            )
                        }
                        android.widget.Toast.makeText(
                            appContext,
                            "Canción descargada",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Descargar") }
        Text(
            "Guardar en playlist",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp)
        )
        if (playlists.isEmpty()) {
            Text(
                "No tienes playlists todavía",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            playlists.forEach { pl ->
                TextButton(
                    onClick = {
                        onDismiss()
                        scope.launch {
                            val song = viewModel.registerOnlineSong(result)
                            viewModel.addToPlaylist(pl.id, song.id)
                        }
                    },
                    modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
                ) { Text(pl.name) }
            }
        }
        Box(Modifier.padding(bottom = 24.dp)) {}
    }
}


@Composable
private fun SearchSkeletonRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun OnlineCollectionRow(
    collection: com.aural.player.youtube.OnlineCollectionResult,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(uri = collection.thumbnailUrl, size = 48.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (collection.subtitle.isNotBlank()) {
                Text(
                    text = collection.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "Reproducir",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

// --------------------------- Pestaña: Canciones ---------------------------

@Composable
internal fun SongsTab(
    songs: List<Song>,
    favorites: Set<Long>,
    currentId: Long?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onFavorite: (Long) -> Unit,
    onMenu: (Song) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    customCovers: Map<Long, String> = emptyMap(),
    customMetadata: Map<Long, Triple<String, String, String>> = emptyMap(),
    // v1.0.7: selección múltiple.
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Song) -> Unit = {},
    onEnterSelection: (Song) -> Unit = {}
) {
    if (songs.isEmpty()) {
        EmptyState("Sin resultados para tu búsqueda")
        return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp, end = 32.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                val meta = customMetadata[song.id]
                SongRow(
                    title = meta?.first?.ifBlank { song.title } ?: song.title,
                    subtitle = listOf(
                        meta?.second?.ifBlank { song.artist } ?: song.artist,
                        meta?.third?.ifBlank { song.album } ?: song.album
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    coverUri = customCovers[song.id] ?: song.albumArtUri,
                    durationMs = song.durationMs,
                    isPlaying = song.id == currentId,
                    onClick = {
                        if (selectionMode) onToggleSelect(song) else onPlay(song)
                    },
                    onLongClick = { onEnterSelection(song) },
                    selected = song.id in selectedIds,
                    showCheckbox = selectionMode,
                    onlineBadge = song.isOnline,
                    trailing = {
                        if (!selectionMode) {
                            TextButton(onClick = { onMenu(song) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                )
            }
        }
        val songTitles = remember(songs) { songs.map { it.title } }
        AlphabetFastScroller(
            keys = songTitles,
            onJumpToIndex = { index -> scope.launch { listState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Artistas ---------------------------

@Composable
internal fun ArtistsTab(
    artists: List<com.aural.player.data.Artist>,
    onOpen: (String) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
,
    customCovers: Map<Long, String> = emptyMap()
) {
    if (artists.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                MediaGridCard(
                    coverUri = artist.representativeSong.albumArtUri,
                    title = artist.name,
                    subtitle = "${artist.songs.size} ${if (artist.songs.size == 1) "canción" else "canciones"}",
                    onClick = { onOpen(artist.name) }
                )
            }
        }
        val artistNames = remember(artists) { artists.map { it.name } }
        AlphabetFastScroller(
            keys = artistNames,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Álbumes ---------------------------

@Composable
internal fun AlbumsTab(
    albums: List<com.aural.player.data.Album>,
    onOpen: (String) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
,
    customCovers: Map<Long, String> = emptyMap()
) {
    if (albums.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                MediaGridCard(
                    coverUri = album.songs.firstOrNull()?.albumArtUri,
                    title = album.title,
                    subtitle = album.artist,
                    onClick = { onOpen(album.title) }
                )
            }
        }
        val albumTitles = remember(albums) { albums.map { it.title } }
        AlphabetFastScroller(
            keys = albumTitles,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Carpetas ---------------------------

/**
 * Carpetas en lista (no cuadrícula): tocar abre la lista de canciones
 * (como álbum/playlist), el ojo oculta la carpeta con confirmación.
 */
@Composable
internal fun FoldersTab(
    folders: List<com.aural.player.data.Folder>,
    onOpen: (com.aural.player.data.Folder) -> Unit,
    onHideFolder: (com.aural.player.data.Folder) -> Unit = {}
) {
    if (folders.isEmpty()) {
        EmptyState("Sin carpetas"); return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            FolderRow(
                folder = folder,
                onClick = { onOpen(folder) },
                onHide = { onHideFolder(folder) }
            )
        }
    }
}

/** Fila de carpeta: icono, nombre, conteo y botón para ocultarla. */
@Composable
private fun FolderRow(
    folder: com.aural.player.data.Folder,
    onClick: () -> Unit,
    onHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.songs.size} ${if (folder.songs.size == 1) "canción" else "canciones"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = "Ocultar carpeta",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --------------------------- Diálogo nueva playlist ---------------------------

@Composable
internal fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Crear playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Crear", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Diálogo para guardar la Playlist del día como permanente (nombre editable). */
@Composable
internal fun SaveDailyPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("Playlist del día") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Guardar como permanente") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Guardar", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// --------------------------- Pestaña: Playlists ---------------------------

@Composable
internal fun PlaylistsTab(
    playlists: List<Playlist>,
    songCount: (Playlist) -> Int,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onExport: (Playlist) -> Unit,
    onImport: () -> Unit,
    hasFavorites: Boolean,
    onOpenFavorites: () -> Unit,
    downloadsCount: Int,
    onOpenDownloads: () -> Unit,
    dailySongCount: Int,
    dailyCoverUri: String?,
    onOpenDaily: () -> Unit,
    onPlayDaily: () -> Unit,
    onSaveDailyPermanent: () -> Unit,
    recentSongCount: Int,
    recentCoverUri: String?,
    onOpenRecent: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    playlistCovers: Map<String, String>,
    firstSongCover: (Playlist) -> String?,
    onRemoveCover: (Playlist) -> Unit,
    showFixedEntries: Boolean = true
) {
    LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        item {
            Text(
                text = "Tuyas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        if (showFixedEntries) {
        // Entrada fija de Favoritos
        item {
            val favInteraction = remember { MutableInteractionSource() }
            val favPressed by favInteraction.collectIsPressedAsState()
            val favScale by animateFloatAsState(
                targetValue = if (favPressed) 0.98f else 1f,
                label = "fav_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(favScale)
                    .clickable(
                        interactionSource = favInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenFavorites() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = null, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Favoritas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hasFavorites) "Tu selección" else "Toca el ♡ en tus canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenFavorites) {
                    Icon(Icons.Rounded.Favorite, "Abrir favoritas",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Entrada fija de Descargas
        item {
            val dlInteraction = remember { MutableInteractionSource() }
            val dlPressed by dlInteraction.collectIsPressedAsState()
            val dlScale by animateFloatAsState(
                targetValue = if (dlPressed) 0.98f else 1f,
                label = "dl_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(dlScale)
                    .clickable(
                        interactionSource = dlInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenDownloads() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = null, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Descargas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (downloadsCount > 0) "$downloadsCount ${if (downloadsCount == 1) "canción" else "canciones"}"
                        else "Tus canciones de YouTube",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, "Abrir descargas",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // Entrada fija de Agregadas recientemente (v1.0.9): va ANTES de la
        // Playlist del día; su icono de la derecha es un reloj (lista
        // basada en tiempo) que abre la lista, como el corazón de
        // Favoritas y el icono de Descargas.
        item {
            val recentInteraction = remember { MutableInteractionSource() }
            val recentPressed by recentInteraction.collectIsPressedAsState()
            val recentScale by animateFloatAsState(
                targetValue = if (recentPressed) 0.98f else 1f,
                label = "recent_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(recentScale)
                    .clickable(
                        interactionSource = recentInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenRecent() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = recentCoverUri, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Agregadas recientemente", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (recentSongCount > 0)
                            "$recentSongCount ${if (recentSongCount == 1) "canción nueva" else "canciones nuevas"}"
                        else "Todavía no hay canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenRecent) {
                    Icon(Icons.Rounded.Schedule, "Abrir",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // Entrada fija de Playlist del día (UNA sola instancia; rota a diario)
        item {
            val dailyInteraction = remember { MutableInteractionSource() }
            val dailyPressed by dailyInteraction.collectIsPressedAsState()
            val dailyScale by animateFloatAsState(
                targetValue = if (dailyPressed) 0.98f else 1f,
                label = "daily_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(dailyScale)
                    .clickable(
                        interactionSource = dailyInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenDaily() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = dailyCoverUri, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Playlist del día", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (dailySongCount > 0)
                            "$dailySongCount ${if (dailySongCount == 1) "canción aleatoria" else "canciones aleatorias"} · se renueva cada día"
                        else "Se genera al abrir la app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayDaily) {
                    Icon(Icons.Rounded.PlayArrow, "Reproducir",
                        tint = MaterialTheme.colorScheme.primary)
                }
                var showDailyMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showDailyMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Opciones")
                    }
                    DropdownMenu(
                        expanded = showDailyMenu,
                        onDismissRequest = { showDailyMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Guardar como permanente") },
                            onClick = {
                                showDailyMenu = false
                                onSaveDailyPermanent()
                            }
                        )
                    }
                }
            }
        }
        // Botones crear / importar playlist
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = onCreate, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Crear playlist", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onImport) {
                    Text("Importar playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        }
        if (playlists.isEmpty()) {
            item { EmptyState("Todavía no has creado playlists") }
        } else {
            items(playlists.filter { it.name != "Descargas" }, key = { it.id }) { pl ->
                val rowInteraction = remember { MutableInteractionSource() }
                val rowPressed by rowInteraction.collectIsPressedAsState()
                val rowScale by animateFloatAsState(
                    targetValue = if (rowPressed) 0.98f else 1f,
                    label = "playlist_press"
                )
                // El conteo se calcula una sola vez por fila (no en cada Text).
                val count = songCount(pl)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(rowScale)
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) { onOpen(pl) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArt(uri = playlistCovers[pl.id] ?: firstSongCover(pl), size = 48.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "$count ${if (count == 1) "canción" else "canciones"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onPlayPlaylist(pl) }) {
                        Icon(Icons.Rounded.PlayArrow, "Reproducir",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar") },
                                onClick = {
                                    showMenu = false
                                    onRename(pl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    showMenu = false
                                    onDelete(pl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar lista") },
                                onClick = {
                                    showMenu = false
                                    onExport(pl)
                                }
                            )
                            if (playlistCovers.containsKey(pl.id)) {
                                DropdownMenuItem(
                                    text = { Text("Quitar foto") },
                                    onClick = {
                                        showMenu = false
                                        onRemoveCover(pl)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

