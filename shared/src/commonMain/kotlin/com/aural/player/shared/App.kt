package com.aural.player.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class LibraryTab(val label: String) {
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    PLAYLISTS("Playlists"),
    FOLDERS("Carpetas")
}

/** Vista detallada dentro de una pestaña (álbum, artista o playlist). */
private sealed interface Drill {
    data class Album(val key: String, val title: String, val subtitle: String) : Drill
    data class Artist(val name: String) : Drill
    data class PlaylistDetail(val id: String) : Drill
}

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
    onRemoveFromPlaylist: (String, Song) -> Unit
) {
    AuralTheme(theme = state.theme) {
        val scheme = MaterialTheme.colorScheme
        var themeMenuExpanded by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(LibraryTab.SONGS) }
        var drill by remember { mutableStateOf<Drill?>(null) }
        var menuSong by remember { mutableStateOf<Song?>(null) }
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<Playlist?>(null) }
        var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

        // Agrupaciones derivadas de la biblioteca (se recalculan al cambiar).
        val albums = remember(state.songs) {
            state.songs.groupBy { "${it.album}\u0001${it.artist}" }
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
        val artists = remember(state.songs) {
            state.songs.groupBy { it.artist }
                .map { (name, list) -> name to list.sortedBy { it.title.lowercase() } }
                .sortedBy { it.first.lowercase() }
        }
        val songsByPath = remember(state.songs) { state.songs.associateBy { it.filePath } }

        fun openTab(next: LibraryTab) {
            tab = next
            drill = null
        }

        Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aural",
                    color = scheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Player",
                    color = scheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "El sonido, sin ruido.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (state.scanning) {
                    Spacer(modifier = Modifier.width(16.dp))
                    CircularProgressIndicator(
                        color = scheme.primary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Buscando música…",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { themeMenuExpanded = true }) {
                        Text(
                            text = state.theme.displayName,
                            color = scheme.primary,
                            fontSize = 13.sp
                        )
                    }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        // Halloween y Navidad solo aparecen en su
                        // temporada (igual que en Android); el tema
                        // activo siempre se muestra aunque su
                        // ventana haya terminado.
                        val visibleThemes = AppTheme.entries.filter { item ->
                            when (item) {
                                AppTheme.HALLOWEEN -> item == state.theme || isHalloweenWindow()
                                AppTheme.CHRISTMAS -> item == state.theme || isChristmasWindow()
                                else -> true
                            }
                        }
                        visibleThemes.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(12.dp)
                                                .background(item.colorScheme().primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(item.displayName)
                                    }
                                },
                                onClick = {
                                    onThemeChange(item)
                                    themeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))

            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = scheme.background,
                contentColor = scheme.primary
            ) {
                LibraryTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { openTab(entry) },
                        text = { Text(entry.label, fontSize = 14.sp) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val current = drill) {
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
                            !state.scanning && state.songs.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No se encontraron canciones en tus carpetas de música.",
                                        color = scheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            else -> {
                                SongList(
                                    songs = state.songs,
                                    currentSong = state.currentSong,
                                    isPlaying = state.isPlaying,
                                    onPlay = { index -> onPlayFrom(state.songs, index) },
                                    onMenu = { song -> menuSong = song }
                                )
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

            state.error?.let { error ->
                Text(
                    text = error,
                    color = scheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            state.currentSong?.let { song ->
                PlayerBar(
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

@Composable
private fun PlayerBar(
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
    Column(
        modifier = Modifier.fillMaxWidth().background(scheme.surface).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
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
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
