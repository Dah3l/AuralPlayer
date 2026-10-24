// Actividad principal: permisos, navegación de pantallas y mini-reproductor
package com.aural.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.app.Application
import com.aural.player.data.Song
import com.aural.player.ui.components.MiniPlayer
import com.aural.player.ui.screens.DetailScreen
import com.aural.player.ui.screens.FAVORITES_ID
import com.aural.player.ui.screens.LibraryScreen
import com.aural.player.ui.screens.NowPlayingScreen
import com.aural.player.ui.screens.ThemesScreen
import com.aural.player.ui.theme.AuralTheme
import com.aural.player.ui.theme.AppTheme
import com.aural.player.data.SettingsStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aural.player.ui.components.SplashScreen
import com.aural.player.ui.screens.OnboardingScreen
import com.aural.player.data.OnboardingPrefs

/** Pantallas navegables de la app (navegación simple por estado). */
private sealed class Screen {
    data object Library : Screen()
    data object Themes : Screen()
    data class Album(val title: String) : Screen()
    data class Artist(val name: String) : Screen()
    data class Playlist(val id: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: AuralViewModel by viewModels()
    private lateinit var youTubeManager: com.aural.player.youtube.YouTubeManager

    /** Launcher de permisos: audio + notificaciones según la versión. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionResult(results.values.any { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAudioSearchIntent(intent)
        youTubeManager = com.aural.player.youtube.YouTubeManager(this)
        enableEdgeToEdge()
        requestNeededPermissions()

        setContent {
            val settingsStore = SettingsStore(this@MainActivity)
            val themeName by settingsStore.appTheme.collectAsState(initial = "BOSQUE")
            val appTheme = try {
                AppTheme.valueOf(themeName)
            } catch (e: Exception) {
                AppTheme.BOSQUE
            }
            AuralTheme(theme = appTheme) {
                var showOnboarding by remember {
                    mutableStateOf(!OnboardingPrefs.isSeen(this@MainActivity))
                }
                if (showOnboarding) {
                    OnboardingScreen(
                        onFinish = {
                            OnboardingPrefs.markSeen(this@MainActivity)
                            showOnboarding = false
                        }
                    )
                } else {
                    AuralApp(viewModel, youTubeManager = youTubeManager)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAudioSearchIntent(intent)
    }

    /** Abre la búsqueda por audio si el intent viene del tile de ajustes rápidos. */
    private fun handleAudioSearchIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(
                com.aural.player.AudioSearchTileService.EXTRA_OPEN_AUDIO_SEARCH, false
            ) == true
        ) {
            viewModel.openAudioSearch()
        }
    }

    /**
     * Solicita READ_MEDIA_AUDIO en Android 13+ o READ_EXTERNAL_STORAGE
     * en versiones anteriores; añade POST_NOTIFICATIONS si hace falta.
     */
    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!isGranted(audioPermission)) needed += audioPermission

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            // Ya concedidos previamente: escanear directamente
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

// ---------------------------------------------------------------------------
// Raíz de la interfaz
// ---------------------------------------------------------------------------

