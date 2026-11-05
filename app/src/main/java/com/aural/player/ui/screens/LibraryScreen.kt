// Biblioteca principal con pestañas: Canciones, Artistas, Álbumes y Playlists
package com.aural.player.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Playlist
import com.aural.player.data.Song
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.HalloweenHomeAmbient
import com.aural.player.ui.components.ChristmasHomeAmbient
import com.aural.player.ui.components.MediaGridCard
import com.aural.player.ui.theme.HalloweenFontFamily
import com.aural.player.ui.components.SectionHeader
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.components.AlphabetFastScroller
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.RadioButton

/** Pestañas de la biblioteca (el orden lo elige el usuario, v1.0.8). */

/**
 * Pantalla de biblioteca. Recibe callbacks para acciones globales
 * (reproducir cola, abrir detalle, menú de canción).
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class
)
@Composable
fun LibraryScreen(
    viewModel: AuralViewModel,
    onPlayList: (songs: List<Song>, index: Int, playlistId: String?) -> Unit,
    onOpenAlbum: (albumTitle: String) -> Unit,
    onOpenFolder: (folder: com.aural.player.data.Folder) -> Unit = {},
    onOpenArtist: (artistName: String) -> Unit,
    onOpenPlaylist: (playlistId: String) -> Unit,
    onOpenThemes: () -> Unit,
    onSongMenu: (song: Song) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onToggleIsland: () -> Unit = {},
    onOpenDonate: () -> Unit = {},
    onOpenContact: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    songsListState: androidx.compose.foundation.lazy.LazyListState,
    albumsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    artistsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    playlistsListState: androidx.compose.foundation.lazy.LazyListState,
    pagerState: androidx.compose.foundation.pager.PagerState =
        // v1.0.8: el juego de pestañas es fijo (6 claves); el orden lo elige
        // el usuario pero la cantidad no cambia.
        rememberPagerState(pageCount = { 6 }),
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    youTubeManager: com.aural.player.youtube.YouTubeManager? = null,
    settingsStore: SettingsStore,
    // El ambiente Halloween solo corre cuando el Home está visible de verdad
    // (con el Now Playing abierto, el Home queda tapado debajo).
    isAmbientVisible: Boolean = true
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    val songs by viewModel.filteredSongsFlow.collectAsStateWithLifecycle()
    val artists by viewModel.artistsFlow.collectAsStateWithLifecycle()
    val albums by viewModel.albumsFlow.collectAsStateWithLifecycle()
    val folders by viewModel.foldersFlow.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val dailyPlaylist by viewModel.dailyPlaylist.collectAsStateWithLifecycle()
    val playlistCovers by viewModel.playlistCovers.collectAsStateWithLifecycle()
    val playCounts by viewModel.playlistPlayCounts.collectAsStateWithLifecycle()
    val downloadsListState = rememberLazyListState()
    val pillsListState = rememberLazyListState()
    // v1.0.8: orden de pestañas elegido por el usuario (claves estables).
    val tabOrder by viewModel.libraryTabOrder.collectAsStateWithLifecycle()
    val tabs = tabOrder.map { tabLabel(it) }
    var showTabCustomizer by remember { mutableStateOf(false) }

    // La fila de pills se desplaza sola para mostrar la pestaña activa
    LaunchedEffect(selectedTab) {
        val layout = pillsListState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedTab }
        val fullyVisible = item != null &&
            item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) pillsListState.animateScrollToItem(selectedTab)
    }

    var showNewPlaylist by remember { mutableStateOf(false) }
    var showSaveDaily by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var showGeneralMenu by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    // v1.0.7: selección múltiple en Canciones.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    // v1.0.8: crear playlist nueva desde el picker y agregarle la selección.
    var pendingIdsForNewPlaylist by remember { mutableStateOf<List<Long>?>(null) }
    var idsBeforePlaylistCreate by remember { mutableStateOf<Set<String>?>(null) }
    // v1.0.7: ocultar carpeta (long-press en Carpetas).
    var folderHideTarget by remember { mutableStateOf<com.aural.player.data.Folder?>(null) }
    var manualUpdateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val audioSearchRequest by viewModel.audioSearchRequest.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(audioSearchRequest) {
        val query = audioSearchRequest ?: return@LaunchedEffect
        viewModel.consumeAudioSearchRequest()
        onTabSelected(tabOrder.indexOf("downloads")) // pestaña Descargas
        youTubeManager?.search(query)
    }
    val m3uPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importM3u(appContext, it) } }
    val refreshScope = rememberCoroutineScope()
    // Pull-to-refresh clásico de Material (método de Aguacero): su
    // indicador gira de verdad y desaparece al terminar el rescan.
    val pullState = androidx.compose.material.pullrefresh.rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshScope.launch {
                    viewModel.rescan()
                    isRefreshing = false
                }
            }
        }
    )

    // "Para ti": la Playlist del día VA PRIMERA (si tiene canciones);
    // después, las más sonadas. Siempre 2 tarjetas. Sin datos todavía:
    // Favoritas y Descargas.
    val downloadsPlaylist = playlists.firstOrNull { it.name == "Descargas" }
    val topPlaylists: List<TopPlaylist> = run {
        fun entryOf(pl: Playlist): TopPlaylist {
            val songsOfPl = viewModel.songsOfPlaylist(pl)
            return TopPlaylist(
                id = pl.id,
                name = pl.name,
                coverUri = playlistCovers[pl.id] ?: songsOfPl.firstOrNull()?.let {
                    customCovers[it.id] ?: it.albumArtUri
                },
                songCount = songsOfPl.size
            )
        }
        val favSongs = viewModel.favoriteSongs()
        val favEntry = TopPlaylist(
            id = FAVORITES_ID,
            name = "Favoritas",
            coverUri = favSongs.firstOrNull()?.let { customCovers[it.id] ?: it.albumArtUri },
            songCount = favSongs.size
        )
        val dlEntry = downloadsPlaylist?.let { entryOf(it) }
        // Playlist del día primera en la lista (si tiene canciones).
        val dailySongs = viewModel.dailyPlaylistSongs()
        val dailyEntry = dailySongs.takeIf { it.isNotEmpty() }?.let { ds ->
            TopPlaylist(
                id = DAILY_PLAYLIST_ID,
                name = "Playlist del día",
                coverUri = ds.firstOrNull()?.let { customCovers[it.id] ?: it.albumArtUri },
                songCount = ds.size
            )
        }
        val rest = if (playCounts.values.none { it > 0 }) {
            listOfNotNull(favEntry, dlEntry)
        } else {
            (playlists.filter { it.name != "Descargas" }.map { entryOf(it) } +
                listOfNotNull(dlEntry) + favEntry)
                .sortedByDescending { playCounts[it.id] ?: 0L }
        }
        listOfNotNull(dailyEntry) + rest.take(if (dailyEntry != null) 1 else 2)
    }

    // Decoración Halloween: solo si el tema activo es Halloween
    // (tema real del menú, no por fecha sola).
    val themeName by viewModel.appTheme.collectAsStateWithLifecycle()
    val isHalloween = themeName == AppTheme.HALLOWEEN.name
    // v1.0.7: Navidad sigue el mismo patrón.
    val isChristmas = themeName == AppTheme.CHRISTMAS.name

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {

        // ---- Cabecera fija: logo + Aural, saludo debajo ----
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = com.aural.player.R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier.size(44.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Aural",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = if (isHalloween) HalloweenFontFamily else null,
                    color = MaterialTheme.colorScheme.primary
                )
                // Calabaza junto al título (tema Halloween activo)
                if (isHalloween) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "🎃", fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = greetingForHour(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Búsqueda siempre visible: color sólido (sin degradado) ----
        // Más oscuro en temas claros; en los oscuros queda el tono de antes.
        val darkBackground =
            MaterialTheme.colorScheme.background.luminance() < 0.5f
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.large)
                // Color sólido: más oscuro en temas claros, igual que antes en los oscuros
                .background(
                    if (darkBackground) MaterialTheme.colorScheme.surfaceContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = if (searchFocused) 2.dp else 1.dp,
                    color = if (searchFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.large
                )
        ) {
            OutlinedTextField(
                value = library.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Canciones, artistas o álbumes…") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
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
                    .onFocusChanged { searchFocused = it.isFocused }
            )
        }

        // ---- Pills de filtros ----
        LazyRow(
            state = pillsListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(tabs.size) { i ->
                val selected = selectedTab == i
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onTabSelected(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabs[i],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // v1.0.8: botón para reordenar las pestañas.
            item {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showTabCustomizer = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Edit, "Personalizar pestañas",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ---- Contenido por pestaña (con pull-to-refresh) ----
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullState)
        ) {
        when {
            library.isLoading -> BoxCentered {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            !library.permissionGranted && library.songs.isEmpty() -> EmptyState(
                "Concede permiso de audio para ver tu biblioteca"
            )

            library.songs.isEmpty() -> EmptyState("No se encontraron canciones en el dispositivo")

            else -> {
                // Pager: el swipe mueve la pill página por página; el tap en la pill mueve el pager.
                // pagerTarget distingue el origen: si el movimiento viene de una pill, se ignoran
                // las páginas intermedias de la animación; si viene del swipe, cada página actualiza la pill.
                // pagerReady evita la carrera al montar: al volver de un detalle, la pestaña manda
                // (p. ej. Descargas=4) y el pager se sincroniza a ella al instante, sin que el efecto
                // de la página vieja la devuelva a la pestaña anterior.
                var pagerTarget by remember { mutableStateOf<Int?>(null) }
                var pagerReady by remember { mutableStateOf(false) }
                LaunchedEffect(pagerState.currentPage) {
                    if (!pagerReady) return@LaunchedEffect
                    if (pagerTarget == null) {
                        if (selectedTab != pagerState.currentPage) {
                            onTabSelected(pagerState.currentPage)
                        }
                    } else if (pagerState.currentPage == pagerTarget) {
                        pagerTarget = null
                    }
                }
                LaunchedEffect(selectedTab) {
                    try {
                        if (pagerState.currentPage != selectedTab) {
                            if (pagerReady) {
                                pagerTarget = selectedTab
                                try {
                                    pagerState.animateScrollToPage(selectedTab)
                                } finally {
                                    pagerTarget = null
                                }
                            } else {
                                pagerState.scrollToPage(selectedTab)
                            }
                        }
                    } finally {
                        pagerReady = true
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    // v1.0.8: las páginas se resuelven por clave, no por índice fijo.
                    when (tabOrder[page]) {
                        "songs" -> SongsTab(
                            songs = songs,
                            favorites = favorites,
                            currentId = playerState.currentSong?.id,
                            isPlaying = playerState.isPlaying,
                            onPlay = { song ->
                                val list = songs
                                onPlayList(list, list.indexOfFirst { it.id == song.id }, null)
                            },
                            onFavorite = viewModel::toggleFavorite,
                            onMenu = onSongMenu,
                            listState = songsListState,
                            customCovers = customCovers,
                            customMetadata = customMetadata,
                            topPlaylists = topPlaylists,
                            onOpenPlaylist = onOpenPlaylist,
                            onPlayPlaylist = { id ->
                                val toPlay = when (id) {
                                    FAVORITES_ID -> viewModel.favoriteSongs()
                                    DAILY_PLAYLIST_ID -> viewModel.dailyPlaylistSongs()
                                    else -> playlists.firstOrNull { it.id == id }?.let { viewModel.songsOfPlaylist(it) }
                                        ?: emptyList()
                                }
                                if (toPlay.isNotEmpty()) onPlayList(toPlay, 0, id)
                            },
                            onSeeAllPlaylists = { onTabSelected(tabOrder.indexOf("playlists")) },
                            selectionMode = selectionMode,
                            selectedIds = selectedIds.toSet(),
                            onToggleSelect = { song ->
                                if (song.id in selectedIds) selectedIds.remove(song.id)
                                else selectedIds.add(song.id)
                            },
                            onEnterSelection = { song ->
                                selectionMode = true
                                selectedIds.clear()
                                selectedIds.add(song.id)
                            }
                        )

                        "artists" -> ArtistsTab(
                            artists = artists,
                            onOpen = { name -> onOpenArtist(name) },
                            gridState = artistsGridState
                        )

                        "albums" -> AlbumsTab(
                            albums = albums,
                            onOpen = { title -> onOpenAlbum(title) },
                            gridState = albumsGridState
                        )

                        "playlists" -> {
                            val downloadsPl = playlists.firstOrNull { it.name == "Descargas" }
                            val dailySongs = viewModel.dailyPlaylistSongs()
                            PlaylistsTab(
                                playlists = playlists,
                                songCount = { viewModel.songsOfPlaylist(it).size },
                                onCreate = { showNewPlaylist = true },
                                onOpen = { pl -> onOpenPlaylist(pl.id) },
                                onPlayPlaylist = { pl -> onPlayList(viewModel.songsOfPlaylist(pl), 0, pl.id) },
                                onRename = { pl -> renameTarget = pl },
                                onDelete = { pl -> deleteTarget = pl },
                                onExport = { pl -> viewModel.exportPlaylist(appContext, pl.id) },
                                onImport = { m3uPicker.launch("audio/x-mpegurl") },
                                hasFavorites = favorites.isNotEmpty(),
                                onOpenFavorites = { onOpenPlaylist(FAVORITES_ID) },
                                downloadsCount = downloadsPl?.let { viewModel.songsOfPlaylist(it).size } ?: 0,
                                onOpenDownloads = { downloadsPl?.let { onOpenPlaylist(it.id) } },
                                dailySongCount = dailySongs.size,
                                dailyCoverUri = dailySongs.firstOrNull()?.let { song ->
                                    customCovers[song.id] ?: song.albumArtUri
                                },
                                onOpenDaily = { onOpenPlaylist(DAILY_PLAYLIST_ID) },
                                onPlayDaily = {
                                    if (dailySongs.isNotEmpty()) onPlayList(dailySongs, 0, DAILY_PLAYLIST_ID)
                                },
                                onSaveDailyPermanent = { showSaveDaily = true },
                                listState = playlistsListState,
                                playlistCovers = playlistCovers,
                                firstSongCover = { pl ->
                                    viewModel.songsOfPlaylist(pl).firstOrNull()?.let { song ->
                                        customCovers[song.id] ?: song.albumArtUri
                                    }
                                },
                                onRemoveCover = { pl -> viewModel.setPlaylistCover(pl.id, "") }
                            )
                        }
                    "downloads" -> {
                            val ytm = youTubeManager
                            if (ytm != null) {
                                DownloadsScreen(
                                    youTubeManager = ytm,
                                    listState = downloadsListState,
                                    onDownloadComplete = { fileName, mediaId ->
                                        if (fileName != null) {
                                            viewModel.addDownloadToPlaylist(fileName, mediaId)
                                            android.widget.Toast.makeText(
                                                appContext,
                                                "Canción descargada",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            } else {
                                Text("YouTube no disponible")
                            }
                        }
                        "folders" -> FoldersTab(
                            folders = folders,
                            onOpen = { folder -> onOpenFolder(folder) },
                            onHideFolder = { folder -> folderHideTarget = folder }
                        )
                    }
                }
            }
        }
            androidx.compose.material.pullrefresh.PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.surface
            )
        }
    }

        // ---- Botón flotante de reconocimiento (opción A) ----
        // Alineado a 16dp del borde derecho y del mini-reproductor, con
        // un anillo que pulsa suavemente para llamar la atención.
        val fabPulse = rememberInfiniteTransition(label = "fab_pulse")
        val fabPulseScale by fabPulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fab_pulse_scale"
        )
        val fabPulseAlpha by fabPulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fab_pulse_alpha"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 16.dp, top = 16.dp, end = 24.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(fabPulseScale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = fabPulseAlpha),
                        CircleShape
                    )
            )
            FloatingActionButton(
                onClick = { viewModel.openAudioSearch() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Identificar canción")
            }
        }
        // ---- Menú general ⋮ fijo arriba a la derecha, como antes ----
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
        ) {            IconButton(onClick = { showGeneralMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menú")
            }
            DropdownMenu(
                expanded = showGeneralMenu,
                onDismissRequest = { showGeneralMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tema") },
                    onClick = { showGeneralMenu = false; onOpenThemes() }
                )
                DropdownMenuItem(
                    text = { Text("Buscar actualizaciones") },
                    onClick = {
                        showGeneralMenu = false
                        if (!checkingUpdate) {
                            checkingUpdate = true
                            refreshScope.launch {
                                settingsStore.setLastUpdateCheckMs(
                                    System.currentTimeMillis()
                                )
                                when (val result = checkForUpdate(appContext)) {
                                    is UpdateCheckResult.Available ->
                                        showUpdateDialog = result.info
                                    is UpdateCheckResult.UpToDate ->
                                        manualUpdateMessage =
                                            "Ya tienes la última versión de Aural."
                                    is UpdateCheckResult.Error ->
                                        manualUpdateMessage =
                                            "No se pudo comprobar la actualización. " +
                                            "Revisa tu conexión e inténtalo de nuevo."
                                }
                                checkingUpdate = false
                            }
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Apoyar a Aural ❤") },
                    onClick = { showGeneralMenu = false; onOpenDonate() }
                )
                DropdownMenuItem(
                    text = { Text("Contacto") },
                    onClick = { showGeneralMenu = false; onOpenContact() }
                )
                DropdownMenuItem(
                    text = { Text("Ajustes") },
                    onClick = { showGeneralMenu = false; onOpenSettings() }
                )
            }
        }
    }

    // ---- v1.0.7: barra de selección múltiple ----
    androidx.activity.compose.BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds.clear()
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
    androidx.compose.animation.AnimatedVisibility(
        visible = selectionMode,
        enter = androidx.compose.animation.slideInVertically { -it },
        exit = androidx.compose.animation.slideOutVertically { -it }
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                bottomStart = 20.dp, bottomEnd = 20.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectionMode = false
                    selectedIds.clear()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancelar selección")
                }
                Text(
                    text = "${selectedIds.size} seleccionada${if (selectedIds.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    } // v1.0.7: cierra Box de la barra de selección

    // ---- v1.0.7: barra de acciones inferior (zona del pulgar) ----
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
    androidx.compose.animation.AnimatedVisibility(
        visible = selectionMode,
        enter = androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.slideOutVertically { it }
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionActionButton(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Playlist",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { showPlaylistPicker = true }
                )
                SelectionActionButton(
                    icon = Icons.Rounded.SelectAll,
                    label = "Todas",
                    enabled = songs.isNotEmpty(),
                    onClick = {
                        // v1.0.8: selecciona las canciones VISIBLES (respeta
                        // el filtro/búsqueda activo, que ya viene aplicado en `songs`).
                        selectedIds.addAll(songs.map { it.id }.filter { it !in selectedIds })
                    }
                )
                SelectionActionButton(
                    icon = Icons.Rounded.QueueMusic,
                    label = "A la cola",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val byId = songs.associateBy { it.id }
                        viewModel.playback.enqueueAll(selectedIds.mapNotNull { byId[it] })
                        selectionMode = false
                        selectedIds.clear()
                    }
                )
                SelectionActionButton(
                    icon = Icons.Rounded.Favorite,
                    label = "Favorita",
                    enabled = selectedIds.isNotEmpty(),
                    tint = Color(0xFFE5484D),
                    onClick = {
                        viewModel.addFavorites(selectedIds.toList())
                        selectionMode = false
                        selectedIds.clear()
                    }
                )
            }
        }
    }
    } // v1.0.7: cierra Box de la barra de acciones inferior

    // ---- Diálogo para crear playlist ----
    renameTarget?.let { pl ->        RenamePlaylistDialog(
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
            onDismiss = {
                showNewPlaylist = false
                pendingIdsForNewPlaylist = null
                idsBeforePlaylistCreate = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showNewPlaylist = false
            }
        )
    }

    // v1.0.8: al aparecer la playlist recién creada, agregarle la selección
    // pendiente y salir del modo selección.
    LaunchedEffect(playlists) {
        val before = idsBeforePlaylistCreate ?: return@LaunchedEffect
        val pending = pendingIdsForNewPlaylist ?: return@LaunchedEffect
        val newPl = playlists.firstOrNull { it.id !in before }
        if (newPl != null) {
            viewModel.addAllToPlaylist(newPl.id, pending)
            pendingIdsForNewPlaylist = null
            idsBeforePlaylistCreate = null
            selectionMode = false
            selectedIds.clear()
        }
    }

    // v1.0.8: personalizar el orden de las pestañas.
    if (showTabCustomizer) {
        TabCustomizerDialog(
            currentOrder = tabOrder,
            onDismiss = { showTabCustomizer = false },
            onConfirm = { newOrder ->
                viewModel.setLibraryTabOrder(newOrder)
                showTabCustomizer = false
            }
        )
    }

    // ---- Diálogo para guardar la Playlist del día como permanente ----
    if (showSaveDaily) {
        SaveDailyPlaylistDialog(
            onDismiss = { showSaveDaily = false },
            onConfirm = { name ->
                viewModel.saveDailyPlaylistAsPermanent(name)
                showSaveDaily = false
            }
        )
    }

    // ---- v1.0.7: elegir playlist para la selección múltiple ----
    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Añadir ${selectedIds.size} a la playlist") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    // v1.0.8: opción para crear una playlist nueva con la selección.
                    item {
                        TextButton(
                            onClick = {
                                pendingIdsForNewPlaylist = selectedIds.toList()
                                idsBeforePlaylistCreate = playlists.map { it.id }.toSet()
                                showPlaylistPicker = false
                                showNewPlaylist = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "＋ Crear nueva playlist",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(playlists, key = { it.id }) { pl ->
                        TextButton(
                            onClick = {
                                viewModel.addAllToPlaylist(pl.id, selectedIds.toList())
                                showPlaylistPicker = false
                                selectionMode = false
                                selectedIds.clear()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                pl.name,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancelar") }
            }
        )
    }

    // ---- v1.0.7: confirmar ocultar carpeta ----
    folderHideTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderHideTarget = null },
            title = { Text("Ocultar carpeta") },
            text = { Text("¿Ocultar \"${folder.displayName}\" de la biblioteca? Podrás mostrarla de nuevo en el menú ⋮ → Carpetas ocultas.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleHiddenFolder(folder.path)
                    folderHideTarget = null
                }) { Text("Ocultar") }
            },
            dismissButton = {
                TextButton(onClick = { folderHideTarget = null }) { Text("Cancelar") }
            }
        )
    }

    // ---- v1.0.7: gestionar carpetas ocultas ----

    // ---- Diálogo de nueva versión (chequeo manual) ----
    showUpdateDialog?.let { info ->
        UpdateAvailableDialog(
            info = info,
            onDownload = { url ->
                showUpdateDialog = null
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
            },
            onLater = {
                showUpdateDialog = null
                refreshScope.launch {
                    settingsStore.setDismissedUpdateVersion(info.version)
                }
            }
        )
    }

    // ---- Buscando… (chequeo manual en curso) ----
    if (checkingUpdate) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Buscando actualizaciones…")
                }
            }
        }
    }

    // ---- Resultado del chequeo manual ----
    manualUpdateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { manualUpdateMessage = null },
            title = { Text("Actualizaciones") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { manualUpdateMessage = null }) { Text("Cerrar") }
            }
        )
    }

    // ---- Ambiente Halloween (tema activo): dibujo puro, no consume toques ----
    // Navidad (diciembre): mismo patrón con ChristmasHomeAmbient.
    if (isHalloween && isAmbientVisible) {
        HalloweenHomeAmbient(Modifier.fillMaxSize())
    }
    if (isChristmas && isAmbientVisible) {
        ChristmasHomeAmbient(Modifier.fillMaxSize())
    }

}


/** ID virtual para la lista de favoritos dentro de las playlists. */
const val FAVORITES_ID = "favorites"

