// Pantalla principal de AuralPlayer para PC: cabecera con logo y
// saludo, búsqueda, pills de pestañas, biblioteca fiel a la app
// Android (canciones con barra A-Z, cuadrículas de artistas/álbumes,
// playlists con entradas fijas, carpetas), multiselección, hoja de
// opciones de canción, MiniPlayer compacto y Now Playing completo.
package com.aural.player.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pestañas de la biblioteca, en el orden de Android (sin Descargas en PC). */
private enum class LibraryTab(val label: String) {
    SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    PLAYLISTS("Playlists"),
    FOLDERS("Carpetas")
}

/** Vista detallada dentro de una pestaña. */
private sealed interface Drill {
    data class Album(val key: String, val title: String, val subtitle: String) : Drill
    data class Artist(val name: String) : Drill
    data class PlaylistDetail(val id: String) : Drill
    data object Favorites : Drill
    data object Recent : Drill
    data object Daily : Drill
}

/** Pantalla superpuesta (Temas, Top 10) abierta desde el menú ⋮. */
private enum class Overlay { THEMES, TOP10 }

private data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val songs: List<Song>
)

@Composable
fun App(
    state: PlaybackState,
    actions: PlayerActions
) {
    AuralTheme(theme = state.theme) {
        val scheme = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(LibraryTab.SONGS) }
        var drill by remember { mutableStateOf<Drill?>(null) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        var showGeneralMenu by remember { mutableStateOf(false) }
        var menuSong by remember { mutableStateOf<Song?>(null) }
        var menuFromPlaylistId by remember { mutableStateOf<String?>(null) }
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<Playlist?>(null) }
        var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
        var addSongsTarget by remember { mutableStateOf<Playlist?>(null) }
        var query by remember { mutableStateOf("") }
        var searchFocused by remember { mutableStateOf(false) }
        var top10Songs by remember { mutableStateOf<List<TopSong>?>(null) }
        var showNowPlaying by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        var selectedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
        var showSelectionPlaylists by remember { mutableStateOf(false) }
        var showDeleteSelection by remember { mutableStateOf(false) }
        var top50 by remember { mutableStateOf<List<TopSong>?>(null) }

        val selectionMode = selectedSongs.isNotEmpty()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        fun showNotice(text: String) {
            notice = text
        }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(3500)
                notice = null
            }
        }

        fun toggleSelect(song: Song) {
            selectedSongs = if (song.filePath in selectedSongs) {
                selectedSongs - song.filePath
            } else {
                selectedSongs + song.filePath
            }
        }

        fun clearSelection() {
            selectedSongs = emptySet()
        }

        fun openTab(next: LibraryTab) {
            tab = next
            drill = null
            clearSelection()
        }

        fun openDrill(next: Drill) {
            drill = next
            clearSelection()
        }

        // La búsqueda filtra canciones, artistas y álbumes.
        val visibleSongs = remember(state.songs, query) {
            if (query.isBlank()) {
                state.songs
            } else {
                state.songs.filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                }
            }
        }

        // Agrupaciones derivadas de la biblioteca visible.
        val albums = remember(visibleSongs) {
            visibleSongs.groupBy { "${it.album}\u0001${it.artist}" }
                .map { (key, list) ->
                    AlbumGroup(
                        key = key,
                        title = list.first().album.ifBlank { "Álbum desconocido" },
                        artist = list.first().artist,
                        songs = list.sortedBy { it.title.lowercase() }
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
        val artists = remember(visibleSongs) {
            visibleSongs.groupBy { it.artist }
                .map { (name, list) -> name to list.sortedBy { it.title.lowercase() } }
                .sortedBy { it.first.lowercase() }
        }
        val songsByPath = remember(state.songs) { state.songs.associateBy { it.filePath } }
        val favoriteSongs = remember(state.songs, state.favorites) {
            state.songs.filter { it.filePath in state.favorites }
                .sortedBy { it.title.lowercase() }
        }
        val recentSongs = remember(state.songs) {
            val cutoff = SystemDates.nowEpochMillis() - 30L * 24 * 60 * 60 * 1000
            state.songs.filter { it.dateAddedMs > cutoff }
                .sortedByDescending { it.dateAddedMs }
        }
        val daily = remember(state.songs) { dailyPlaylist(state.songs) }

        // El Top 10 se carga al abrir su pantalla; el Top 50 alimenta "Para ti".
        LaunchedEffect(overlay) {
            if (overlay == Overlay.TOP10) {
                top10Songs = null
                top10Songs = runCatching { actions.loadTopSongs(10) }.getOrDefault(emptyList())
            }
        }
        LaunchedEffect(state.songs.isNotEmpty()) {
            if (state.songs.isNotEmpty() && top50 == null) {
                top50 = runCatching { actions.loadTopSongs(50) }.getOrDefault(emptyList())
            }
        }

        // Tarjetas de "Para ti": la playlist del día primero y luego la
        // playlist más escuchada (o la más grande, o Favoritas).
        val paraTiCards = remember(state.songs, state.playlists, state.favorites, top50, daily) {
            val cards = mutableListOf<MostPlayedCard>()
            if (daily.isNotEmpty()) {
                cards += MostPlayedCard(
                    title = "Playlist del día",
                    subtitle = "${daily.size} canciones",
                    coverSong = daily.firstOrNull(),
                    onOpen = { openDrill(Drill.Daily) },
                    onPlay = { actions.playFrom(daily, 0) }
                )
            }
            val secondsMap = top50.orEmpty().associate {
                "${SongKey.normalize(it.title)}\u0001${SongKey.normalize(it.artist)}" to it.seconds
            }
            fun playlistSeconds(playlist: Playlist): Long =
                playlist.songPaths.sumOf { path ->
                    val song = songsByPath[path] ?: return@sumOf 0L
                    secondsMap["${SongKey.normalize(song.title)}\u0001${SongKey.normalize(song.artist)}"] ?: 0L
                }
            val best = state.playlists.maxByOrNull { playlistSeconds(it) }
                ?.takeIf { playlistSeconds(it) > 0 }
                ?: state.playlists.maxByOrNull { it.songPaths.size }
                    ?.takeIf { it.songPaths.isNotEmpty() }
            if (best != null) {
                val list = best.songPaths.mapNotNull { songsByPath[it] }
                cards += MostPlayedCard(
                    title = best.name,
                    subtitle = "${list.size} canciones",
                    coverSong = list.firstOrNull(),
                    onOpen = { openDrill(Drill.PlaylistDetail(best.id)) },
                    onPlay = { if (list.isNotEmpty()) actions.playFrom(list, 0) }
                )
            } else if (favoriteSongs.isNotEmpty()) {
                cards += MostPlayedCard(
                    title = "Favoritas",
                    subtitle = "Tu selección",
                    coverSong = favoriteSongs.firstOrNull(),
                    onOpen = { openDrill(Drill.Favorites) },
                    onPlay = { actions.playFrom(favoriteSongs, 0) }
                )
            }
            cards
        }

        // Datos de la vista de detalle actual (título, subtítulo, canciones).
        val playlistDetailMissing = drill is Drill.PlaylistDetail &&
            state.playlists.none { it.id == (drill as Drill.PlaylistDetail).id }
        LaunchedEffect(playlistDetailMissing) {
            if (playlistDetailMissing) drill = null
        }
        val drillData: Triple<String, String, List<Song>>? = when (val current = drill) {
            is Drill.Album -> {
                val group = albums.firstOrNull { it.key == current.key }
                Triple(current.title, current.subtitle, group?.songs ?: emptyList())
            }
            is Drill.Artist -> {
                val list = artists.firstOrNull { it.first == current.name }?.second ?: emptyList()
                Triple(current.name, "${list.size} canciones", list)
            }
            is Drill.PlaylistDetail -> {
                val playlist = state.playlists.firstOrNull { it.id == current.id }
                if (playlist == null) null else {
                    val list = playlist.songPaths.mapNotNull { songsByPath[it] }
                    Triple(playlist.name, "${list.size} canciones", list)
                }
            }
            Drill.Favorites -> Triple(
                "Favoritas",
                "${favoriteSongs.size} canciones",
                favoriteSongs
            )
            Drill.Recent -> Triple(
                "Agregadas recientemente",
                "${recentSongs.size} canciones",
                recentSongs
            )
            Drill.Daily -> Triple(
                "Playlist del día",
                "${daily.size} canciones",
                daily
            )
            null -> null
        }

        // Lista visible para "Todas" en la barra de multiselección.
        val selectionScope: List<Song> = when {
            drillData != null -> drillData.third
            tab == LibraryTab.SONGS -> visibleSongs
            else -> emptyList()
        }
        val selectedList = remember(selectedSongs, state.songs) {
            state.songs.filter { it.filePath in selectedSongs }
        }

        // Estados de scroll (la barra A-Z salta en ellos).
        val songsListState = rememberLazyListState()
        val artistsGridState = rememberLazyGridState()
        val albumsGridState = rememberLazyGridState()

        // La fila de pills se desplaza sola para mostrar la pestaña activa.
        val pillsListState = rememberLazyListState()
        LaunchedEffect(tab) {
            val layout = pillsListState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == tab.ordinal }
            val fullyVisible = item != null && item.offset >= 0 &&
                item.offset + item.size <= layout.viewportEndOffset
            if (!fullyVisible) pillsListState.animateScrollToItem(tab.ordinal)
        }

        fun playOrToggle(list: List<Song>, song: Song) {
            if (selectionMode) {
                toggleSelect(song)
            } else {
                val index = list.indexOfFirst { it.filePath == song.filePath }.coerceAtLeast(0)
                actions.playFrom(list, index)
            }
        }

        fun openSongMenu(song: Song, fromPlaylistId: String? = null) {
            menuSong = song
            menuFromPlaylistId = fromPlaylistId
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        when {
                            selectionMode -> {
                                clearSelection()
                                true
                            }
                            showNowPlaying -> {
                                showNowPlaying = false
                                true
                            }
                            drill != null -> {
                                drill = null
                                true
                            }
                            overlay != null -> {
                                overlay = null
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
                // ---- Cabecera fija: logo + Aural, saludo debajo, menú ⋮ ----
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoMark(
                            color = scheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Aural",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary
                        )
                        if (isHalloweenWindow()) {
                            Spacer(Modifier.width(6.dp))
                            Text(text = "🎃", fontSize = 28.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { showGeneralMenu = true }) {
                                Icon(
                                    imageVector = AuralIcons.MoreVert,
                                    contentDescription = "Menú"
                                )
                            }
                            DropdownMenu(
                                expanded = showGeneralMenu,
                                onDismissRequest = { showGeneralMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tema") },
                                    onClick = { showGeneralMenu = false; overlay = Overlay.THEMES }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tu Top 10") },
                                    onClick = { showGeneralMenu = false; overlay = Overlay.TOP10 }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = greetingForHour(),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant
                    )
                }

                // ---- Búsqueda siempre visible ----
                val darkBackground = isDarkBackground(scheme.background)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            if (darkBackground) scheme.surfaceContainer
                            else scheme.surfaceVariant
                        )
                        .border(
                            width = if (searchFocused) 2.dp else 1.dp,
                            color = if (searchFocused) scheme.primary else scheme.outline,
                            shape = MaterialTheme.shapes.large
                        )
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
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
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        AuralIcons.Close,
                                        contentDescription = "Borrar búsqueda",
                                        tint = scheme.onSurfaceVariant
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
                    items(LibraryTab.entries.size) { i ->
                        val entry = LibraryTab.entries[i]
                        val selected = tab == entry
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) scheme.primary
                                    else scheme.surfaceVariant
                                )
                                .clickable { openTab(entry) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) scheme.onPrimary
                                else scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (overlay) {
                        Overlay.THEMES -> ThemesScreen(
                            currentTheme = state.theme,
                            onSelect = { actions.setTheme(it) },
                            onBack = { overlay = null }
                        )
                        Overlay.TOP10 -> Top10Screen(
                            topSongs = top10Songs,
                            onBack = { overlay = null }
                        )
                        null -> {
                            val data = drillData
                            if (data != null) {
                                val (drillTitle, drillSubtitle, drillSongs) = data
                                val playlistId =
                                    (drill as? Drill.PlaylistDetail)?.id
                                val playlist =
                                    playlistId?.let { id -> state.playlists.firstOrNull { it.id == id } }
                                DrillView(
                                    title = drillTitle,
                                    subtitle = drillSubtitle,
                                    songs = drillSongs,
                                    coverSong = drillSongs.firstOrNull(),
                                    loadCoverBytes = actions::loadCoverBytes,
                                    onBack = { drill = null },
                                    onPlayAll = {
                                        if (drillSongs.isNotEmpty()) actions.playFrom(drillSongs, 0)
                                    },
                                    onShuffle = {
                                        if (drillSongs.isNotEmpty()) actions.playShuffled(drillSongs)
                                    },
                                    onSongClick = { song -> playOrToggle(drillSongs, song) },
                                    onSongLongPress = { song -> toggleSelect(song) },
                                    onSongMenu = { song -> openSongMenu(song, playlistId) },
                                    playingSongId = state.currentSong?.filePath,
                                    selectionMode = selectionMode,
                                    selectedSongs = selectedSongs,
                                    onAddClick = if (playlist != null) {
                                        { addSongsTarget = playlist }
                                    } else null,
                                    extraActions = {
                                        if (playlist != null) {
                                            TextButton(onClick = { renameTarget = playlist }) {
                                                Text("Renombrar", color = scheme.primary, fontSize = 13.sp)
                                            }
                                            TextButton(onClick = { deleteTarget = playlist }) {
                                                Text("Eliminar", color = scheme.error, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                )
                            } else when (tab) {
                                LibraryTab.SONGS -> when {
                                    state.scanning && state.songs.isEmpty() -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    color = scheme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    "Buscando tu música…",
                                                    color = scheme.onSurfaceVariant,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                    !state.scanning && visibleSongs.isEmpty() -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                if (query.isNotBlank()) "Nada coincide con tu búsqueda."
                                                else "No se encontraron canciones en tus carpetas de música.",
                                                color = scheme.onSurfaceVariant,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    else -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            if (query.isBlank()) {
                                                MostPlayedSection(
                                                    cards = paraTiCards,
                                                    loadCoverBytes = actions::loadCoverBytes
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                SongList(
                                                    songs = visibleSongs,
                                                    listState = songsListState,
                                                    playingSongId = state.currentSong?.filePath,
                                                    selectionMode = selectionMode,
                                                    selectedSongs = selectedSongs,
                                                    onSongClick = { song ->
                                                        playOrToggle(visibleSongs, song)
                                                    },
                                                    onSongLongPress = { song -> toggleSelect(song) },
                                                    onSongMenu = { song -> openSongMenu(song) },
                                                    loadCoverBytes = actions::loadCoverBytes
                                                )
                                                AlphabetFastScroller(
                                                    keys = visibleSongs.map { it.title },
                                                    onJumpToIndex = { index ->
                                                        scope.launch { songsListState.scrollToItem(index) }
                                                    },
                                                    modifier = Modifier.align(Alignment.CenterEnd)
                                                )
                                            }
                                        }
                                    }
                                }
                                LibraryTab.ARTISTS -> {
                                    if (artists.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmptyTabMessage("Aún no hay artistas. Añade carpetas con música en la pestaña Carpetas.")
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(160.dp),
                                                state = artistsGridState,
                                                contentPadding = PaddingValues(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                gridItems(artists, key = { it.first }) { (name, list) ->
                                                    MediaGridCard(
                                                        title = name,
                                                        subtitle = "${list.size} canciones",
                                                        coverSong = list.firstOrNull(),
                                                        loadCoverBytes = actions::loadCoverBytes,
                                                        onClick = { openDrill(Drill.Artist(name)) }
                                                    )
                                                }
                                            }
                                            AlphabetFastScroller(
                                                keys = artists.map { it.first },
                                                onJumpToIndex = { index ->
                                                    scope.launch { artistsGridState.scrollToItem(index) }
                                                },
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                }
                                LibraryTab.ALBUMS -> {
                                    if (albums.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmptyTabMessage("Aún no hay álbumes. Añade carpetas con música en la pestaña Carpetas.")
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(160.dp),
                                                state = albumsGridState,
                                                contentPadding = PaddingValues(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                gridItems(albums, key = { it.key }) { album ->
                                                    MediaGridCard(
                                                        title = album.title,
                                                        subtitle = "${album.artist} · ${album.songs.size} canciones",
                                                        coverSong = album.songs.firstOrNull(),
                                                        loadCoverBytes = actions::loadCoverBytes,
                                                        onClick = {
                                                            openDrill(
                                                                Drill.Album(
                                                                    album.key,
                                                                    album.title,
                                                                    "${album.artist} · ${album.songs.size} canciones"
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                            AlphabetFastScroller(
                                                keys = albums.map { it.title },
                                                onJumpToIndex = { index ->
                                                    scope.launch { albumsGridState.scrollToItem(index) }
                                                },
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                }
                                LibraryTab.PLAYLISTS -> {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            PlaylistEntryRow(
                                                icon = AuralIcons.Heart,
                                                iconTint = Color(0xFFE5484D),
                                                title = "Favoritas",
                                                subtitle = if (favoriteSongs.isNotEmpty()) "Tu selección"
                                                else "Toca el ♡ en tus canciones",
                                                onClick = { openDrill(Drill.Favorites) }
                                            )
                                        }
                                        item {
                                            PlaylistEntryRow(
                                                icon = AuralIcons.Clock,
                                                iconTint = scheme.primary,
                                                title = "Agregadas recientemente",
                                                subtitle = "Últimos 30 días",
                                                onClick = { openDrill(Drill.Recent) }
                                            )
                                        }
                                        item {
                                            PlaylistEntryRow(
                                                icon = AuralIcons.MusicNote,
                                                iconTint = scheme.primary,
                                                title = "Playlist del día",
                                                subtitle = "30 canciones para hoy",
                                                onClick = { openDrill(Drill.Daily) }
                                            )
                                        }
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Tus playlists",
                                                    color = scheme.onSurface,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                TextButton(onClick = { showCreatePlaylist = true }) {
                                                    Text(
                                                        "Nueva playlist",
                                                        color = scheme.primary,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                        if (state.playlists.isEmpty()) {
                                            item {
                                                EmptyTabMessage("Aún no tienes playlists. Crea la primera con el botón de arriba.")
                                            }
                                        } else {
                                            items(state.playlists, key = { it.id }) { playlist ->
                                                val count = playlist.songPaths.count { songsByPath.containsKey(it) }
                                                PlaylistEntryRow(
                                                    icon = AuralIcons.MusicNote,
                                                    iconTint = scheme.onSurfaceVariant,
                                                    title = playlist.name,
                                                    subtitle = "$count canciones",
                                                    onClick = { openDrill(Drill.PlaylistDetail(playlist.id)) }
                                                )
                                            }
                                        }
                                    }
                                }
                                LibraryTab.FOLDERS -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Aural busca música en estas carpetas.",
                                                color = scheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { actions.addFolder() }) {
                                                Text(
                                                    "Añadir carpeta",
                                                    color = scheme.primary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        if (state.folders.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                EmptyTabMessage("No hay carpetas. Añade una para buscar música.")
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(state.folders, key = { it }) { folder ->
                                                    val count = state.songs.count { isInFolder(it, folder) }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .padding(horizontal = 24.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = folder,
                                                                color = scheme.onSurface,
                                                                fontSize = 15.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "$count canciones",
                                                                color = scheme.onSurfaceVariant,
                                                                fontSize = 13.sp
                                                            )
                                                        }
                                                        TextButton(onClick = { actions.removeFolder(folder) }) {
                                                            Text(
                                                                "Quitar",
                                                                color = scheme.error,
                                                                fontSize = 13.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = scheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                // Aviso breve (enlace copiado, guardado, etc.)
                AnimatedVisibility(
                    visible = notice != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        notice?.let {
                            Text(
                                text = it,
                                color = scheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.clip(CircleShape)
                                    .background(scheme.surfaceContainerHigh)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                state.currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = state.isPlaying,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        loadCoverBytes = actions::loadCoverBytes,
                        onOpen = { showNowPlaying = true },
                        onTogglePlay = { actions.togglePlay() }
                    )
                }
            }

            // ---- Barras de multiselección (deslizan desde arriba/abajo) ----
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                SelectionTopBar(
                    count = selectedSongs.size,
                    onClear = { clearSelection() },
                    onSelectAll = {
                        selectedSongs = selectionScope.map { it.filePath }.toSet()
                    }
                )
            }
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SelectionBottomBar(
                    onAddToPlaylist = { showSelectionPlaylists = true },
                    onAddToQueue = {
                        actions.enqueueAll(selectedList)
                        showNotice("Añadidas a la cola")
                        clearSelection()
                    },
                    onFavorite = {
                        actions.addFavorites(selectedList)
                        showNotice("Añadidas a favoritas")
                        clearSelection()
                    },
                    onDelete = { showDeleteSelection = true }
                )
            }

            // ---- Now Playing completo ----
            if (showNowPlaying && state.currentSong != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(scheme.background)
                ) {
                    NowPlayingScreen(
                        state = state,
                        actions = actions,
                        onDismiss = { showNowPlaying = false },
                        onNotice = { showNotice(it) }
                    )
                }
            }

            // ---- Hoja de opciones de canción ----
            menuSong?.let { song ->
                SongMenuSheet(
                    song = song,
                    playlists = state.playlists,
                    isFavorite = song.filePath in state.favorites,
                    fromPlaylistId = menuFromPlaylistId,
                    onDismiss = { menuSong = null },
                    onEnqueue = {
                        actions.enqueue(song)
                        showNotice("Añadida a la cola")
                        menuSong = null
                    },
                    onToggleFavorite = { actions.toggleFavorite(song) },
                    onShareAuralLink = {
                        actions.copyText(AuralLink.shareText(song.title, song.artist))
                        showNotice("Enlace copiado")
                        menuSong = null
                    },
                    onAddToPlaylist = { id -> actions.addToPlaylist(id, song) },
                    onCreateAndAdd = { name -> actions.createPlaylistAndAdd(name, song) },
                    onRemoveFromPlaylist = {
                        menuFromPlaylistId?.let { actions.removeFromPlaylist(it, song) }
                        menuSong = null
                    }
                )
            }
        }

        // ---------- Diálogos ----------
        if (showCreatePlaylist) {
            NamePlaylistDialog(
                title = "Nueva playlist",
                confirmLabel = "Crear",
                initialName = "",
                onConfirm = { name ->
                    actions.createPlaylist(name)
                    showCreatePlaylist = false
                },
                onDismiss = { showCreatePlaylist = false }
            )
        }
        renameTarget?.let { playlist ->
            NamePlaylistDialog(
                title = "Renombrar playlist",
                confirmLabel = "Guardar",
                initialName = playlist.name,
                onConfirm = { name ->
                    actions.renamePlaylist(playlist.id, name)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }
        deleteTarget?.let { playlist ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Eliminar playlist") },
                text = {
                    Text("Se eliminará la lista «${playlist.name}». Las canciones no se borran, solo la lista.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.deletePlaylist(playlist.id)
                            deleteTarget = null
                            drill = null
                        }
                    ) {
                        Text("Eliminar", color = scheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
                }
            )
        }
        addSongsTarget?.let { playlist ->
            AddSongsDialog(
                allSongs = state.songs,
                alreadyIn = playlist.songPaths.toSet(),
                onAdd = { songs ->
                    actions.addAllToPlaylist(playlist.id, songs)
                    addSongsTarget = null
                },
                onDismiss = { addSongsTarget = null }
            )
        }
        if (showSelectionPlaylists) {
            AddToPlaylistDialog(
                songs = selectedList,
                playlists = state.playlists,
                onAdd = { id ->
                    actions.addAllToPlaylist(id, selectedList)
                    showSelectionPlaylists = false
                    clearSelection()
                },
                onCreateAndAdd = { name ->
                    actions.createPlaylistAndAddAll(name, selectedList)
                    showSelectionPlaylists = false
                    clearSelection()
                },
                onDismiss = { showSelectionPlaylists = false }
            )
        }
        if (showDeleteSelection) {
            val count = selectedSongs.size
            AlertDialog(
                onDismissRequest = { showDeleteSelection = false },
                title = { Text("¿Eliminar ${if (count == 1) "esta canción" else "$count canciones"}?") },
                text = {
                    Text(
                        if (count == 1) "Se borrará el archivo de tu PC."
                        else "Se borrarán los archivos de tu PC."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.deleteSongs(selectedList)
                            showDeleteSelection = false
                            clearSelection()
                        }
                    ) {
                        Text("Eliminar", color = scheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelection = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

/** Fila de entrada fija o playlist en la pestaña Playlists. */
@Composable
private fun PlaylistEntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(text = "›", color = scheme.onSurfaceVariant, fontSize = 20.sp)
    }
}

/**
 * Mini-reproductor flotante como el de Android: progreso fino arriba,
 * portada, título/artista y solo play/pausa. Tocar la tarjeta abre
 * el Now Playing completo.
 */
@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    loadCoverBytes: (Song) -> ByteArray?,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(scheme.surfaceContainer, scheme.surface)
                )
            )
            .clickable { onOpen() }
    ) {
        Column {
            if (durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    bitmap = rememberCoverImage(song, loadCoverBytes),
                    modifier = Modifier.size(42.dp),
                    cornerRadius = 7.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) AuralIcons.Pause else AuralIcons.Play,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = scheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

/** Saludo según la hora, como en Android (sin nombre guardado todavía en PC). */
private fun greetingForHour(): String = when (SystemDates.hour()) {
    in 5..11 -> "Buenos días"
    in 12..17 -> "Buenas tardes"
    else -> "Buenas noches"
}
