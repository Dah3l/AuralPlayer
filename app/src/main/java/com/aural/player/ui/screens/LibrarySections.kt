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


// --------------------------- Búsqueda unificada (1.3.0) ---------------------------

private enum class SearchFilter(val label: String) {
    ALL("Todo"),
    LIBRARY("Mi biblioteca"),
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    PLAYLISTS("Playlists"),
    PODCASTS("Podcasts"),
    EMISORAS("Emisoras")
}

/**
 * Resultados unificados con texto: Mi biblioteca primero y después
 * los grupos online. Sin internet solo se ve lo local y la línea
 * de aviso; cargando se muestran esqueletos.
 */
@Composable
internal fun UnifiedSearchContent(
    localSongs: List<Song>,
    onlineResults: com.aural.player.youtube.OnlineSearchResults,
    onlineSearching: Boolean,
    onlineError: String?,
    hasInternet: Boolean,
    currentId: Long?,
    isPlaying: Boolean,
    customCovers: Map<Long, String>,
    onPlayLocal: (Song) -> Unit,
    onLocalMenu: (Song) -> Unit,
    onPlayOnline: (
        com.aural.player.youtube.YTResult,
        List<com.aural.player.youtube.YTResult>
    ) -> Unit,
    onOnlineMenu: (com.aural.player.youtube.YTResult) -> Unit,
    onPlayCollection: (com.aural.player.youtube.OnlineCollectionResult) -> Unit
) {
    var filter by remember(onlineResults.query) { mutableStateOf(SearchFilter.ALL) }
    val showLocal = filter == SearchFilter.ALL || filter == SearchFilter.LIBRARY
    val showSongs = hasInternet &&
        (filter == SearchFilter.ALL || filter == SearchFilter.SONGS)
    val showAlbums = hasInternet &&
        (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS)
    val showPlaylists = hasInternet &&
        (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS)
    val showArtists = hasInternet && filter == SearchFilter.ALL
    val nothingOnline = !onlineSearching &&
        onlineResults.songs.isEmpty() && onlineResults.albums.isEmpty() &&
        onlineResults.playlists.isEmpty() && onlineResults.artists.isEmpty()

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(SearchFilter.entries.size) { i ->
                val entry = SearchFilter.entries[i]
                val selected = filter == entry
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { filter = entry }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (!hasInternet) {
                item {
                    Text(
                        text = "Sin conexión: resultados en línea no disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else if (onlineError != null && nothingOnline) {
                item {
                    Text(
                        text = "No se pudieron cargar resultados en línea",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            if (showLocal && localSongs.isNotEmpty()) {
                item { SearchGroupHeader("Mi biblioteca") }
                items(localSongs, key = { "local-${it.id}" }) { song ->
                    SongRow(
                        title = song.title,
                        subtitle = song.artist,
                        coverUri = customCovers[song.id] ?: song.albumArtUri,
                        durationMs = song.durationMs,
                        isPlaying = song.id == currentId && isPlaying,
                        onlineBadge = song.isOnline,
                        onClick = { onPlayLocal(song) },
                        trailing = {
                            TextButton(onClick = { onLocalMenu(song) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    )
                }
            }
            if (onlineSearching && hasInternet) {
                item { SearchGroupHeader("En línea") }
                items(3) { SearchSkeletonRow() }
            }
            if (showSongs && onlineResults.songs.isNotEmpty()) {
                item { SearchGroupHeader("Canciones") }
                items(onlineResults.songs, key = { "song-${it.videoId}" }) { result ->
                    SongRow(
                        title = result.title,
                        subtitle = result.artist,
                        coverUri = result.thumbnailUrl,
                        durationMs = result.durationSec * 1000L,
                        isPlaying = false,
                        onlineBadge = true,
                        onClick = { onPlayOnline(result, onlineResults.songs) },
                        trailing = {
                            TextButton(onClick = { onOnlineMenu(result) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    )
                }
            }
            if (showAlbums && onlineResults.albums.isNotEmpty()) {
                item { SearchGroupHeader("Álbumes") }
                items(onlineResults.albums, key = { "album-${it.url}" }) { album ->
                    OnlineCollectionRow(album) { onPlayCollection(album) }
                }
            }
            if (showPlaylists && onlineResults.playlists.isNotEmpty()) {
                item { SearchGroupHeader("Playlists") }
                items(onlineResults.playlists, key = { "playlist-${it.url}" }) { playlist ->
                    OnlineCollectionRow(playlist) { onPlayCollection(playlist) }
                }
            }
            if (showArtists && onlineResults.artists.isNotEmpty()) {
                item { SearchGroupHeader("Artistas") }
                items(onlineResults.artists, key = { "artist-${it.url}" }) { artist ->
                    OnlineCollectionRow(artist) { onPlayCollection(artist) }
                }
            }
            if (!onlineSearching && localSongs.isEmpty() && nothingOnline) {
                item { EmptyState("Sin resultados para tu búsqueda") }
            }
        }
    }
}

@Composable
private fun SearchGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
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
                    subtitle = "${meta?.second?.ifBlank { song.artist } ?: song.artist} · ${meta?.third?.ifBlank { song.album } ?: song.album}",
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

