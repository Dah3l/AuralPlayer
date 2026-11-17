package com.aural.player.shared

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pestañas de la biblioteca, en el orden de Android (sin Descargas por ahora). */
private enum class LibraryTab(val label: String) {
    SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    PLAYLISTS("Playlists"),
    FOLDERS("Carpetas")
}

/** Vista detallada dentro de una pestaña (álbum, artista o playlist). */
private sealed interface Drill {
    data class Album(val key: String, val title: String, val subtitle: String) : Drill
    data class Artist(val name: String) : Drill
    data class PlaylistDetail(val id: String) : Drill
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
    onPlayFrom: (List<Song>, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreatePlaylistAndAdd: (String, Song) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit,
    onRemoveFromPlaylist: (String, Song) -> Unit,
    onLoadTop10: suspend () -> List<TopSong>
) {
    AuralTheme(theme = state.theme) {
        val scheme = MaterialTheme.colorScheme
        var tab by remember { mutableStateOf(LibraryTab.SONGS) }
        var drill by remember { mutableStateOf<Drill?>(null) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        var showGeneralMenu by remember { mutableStateOf(false) }
        var menuSong by remember { mutableStateOf<Song?>(null) }
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<Playlist?>(null) }
        var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
        var query by remember { mutableStateOf("") }
        var searchFocused by remember { mutableStateOf(false) }
        var top10Songs by remember { mutableStateOf<List<TopSong>?>(null) }

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

        // El Top 10 se carga al abrir su pantalla.
        LaunchedEffect(overlay) {
            if (overlay == Overlay.TOP10) {
                top10Songs = null
                top10Songs = onLoadTop10()
            }
        }

        // La fila de pills se desplaza sola para mostrar la pestaña activa.
        val pillsListState = rememberLazyListState()
        LaunchedEffect(tab) {
            val layout = pillsListState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == tab.ordinal }
            val fullyVisible = item != null && item.offset >= 0 &&
                item.offset + item.size <= layout.viewportEndOffset
            if (!fullyVisible) pillsListState.animateScrollToItem(tab.ordinal)
        }

        fun openTab(next: LibraryTab) {
            tab = next
            drill = null
        }

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
                        onSelect = onThemeChange,
                        onBack = { overlay = null }
                    )
                    Overlay.TOP10 -> Top10Screen(
                        topSongs = top10Songs,
                        onBack = { overlay = null }
                    )
                    null -> when (val current = drill) {
                    is Drill.Album -> {
                        val group = albums.firstOrNull { it.key == current.key }
                        val list = group?.songs ?: emptyList()
                        DrillView(
                            title = current.title,
                            subtitle = current.subtitle,
                            onBack = { drill = null },
                            headerActions = {}
                        ) {
                            SongList(
                                songs = list,
                                currentSong = state.currentSong,
                                isPlaying = state.isPlaying,
                                onPlay = { index -> onPlayFrom(list, index) },
                                onMenu = { song -> menuSong = song }
                            )
                        }
                    }
                    is Drill.Artist -> {
                        val list = artists.firstOrNull { it.first == current.name }?.second
                            ?: emptyList()
                        DrillView(
                            title = current.name,
                            subtitle = "${list.size} canciones",
                            onBack = { drill = null },
                            headerActions = {}
                        ) {
                            SongList(
                                songs = list,
                                currentSong = state.currentSong,
                                isPlaying = state.isPlaying,
                                onPlay = { index -> onPlayFrom(list, index) },
                                onMenu = { song -> menuSong = song }
                            )
                        }
                    }
                    is Drill.PlaylistDetail -> {
                        val playlist = state.playlists.firstOrNull { it.id == current.id }
                        // Si la playlist desapareció, se vuelve a la lista.
                        LaunchedEffect(playlist) {
                            if (playlist == null) drill = null
                        }
                        if (playlist != null) {
                            val list = playlist.songPaths.mapNotNull { songsByPath[it] }
                            DrillView(
                                title = playlist.name,
                                subtitle = "${list.size} canciones",
                                onBack = { drill = null },
                                headerActions = {
                                    TextButton(onClick = { renameTarget = playlist }) {
                                        Text("Renombrar", color = scheme.primary, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { deleteTarget = playlist }) {
                                        Text("Eliminar", color = scheme.error, fontSize = 13.sp)
                                    }
                                }
                            ) {
                                if (list.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Esta playlist está vacía. Añade canciones con el menú ⋯ de cada canción.",
                                            color = scheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    SongList(
                                        songs = list,
                                        currentSong = state.currentSong,
                                        isPlaying = state.isPlaying,
                                        onPlay = { index -> onPlayFrom(list, index) },
                                        onRemove = { song ->
                                            onRemoveFromPlaylist(playlist.id, song)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    null -> when (tab) {
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
                                SongList(
                                    songs = visibleSongs,
                                    currentSong = state.currentSong,
                                    isPlaying = state.isPlaying,
                                    onPlay = { index -> onPlayFrom(visibleSongs, index) },
                                    onMenu = { song -> menuSong = song }
                                )
                            }
                        }
                        LibraryTab.ARTISTS -> {
                            if (artists.isEmpty()) {
                                EmptyTabMessage("Aún no hay artistas. Añade carpetas con música en la pestaña Carpetas.")
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(artists, key = { it.first }) { (name, list) ->
                                        GroupRow(
                                            title = name,
                                            subtitle = "${list.size} canciones",
                                            onClick = { drill = Drill.Artist(name) }
                                        )
                                    }
                                }
                            }
                        }
                        LibraryTab.ALBUMS -> {
                            if (albums.isEmpty()) {
                                EmptyTabMessage("Aún no hay álbumes. Añade carpetas con música en la pestaña Carpetas.")
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(albums, key = { it.key }) { album ->
                                        GroupRow(
                                            title = album.title,
                                            subtitle = "${album.artist} · ${album.songs.size} canciones",
                                            onClick = {
                                                drill = Drill.Album(
                                                    album.key,
                                                    album.title,
                                                    "${album.artist} · ${album.songs.size} canciones"
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        LibraryTab.PLAYLISTS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
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
                                if (state.playlists.isEmpty()) {
                                    EmptyTabMessage("Aún no tienes playlists. Crea la primera con el botón de arriba.")
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(state.playlists, key = { it.id }) { playlist ->
                                            val count = playlist.songPaths.count { songsByPath.containsKey(it) }
                                            GroupRow(
                                                title = playlist.name,
                                                subtitle = "$count canciones",
                                                onClick = { drill = Drill.PlaylistDetail(playlist.id) }
                                            )
                                        }
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
                                    TextButton(onClick = onAddFolder) {
                                        Text(
                                            "Añadir carpeta",
                                            color = scheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (state.folders.isEmpty()) {
                                    EmptyTabMessage("No hay carpetas. Añade una para buscar música.")
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(state.folders, key = { it }) { folder ->
                                            val count = state.songs.count { isInFolder(it.filePath, folder) }
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
                                                TextButton(onClick = { onRemoveFolder(folder) }) {
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

            state.currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    volume = state.volume,
                    muted = state.muted,
                    onVolumeChange = onVolumeChange,
                    onToggleMute = onToggleMute
                )
            }
        }

        // ---------- Diálogos ----------
        menuSong?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = state.playlists,
                onAdd = { playlistId ->
                    onAddToPlaylist(playlistId, song)
                    menuSong = null
                },
                onCreateAndAdd = { name ->
                    onCreatePlaylistAndAdd(name, song)
                    menuSong = null
                },
                onDismiss = { menuSong = null }
            )
        }
        if (showCreatePlaylist) {
            NamePlaylistDialog(
                title = "Nueva playlist",
                confirmLabel = "Crear",
                initialName = "",
                onConfirm = { name ->
                    onCreatePlaylist(name)
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
                    onRenamePlaylist(playlist.id, name)
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
                            onDeletePlaylist(playlist.id)
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
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = scheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun GroupRow(title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

@Composable
private fun DrillView(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    headerActions: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Volver", color = scheme.primary, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = scheme.onSurface,
                    fontSize = 16.sp,
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
            headerActions()
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onMenu: ((Song) -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
            val isCurrent = song.filePath == currentSong?.filePath
            SongRow(
                song = song,
                isCurrent = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onClick = { onPlay(index) },
                trailing = when {
                    onMenu != null -> {
                        {
                            IconButton(onClick = { onMenu(song) }) {
                                Icon(
                                    imageVector = AuralIcons.MoreVert,
                                    contentDescription = "Opciones",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    onRemove != null -> {
                        {
                            IconButton(onClick = { onRemove(song) }) {
                                Icon(
                                    imageVector = AuralIcons.Close,
                                    contentDescription = "Quitar de la playlist",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    else -> null
                }
            )
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) scheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (isPlaying) {
            Text(text = "♪", color = scheme.primary, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = formatTime(song.durationMs), color = scheme.onSurfaceVariant, fontSize = 13.sp)
        trailing?.invoke()
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onAdd: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    text = "${song.title} · ${song.artist}",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text(
                        "Aún no tienes playlists.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    playlists.forEach { playlist ->
                        val contains = song.filePath in playlist.songPaths
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { if (!contains) onAdd(playlist.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = playlist.name,
                                color = scheme.onSurface,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (contains) {
                                Text("✓", color = scheme.primary, fontSize = 15.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nueva playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = { onCreateAndAdd(newName.trim()) }
                    ) {
                        Text("Crear", color = scheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun NamePlaylistDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Mini-reproductor flotante como el de Android: tarjeta con degradado,
 * progreso fino arriba y portada. En PC conserva transporte, búsqueda
 * y volumen hasta que llegue el Now Playing completo (ahí la tarjeta
 * queda solo con play/pausa y abre la pantalla al tocarla, como en el teléfono).
 */
@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    volume: Int,
    muted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Mientras se arrastra, la barra sigue el dedo y solo se busca
    // al soltar: buscar en cada tick entrecorta la reproducción.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
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
    ) {
        Column {
            // Barra de progreso fina e integrada
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
                // Portada (sin imagen todavía: el degradado del placeholder)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(scheme.surfaceVariant, scheme.surface)
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = scheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(7.dp)
                        )
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
                TransportIcon(
                    icon = AuralIcons.SkipPrevious,
                    description = "Anterior",
                    onClick = onPrevious
                )
                TransportIcon(
                    icon = if (isPlaying) AuralIcons.Pause else AuralIcons.Play,
                    description = if (isPlaying) "Pausar" else "Reproducir",
                    onClick = onTogglePlay,
                    prominent = true
                )
                TransportIcon(
                    icon = AuralIcons.SkipNext,
                    description = "Siguiente",
                    onClick = onNext
                )
                Spacer(modifier = Modifier.width(8.dp))
                TransportIcon(
                    icon = if (muted || volume == 0) AuralIcons.VolumeOff else AuralIcons.VolumeUp,
                    description = if (muted) "Activar sonido" else "Silenciar",
                    onClick = onToggleMute
                )
                Slider(
                    value = volume / 100f,
                    onValueChange = { onVolumeChange((it * 100).roundToInt().coerceIn(0, 100)) },
                    modifier = Modifier.width(110.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.outlineVariant
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(text = formatTime(positionMs), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                Slider(
                    value = if (dragging) dragValue else if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragValue = it; dragging = true },
                    onValueChangeFinished = { dragging = false; onSeek(dragValue) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.outlineVariant
                    )
                )
                Text(text = formatTime(durationMs), color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (prominent) scheme.primary else scheme.onSurface,
            modifier = Modifier.size(if (prominent) 30.dp else 24.dp)
        )
    }
}

/** Saludo según la hora, como en Android (sin nombre guardado todavía en PC). */
private fun greetingForHour(): String = when (SystemDates.hour()) {
    in 5..11 -> "Buenos días"
    in 12..17 -> "Buenas tardes"
    else -> "Buenas noches"
}

/** ¿El fondo es oscuro? (luminancia relativa sRGB, igual que Color.luminance). */
private fun isDarkBackground(color: Color): Boolean {
    fun lin(c: Float) =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val luminance = 0.2126f * lin(color.red) + 0.7152f * lin(color.green) + 0.0722f * lin(color.blue)
    return luminance < 0.5f
}

/** ¿La ruta pertenece a la carpeta? (con límite: /Music no casa /Music2). */
private fun isInFolder(path: String, folder: String): Boolean {
    val prefix = folder.trimEnd('/', '\\')
    if (path.length <= prefix.length) return false
    if (!path.startsWith(prefix, ignoreCase = true)) return false
    return path[prefix.length] == '/' || path[prefix.length] == '\\'
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