@Composable
private fun AuralApp(viewModel: AuralViewModel,
    youTubeManager: com.aural.player.youtube.YouTubeManager?) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var showSplash by remember { mutableStateOf(true) }
    // El splash se oculta tras 1.8s (cortina mientras carga)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        showSplash = false
    }
    // Estados elevados: sobreviven a la navegación para recordar posición
    val songsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val albumsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val artistsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val playlistsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val themesGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Estados de scroll de cada detalle, por destino: al volver atrás la
    // lista queda donde estaba (cada álbum, artista y playlist por separado)
    val detailListStates = remember {
        mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>()
    }
    fun detailListState(key: String) =
        detailListStates.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }
    var libraryTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    // Playlist desde la que se abrió el menú de canción (null si fue desde
    // la biblioteca, un álbum o un artista): cambia "Añadir a playlist"
    // por "Eliminar de la lista"
    var songMenuPlaylistId by remember { mutableStateOf<String?>(null) }

    val library by viewModel.library.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
        val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
        val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Mini-reproductor persistente sobre la barra de navegación
        bottomBar = {
            AnimatedVisibility(
                visible = playerState.currentSong != null && !showNowPlaying,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                MiniPlayer(
                    state = playerState,
                    modifier = Modifier.navigationBarsPadding(),
                    onOpen = { showNowPlaying = true },
                    onTogglePlay = viewModel.playback::togglePlayPause,
                    customCoverUri = playerState.currentSong?.let { customCovers[it.id] },
                    customMetadata = playerState.currentSong?.let { customMetadata[it.id] }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Atrás en detalle -> vuelve a la biblioteca (conserva posición)
            androidx.activity.compose.BackHandler(enabled = screen != Screen.Library) {
                screen = Screen.Library
            }
            // Atrás en Now Playing -> vuelve a la pantalla anterior
            androidx.activity.compose.BackHandler(enabled = showNowPlaying) {
                showNowPlaying = false
            }
            when (val s = screen) {
                Screen.Library -> LibraryScreen(
                    viewModel = viewModel,
                    onPlayList = { songs, index ->
                        viewModel.playSongs(songs, index)
                        showNowPlaying = true
                    },
                    onOpenAlbum = { screen = Screen.Album(it) },
                    onOpenArtist = { screen = Screen.Artist(it) },
                    onOpenPlaylist = { screen = Screen.Playlist(it) },
                    onOpenThemes = { screen = Screen.Themes },
                    onSongMenu = { song ->
                        songMenuTarget = song
                        songMenuPlaylistId = null
                    },
                    songsListState = songsListState,
                    albumsGridState = albumsGridState,
                    artistsGridState = artistsGridState,
                    playlistsListState = playlistsListState,
                    selectedTab = libraryTab,
                    onTabSelected = { libraryTab = it },
                        youTubeManager = youTubeManager
                    )

                is Screen.Album -> {
                    val allSongs by viewModel.songsFlow.collectAsStateWithLifecycle()
                    val songs = allSongs.filter { it.album == s.title }
                    DetailScreen(
                        viewModel = viewModel,
                        title = s.title,
                        subtitle = songs.firstOrNull()?.artist ?: "",
                        songs = songs.sortedBy { it.trackNumber },
                        coverUri = songs.firstOrNull()?.albumArtUri,
                        listState = detailListState("album:${s.title}"),
                        onBack = { screen = Screen.Library },
                        onSongMenu = { song ->
                            songMenuTarget = song
                            songMenuPlaylistId = null
                        }
                    )
                }

                is Screen.Artist -> {
                    val allSongs by viewModel.songsFlow.collectAsStateWithLifecycle()
                    val songs = allSongs.filter { it.artist == s.name }
                    DetailScreen(
                        viewModel = viewModel,
                        title = s.name,
                        subtitle = "Artista",
                        songs = songs,
                        coverUri = songs.firstOrNull()?.albumArtUri,
                        listState = detailListState("artist:${s.name}"),
                        onBack = { screen = Screen.Library },
                        onSongMenu = { song ->
                            songMenuTarget = song
                            songMenuPlaylistId = null
                        }
                    )
                }

                is Screen.Playlist -> {
                    val isFavorites = s.id == FAVORITES_ID
                    val playlist = playlists.find { it.id == s.id }
                    val isDownloads = playlist?.name == "Descargas"
                    val songs = if (isFavorites) viewModel.favoriteSongs()
                    else playlist?.let { viewModel.songsOfPlaylist(it) } ?: emptyList()

                    DetailScreen(
                        viewModel = viewModel,
                        title = if (isFavorites) "Canciones favoritas" else playlist?.name ?: "",
                        subtitle = if (isFavorites) "Tus preferidas" else "Playlist",
                        songs = songs,
                        coverUri = songs.firstOrNull()?.albumArtUri,
                        listState = detailListState("playlist:${s.id}"),
                        onBack = { screen = Screen.Library },
                        onSongMenu = { song ->
                            songMenuTarget = song
                            // Favoritas se comporta como las demás: su menú
                            // ofrece «Eliminar de la lista» (quitar favorito)
                            songMenuPlaylistId = s.id
                        },
                        removableFromPlaylistId = s.id,
                        // Solo las playlists creadas tienen foto editable;
                        // Favoritas y Descargas conservan su identidad
                        playlistCoverId = if (isFavorites || isDownloads) null else s.id
                    )
                }

                Screen.Themes -> {
                    val themeName by viewModel.appTheme.collectAsStateWithLifecycle()
                    val currentTheme = try {
                        AppTheme.valueOf(themeName)
                    } catch (e: Exception) {
                        AppTheme.BOSQUE
                    }
                    ThemesScreen(
                        currentTheme = currentTheme,
                        onSelect = { theme -> viewModel.setAppTheme(theme.name) },
                        onBack = { screen = Screen.Library },
                        gridState = themesGridState
                    )
                }
            }
        }
    }

    // ---- Diálogo de búsqueda por audio ----
    val showAudioSearch by viewModel.showAudioSearch.collectAsStateWithLifecycle()
    if (showAudioSearch) {
        com.aural.player.ui.components.AudioSearchDialog(
            onDismiss = { viewModel.closeAudioSearch() },
            onSearchYouTube = { query ->
                viewModel.closeAudioSearch()
                viewModel.requestYouTubeSearch(query)
            }
        )
    }

    // ---- Pantalla Now Playing a pantalla completa ----
    if (showNowPlaying) {
        NowPlayingScreen(
            viewModel = viewModel,
            state = playerState,
            isFavorite = playerState.currentSong?.let { favorites.contains(it.id) } ?: false,
            onDismiss = { showNowPlaying = false },
            onTogglePlay = viewModel.playback::togglePlayPause,
            onNext = viewModel.playback::seekToNext,
            onPrevious = viewModel.playback::seekToPrevious,
            onSeek = viewModel.playback::seekTo,
            onToggleShuffle = viewModel.playback::toggleShuffle,
            onCycleRepeat = viewModel.playback::cyclePlayMode,
            onToggleFavorite = {
                playerState.currentSong?.let { viewModel.toggleFavorite(it.id) }
            },
            onPlayQueueItem = viewModel.playback::playAt,
            onRemoveFromQueue = viewModel.playback::removeFromQueue,
            onMoveQueueItem = viewModel.playback::moveInQueue,
        )
    }
    // ---- Splash de bienvenida ----
    if (showSplash) {
        SplashScreen()
    }

    // ---- Menú contextual de canción: añadir a playlist / favoritos ----
    songMenuTarget?.let { song ->
        SongMenuSheet(
            song = song,
            playlists = playlists,
            isFavorite = favorites.contains(song.id),
            removeFromPlaylistId = songMenuPlaylistId,
            onDismiss = {
                songMenuTarget = null
                songMenuPlaylistId = null
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(song.id)
                songMenuTarget = null
                songMenuPlaylistId = null
            },
            onAddToPlaylist = { plId ->
                viewModel.addToPlaylist(plId, song.id)
                songMenuTarget = null
                songMenuPlaylistId = null
            },
            onEnqueue = {
                viewModel.playback.enqueue(song)
                songMenuTarget = null
                songMenuPlaylistId = null
            },
            onRemoveFromPlaylist = {
                val pid = songMenuPlaylistId
                if (pid == FAVORITES_ID) {
                    viewModel.toggleFavorite(song.id)
                } else {
                    pid?.let { viewModel.removeFromPlaylist(it, song.id) }
                }
                songMenuTarget = null
                songMenuPlaylistId = null
            }
        )
    }

    // ---- Diálogo de reporte de crash (diagnóstico temporal) ----
    val appCtx = LocalContext.current.applicationContext as Application
    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashReport = com.aural.player.AuralApp.readCrashReport(appCtx)
    }
    crashReport?.let { report ->
        CrashReportDialog(
            report = report,
            onDismiss = {
                com.aural.player.AuralApp.clearCrashReport(appCtx)
                crashReport = null
            }
        )
    }
}

