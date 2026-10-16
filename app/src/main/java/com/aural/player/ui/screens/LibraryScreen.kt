// Biblioteca principal con pestañas: Canciones, Artistas, Álbumes y Playlists
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Playlist
import com.aural.player.data.Song
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.MediaGridCard
import com.aural.player.ui.components.SectionHeader
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.components.AlphabetFastScroller
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ScrollableTabRow
import com.aural.player.youtube.YouTubeManager
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Download
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

/** Pestañas de la biblioteca. */
private val tabs = listOf("Canciones", "Artistas", "Álbumes", "Playlists", "Descargas", "Carpetas")

/**
 * Pantalla de biblioteca. Recibe callbacks para acciones globales
 * (reproducir cola, abrir detalle, menú de canción).
 */
@Composable
fun LibraryScreen(
    viewModel: AuralViewModel,
    onPlayList: (songs: List<Song>, index: Int) -> Unit,
    onOpenAlbum: (albumTitle: String) -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
    onOpenPlaylist: (playlistId: String) -> Unit,
    onSongMenu: (song: Song) -> Unit,
    songsListState: androidx.compose.foundation.lazy.LazyListState,
    albumsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    artistsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    youTubeManager: com.aural.player.youtube.YouTubeManager? = null
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var showGeneralMenu by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val m3uPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importM3u(appContext, it) } }
    val refreshScope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {

        // ---- Barra superior: título + búsqueda ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = com.aural.player.R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Aural",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = greetingForHour(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = {
                    isRefreshing = true
                    refreshScope.launch {
                        viewModel.rescan()
                        isRefreshing = false
                    }
                }
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
            // ---- Menú general ⋮ ----
            Box {
                IconButton(onClick = { showGeneralMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                }
                DropdownMenu(
                    expanded = showGeneralMenu,
                    onDismissRequest = { showGeneralMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Licencia") },
                        onClick = { showGeneralMenu = false; showLicenseDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Repositorio en GitHub") },
                        onClick = {
                            showGeneralMenu = false
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Dah3l/AuralPlayer")
                            )
                            appContext.startActivity(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Acerca de AuralPlayer") },
                        onClick = { showGeneralMenu = false; showAboutDialog = true }
                    )
                }
            }
        }

        // ---- Campo de búsqueda local ----
        if (showSearch) {
            OutlinedTextField(
                value = library.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Canciones, artistas o álbumes…") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    if (library.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Borrar búsqueda",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // ---- Pestañas ----
        ScrollableTabRow(
                edgePadding = 0.dp,
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { onTabSelected(i) },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // ---- Contenido por pestaña ----
        when {
            library.isLoading -> BoxCentered {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            !library.permissionGranted && library.songs.isEmpty() -> EmptyState(
                "Concede permiso de audio para ver tu biblioteca"
            )

            library.songs.isEmpty() -> EmptyState("No se encontraron canciones en el dispositivo")

            else -> when (selectedTab) {
                0 -> SongsTab(
                    songs = library.filteredSongs,
                    favorites = favorites,
                    currentId = playerState.currentSong?.id,
                    isPlaying = playerState.isPlaying,
                    onPlay = { song -> onPlayList(library.filteredSongs, library.filteredSongs.indexOf(song)) },
                    onFavorite = viewModel::toggleFavorite,
                    onMenu = onSongMenu,
                    listState = songsListState
                )

                1 -> ArtistsTab(
                    artists = viewModel.artists(),
                    onOpen = { name -> onOpenArtist(name) },
                    gridState = artistsGridState
                )

                2 -> AlbumsTab(
                    albums = viewModel.albums(),
                    onOpen = { title -> onOpenAlbum(title) },
                    gridState = albumsGridState
                )

                3 -> {
                    val downloadsPl = playlists.firstOrNull { it.name == "Descargas" }
                    PlaylistsTab(
                        playlists = playlists,
                        songCount = { viewModel.songsOfPlaylist(it).size },
                        onCreate = { showNewPlaylist = true },
                        onOpen = { pl -> onOpenPlaylist(pl.id) },
                        onRename = { pl -> renameTarget = pl },
                        onDelete = { pl -> deleteTarget = pl },
                        onExport = { pl -> viewModel.exportPlaylist(appContext, pl.id) },
                        onImport = { m3uPicker.launch("audio/x-mpegurl") },
                        hasFavorites = favorites.isNotEmpty(),
                        onOpenFavorites = { onOpenPlaylist(FAVORITES_ID) },
                        downloadsCount = downloadsPl?.let { viewModel.songsOfPlaylist(it).size } ?: 0,
                        onOpenDownloads = { downloadsPl?.let { onOpenPlaylist(it.id) } }
                    )
                }
            4 -> {
                    val ytm = youTubeManager
                    if (ytm != null) {
                        DownloadsScreen(
                            youTubeManager = ytm,
                            onDownloadComplete = { fileName ->
                                if (fileName != null) {
                                    viewModel.addDownloadToPlaylist(fileName)
                                }
                            }
                        )
                    } else {
                        Text("YouTube no disponible")
                    }
                }
                5 -> FoldersTab(
                    folders = viewModel.folders(),
                    onPlay = { folder -> onPlayList(folder.songs, 0) }
                )
            }
        }
    }

    // ---- Diálogo para crear playlist ----
    renameTarget?.let { pl ->
        RenamePlaylistDialog(
            currentName = pl.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(pl.id, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Eliminar \"${pl.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(pl.id)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }

    if (showNewPlaylist) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylist = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showNewPlaylist = false
            }
        )
    }

    // ---- Diálogo de licencia ----
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("Licencia") },
            text = {
                Text(
                    "AuralPlayer es software libre bajo la GNU General Public " +
                        "License v3.0.\n\nPuedes usar, modificar y compartir " +
                        "esta app libremente, siempre que cualquier versión " +
                        "distribuida mantenga esta misma licencia y el código " +
                        "fuente disponible.\n\nCopyright (C) 2026 Dahel"
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("Cerrar") }
            }
        )
    }

    // ---- Diálogo acerca de ----
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Acerca de AuralPlayer") },
            text = {
                Text(
                    "AuralPlayer — tu música, sin ruido.\n\n" +
                        "Reproductor minimalista con descargas de YouTube, " +
                        "letras sincronizadas y diseño oscuro premium.\n\n" +
                        "Versión 1.0.0\nHecho con ❤️ y Jetpack Compose"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

/** ID virtual para la lista de favoritos dentro de las playlists. */
const val FAVORITES_ID = "favorites"

/** Contenedor centrado simple. */
@Composable
private fun BoxCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// --------------------------- Pestaña: Canciones ---------------------------

@Composable
private fun SongsTab(
    songs: List<Song>,
    favorites: Set<Long>,
    currentId: Long?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onFavorite: (Long) -> Unit,
    onMenu: (Song) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
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
            contentPadding = PaddingValues(bottom = 90.dp, end = 32.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    title = song.title,
                    subtitle = "${song.artist} · ${song.album}",
                    coverUri = song.albumArtUri,
                    durationMs = song.durationMs,
                    isPlaying = song.id == currentId,
                    onClick = { onPlay(song) },
                    trailing = {
                        TextButton(onClick = { onMenu(song) }) {
                            Text("···", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                )
            }
        }
        AlphabetFastScroller(
            keys = songs.map { it.title },
            onJumpToIndex = { index -> scope.launch { listState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// --------------------------- Pestaña: Artistas ---------------------------

@Composable
private fun ArtistsTab(
    artists: List<com.aural.player.data.Artist>,
    onOpen: (String) -> Unit,
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
            items(artists, key = { it.name }) { artist ->
                MediaGridCard(
                    coverUri = artist.representativeSong.albumArtUri,
                    title = artist.name,
                    subtitle = "${artist.songs.size} ${if (artist.songs.size == 1) "canción" else "canciones"}",
                    onClick = { onOpen(artist.name) }
                )
            }
        }
        AlphabetFastScroller(
            keys = artists.map { it.name },
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// --------------------------- Pestaña: Álbumes ---------------------------

@Composable
private fun AlbumsTab(
    albums: List<com.aural.player.data.Album>,
    onOpen: (String) -> Unit,
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
            items(albums, key = { it.id }) { album ->
                MediaGridCard(
                    coverUri = album.songs.firstOrNull()?.albumArtUri,
                    title = album.title,
                    subtitle = album.artist,
                    onClick = { onOpen(album.title) }
                )
            }
        }
        AlphabetFastScroller(
            keys = albums.map { it.title },
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// --------------------------- Pestaña: Playlists ---------------------------

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    songCount: (Playlist) -> Int,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onExport: (Playlist) -> Unit,
    onImport: () -> Unit,
    hasFavorites: Boolean,
    onOpenFavorites: () -> Unit,
    downloadsCount: Int,
    onOpenDownloads: () -> Unit
) {
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
        item {
            SectionHeader("Tuyas")
        }
        // Entrada fija de Favoritos
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Text("Importar .m3u", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (playlists.isEmpty()) {
            item { EmptyState("Todavía no has creado playlists") }
        } else {
            items(playlists.filter { it.name != "Descargas" }, key = { it.id }) { pl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArt(uri = null, size = 48.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "${songCount(pl)} ${if (songCount(pl) == 1) "canción" else "canciones"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onOpen(pl) }) {
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
                                text = { Text("Exportar .m3u") },
                                onClick = {
                                    showMenu = false
                                    onExport(pl)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------- Pestaña: Carpetas ---------------------------

@Composable
private fun FoldersTab(
    folders: List<com.aural.player.data.Folder>,
    onPlay: (com.aural.player.data.Folder) -> Unit
) {
    if (folders.isEmpty()) {
        EmptyState("Sin carpetas"); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            MediaGridCard(
                coverUri = folder.songs.firstOrNull()?.albumArtUri,
                title = folder.displayName,
                subtitle = "${folder.songs.size} ${if (folder.songs.size == 1) "canción" else "canciones"}",
                onClick = { onPlay(folder) }
            )
        }
    }
}

// --------------------------- Diálogo nueva playlist ---------------------------

@Composable
private fun RenamePlaylistDialog(
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
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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

/**
 * Saludo según la hora del día, estilo cálido.
 */
private fun greetingForHour(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Buenos días ☀️"
        in 12..17 -> "Buenas tardes 🌤️"
        else -> "Buenas noches 🌙"
    }
}