/** ID virtual para la Playlist del día dentro de las playlists. */
const val DAILY_PLAYLIST_ID = "daily_playlist"

/** Contenedor centrado simple. */
@Composable
private fun BoxCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Datos de una playlist para la sección "Para ti". */
private data class TopPlaylist(
    val id: String,
    val name: String,
    val coverUri: String?,
    val songCount: Int
)

/** Sección "Para ti": las 2 playlists que más suenan, con "Ver todas" y título + ▶ dentro de la portada. */
@Composable
private fun MostPlayedSection(
    topPlaylists: List<TopPlaylist>,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Para ti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSeeAll) { Text("Ver todas") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            topPlaylists.forEach { tp ->
                // Tarjeta = solo la portada, con título y ▶ dentro (como la referencia)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenPlaylist(tp.id) },
                    contentAlignment = Alignment.Center
                ) {
                    // Nota musical si no hay imagen
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(40.dp)
                    )
                    if (!tp.coverUri.isNullOrBlank()) {
                        AsyncImage(
                            model = android.net.Uri.parse(tp.coverUri),
                            contentDescription = "Portada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Degradado oscuro abajo para que el título blanco se lea siempre
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.55f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                    // Título + ▶ dentro de la portada, abajo
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = tp.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${tp.songCount} ${if (tp.songCount == 1) "canción" else "canciones"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        // Separación para que el texto no se pegue al botón
                        Spacer(Modifier.width(8.dp))
                        if (tp.songCount > 0) {
                            IconButton(
                                onClick = { onPlayPlaylist(tp.id) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    "Reproducir",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    customCovers: Map<Long, String> = emptyMap(),
    customMetadata: Map<Long, Triple<String, String, String>> = emptyMap(),
    topPlaylists: List<TopPlaylist> = emptyList(),
    onOpenPlaylist: (String) -> Unit = {},
    onPlayPlaylist: (String) -> Unit = {},
    onSeeAllPlaylists: () -> Unit = {},
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
            contentPadding = PaddingValues(bottom = 90.dp, end = 32.dp)
        ) {
            if (topPlaylists.isNotEmpty()) {
                item {
                    MostPlayedSection(
                        topPlaylists = topPlaylists,
                        onOpenPlaylist = onOpenPlaylist,
                        onPlayPlaylist = onPlayPlaylist,
                        onSeeAll = onSeeAllPlaylists,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
            }
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
        AlphabetFastScroller(
            keys = songs.map { it.title },
            onJumpToIndex = { index -> scope.launch { listState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Artistas ---------------------------

@Composable
private fun ArtistsTab(
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
        AlphabetFastScroller(
            keys = artists.map { it.name },
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Álbumes ---------------------------

@Composable
private fun AlbumsTab(
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
        AlphabetFastScroller(
            keys = albums.map { it.title },
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    playlistCovers: Map<String, String>,
    firstSongCover: (Playlist) -> String?,
    onRemoveCover: (Playlist) -> Unit
) {
    LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
        item {
            SectionHeader("Tuyas")
        }
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

// --------------------------- Pestaña: Carpetas ---------------------------

/**
 * Carpetas en lista (no cuadrícula): tocar abre la lista de canciones
 * (como álbum/playlist), el ojo oculta la carpeta con confirmación.
 */
@Composable
private fun FoldersTab(
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

/** Diálogo para guardar la Playlist del día como permanente (nombre editable). */
@Composable
private fun SaveDailyPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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

/**
 * Saludo según la hora del día, estilo cálido.
 */
private fun greetingForHour(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Buenos días"
        in 12..17 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

/**
 * Botón de la barra de acciones de selección múltiple:
 * icono grande + etiqueta, pensado para la zona del pulgar.
 */
@Composable
private fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * v1.0.8: diálogo para reordenar las pestañas de la biblioteca.
 * Usa la misma librería de reorden (sh.calvin.reorderable) que las
 * favoritas y la cola de reproducción.
 */
@Composable
private fun TabCustomizerDialog(
    currentOrder: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var order by remember(currentOrder) { mutableStateOf(currentOrder) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orden de pestañas") },
        text = {
            Text(
                text = "Arrastra para ordenar las pestañas a tu gusto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(order, key = { it }) { key ->
                    ReorderableItem(reorderableState, key = key) { isDragging ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tabLabel(key),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Arrastrar para reordenar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle()
                                    .size(28.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