/** Muestra el último crash para diagnóstico. Temporal. */
@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("La app se cerró antes") },
        text = {
            Text(
                text = "Copia este error y mándalo para arreglarlo:\n\n" + report.take(3000),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                onDismiss()
            }) { Text("Copiar error") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/** Hoja inferior con acciones para una canción concreta. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun SongMenuSheet(
    song: Song,
    playlists: List<com.aural.player.data.Playlist>,
    isFavorite: Boolean,
    removeFromPlaylistId: String? = null,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onEnqueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit = {}
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 4.dp),
            maxLines = 1
        )
        TextButton(onClick = onEnqueue, modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)) {
            Text("Añadir a la cola")
        }
        TextButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)) {
            Text(if (isFavorite) "Quitar de favoritas" else "Marcar como favorita")
        }
        if (removeFromPlaylistId != null) {
            // Abierto desde una playlist: quitar de esta lista en vez de añadir a otra
            TextButton(
                onClick = onRemoveFromPlaylist,
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
            ) {
                Text("Eliminar de la lista")
            }
        } else {
            Text(
                "Añadir a playlist",
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
                        onClick = { onAddToPlaylist(pl.id) },
                        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
                    ) {
                        Text(pl.name)
                    }
                }
            }
        }
        Box(Modifier.padding(bottom = 24.dp)) {}
    }
}
