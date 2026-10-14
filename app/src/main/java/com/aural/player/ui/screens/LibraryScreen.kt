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

/** Pestañas de la biblioteca. */
private val tabs = listOf("Canciones", "Artistas", "Álbumes", "Playlists")

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
    onTabSelected: (Int) -> Unit
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {

        // ---- Barra superior: título + búsqueda ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aural",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // ---- Pestañas ----
        TabRow(
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
                    onMenu = onSongMenu
                )

                1 -> ArtistsTab(
                    artists = viewModel.artists(),
                    onOpen = { name -> onOpenArtist(name) }
                )

                2 -> AlbumsTab(
                    albums = viewModel.albums(),
                    onOpen = { title -> onOpenAlbum(title) }
                )

                3 -> PlaylistsTab(
                    playlists = playlists,
                    songCount = { viewModel.songsOfPlaylist(it).size },
                    onCreate = { showNewPlaylist = true },
                    onOpen = { pl -> onOpenPlaylist(pl.id) },
                    hasFavorites = favorites.isNotEmpty(),
                    onOpenFavorites = { onOpenPlaylist(FAVORITES_ID) }
                )
            }
        }
    }

    // ---- Diálogo para crear playlist ----
    if (showNewPlaylist) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylist = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showNewPlaylist = false
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
    onMenu: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState("Sin resultados para tu búsqueda")
        return
    }
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = { onFavorite(song.id) }) {
                            Icon(
                                imageVector = if (favorites.contains(song.id))
                                    Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (favorites.contains(song.id))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        TextButton(onClick = { onMenu(song) }) {
                            Text("···", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            )
        }
    }
}

// --------------------------- Pestaña: Artistas ---------------------------

@Composable
private fun ArtistsTab(artists: List<com.aural.player.data.Artist>, onOpen: (String) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            MediaGridCard(
                coverUri = artist.representativeSong.albumArtUri,
                title = artist.name,
                subtitle = "${artist.songs.size} canciones",
                onClick = { onOpen(artist.name) }
            )
        }
    }
}

// --------------------------- Pestaña: Álbumes ---------------------------

@Composable
private fun AlbumsTab(albums: List<com.aural.player.data.Album>, onOpen: (String) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 100.dp),
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
}

// --------------------------- Pestaña: Playlists ---------------------------

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    songCount: (Playlist) -> Int,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    hasFavorites: Boolean,
    onOpenFavorites: () -> Unit
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
                        if (hasFavorites) "Tu selección" else "Aún sin favoritas",
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
        // Botón crear playlist
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
                    Text("Nueva playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (playlists.isEmpty()) {
            item { EmptyState("Todavía no has creado playlists") }
        } else {
            items(playlists, key = { it.id }) { pl ->
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
                            "${songCount(pl)} canciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onOpen(pl) }) {
                        Icon(Icons.Rounded.PlayArrow, "Reproducir",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// --------------------------- Diálogo nueva playlist ---------------------------

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Nueva playlist") },
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
