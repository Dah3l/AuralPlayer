// Actividad principal: permisos, navegación de pantallas y mini-reproductor
package com.aural.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aural.player.ui.components.PeekingSkull
import com.aural.player.ui.screens.DetailScreen
import com.aural.player.ui.screens.FAVORITES_ID
import com.aural.player.ui.screens.DAILY_PLAYLIST_ID
import com.aural.player.ui.screens.LibraryScreen
import com.aural.player.ui.screens.NowPlayingScreen
import com.aural.player.ui.screens.ThemesScreen
import com.aural.player.ui.theme.AuralTheme
import com.aural.player.ui.theme.AppTheme
import com.aural.player.data.SettingsStore
import com.aural.player.data.UpdateCheckResult
import com.aural.player.data.UpdateInfo
import com.aural.player.data.checkForUpdate
import com.aural.player.ui.components.UpdateAvailableDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aural.player.ui.components.SplashScreen
import com.aural.player.ui.screens.OnboardingScreen
import com.aural.player.data.OnboardingPrefs
import androidx.lifecycle.lifecycleScope
import com.aural.player.ui.theme.isChristmasWindow
import com.aural.player.ui.theme.isHalloweenWindow
import java.time.LocalDate

/** Pantallas navegables de la app (navegación simple por estado). */
private sealed class Screen {
    data object Library : Screen()
    data object Themes : Screen()
    data object Donate : Screen()
    data class Album(val title: String) : Screen()
    data class Folder(val path: String) : Screen()
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
        handleIslandIntent(intent)
        youTubeManager = com.aural.player.youtube.YouTubeManager(this)
        enableEdgeToEdge()
        requestNeededPermissions()

        // Auto-aplicado de los temas de temporada (una vez por arranque,
        // antes de pintar): Halloween (27-oct..1-nov) y Navidad (20-dic..26-dic).
        lifecycleScope.launch {
            val store = SettingsStore(this@MainActivity)
            val year = LocalDate.now().year
            store.runSeasonalAutoTheme(isHalloweenWindow(), year)
            store.runChristmasAutoTheme(isChristmasWindow(), year)
        }

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
                    AuralApp(viewModel, youTubeManager = youTubeManager, settingsStore = settingsStore,
                        onToggleIsland = { toggleDynamicIsland(viewModel, settingsStore) },
                        pendingIslandTab = pendingIslandTab,
                        onConsumeIslandTab = { pendingIslandTab = null },
                        pendingIslandHome = pendingIslandHome,
                        onConsumeIslandHome = { pendingIslandHome = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAudioSearchIntent(intent)
        handleIslandIntent(intent)
    }

    /** Abre la búsqueda por audio si el intent viene del tile de ajustes rápidos. */
    private fun handleAudioSearchIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(
                com.aural.player.AudioSearchTileService.EXTRA_OPEN_AUDIO_SEARCH, false
            ) == true
        ) {
            viewModel.openAudioSearch()
            pendingIslandHome = true
        }
    }

    /**
     * v1.0.7: intents de la isla dinámica. EXTRA_OPEN_YOUTUBE_TAB abre la
     * pestaña de Descargas (búsqueda de YouTube); EXTRA_OPEN_AUDIO_SEARCH
     * (arriba) abre el reconocimiento.
     */
    /** Pestaña que la isla dinámica pidió abrir (estado observable). */
    var pendingIslandTab by mutableStateOf<Int?>(null)
    // v1.0.7: la isla pide ir al Home (Biblioteca, sin overlays).
    var pendingIslandHome by mutableStateOf(false)
        private set

    private fun handleIslandIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_YOUTUBE_TAB, false) == true) {
            pendingIslandTab = 4 // pestaña Descargas
            pendingIslandHome = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_HOME, false) == true) {
            pendingIslandHome = true
        }
    }

    companion object {
        /** Extra para abrir la pestaña de Descargas (isla dinámica → YouTube). */
        const val EXTRA_OPEN_YOUTUBE_TAB = "com.aural.player.EXTRA_OPEN_YOUTUBE_TAB"
        const val EXTRA_OPEN_HOME = "com.aural.player.EXTRA_OPEN_HOME"
    }

    /**
     * v1.0.7: activa/desactiva la isla dinámica. Si falta el permiso de
     * overlay, abre los ajustes del sistema para concederlo; si ya está
     * concedido, alterna el ajuste.
     */
    private fun toggleDynamicIsland(
        viewModel: AuralViewModel,
        settingsStore: SettingsStore
    ) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // Pedir el permiso en los ajustes del sistema.
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            android.widget.Toast.makeText(
                this,
                "Activa \"Permitir mostrar sobre otras apps\" y vuelve a tocar Isla dinámica",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val currentlyEnabled = viewModel.islandEnabled.value
        viewModel.setIslandEnabled(!currentlyEnabled)
        android.widget.Toast.makeText(
            this,
            if (!currentlyEnabled) "Isla dinámica activada" else "Isla dinámica desactivada",
            android.widget.Toast.LENGTH_SHORT
        ).show()
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
    youTubeManager: com.aural.player.youtube.YouTubeManager?,
    settingsStore: SettingsStore,
    onToggleIsland: () -> Unit = {},
    pendingIslandTab: Int? = null,
    onConsumeIslandTab: () -> Unit = {},
    pendingIslandHome: Boolean = false,
    onConsumeIslandHome: () -> Unit = {}) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var showSplash by remember { mutableStateOf(true) }
    // El splash se oculta tras 1.8s (cortina mientras carga).
    val splashThemeName by settingsStore.appTheme.collectAsState(initial = "BOSQUE")
    val splashHalloween = splashThemeName == AppTheme.HALLOWEEN.name
    val splashChristmas = splashThemeName == AppTheme.CHRISTMAS.name
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800L)
        showSplash = false
    }
    // Estados elevados: sobreviven a la navegación para recordar posición
    val songsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val albumsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val artistsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val playlistsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    // El pager vive aquí para que al volver de un detalle siga en la misma pestaña
    val libraryPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 6 })
    val themesGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Estados de scroll de cada detalle, por destino: al volver atrás la
    // lista queda donde estaba (cada álbum, artista y playlist por separado)
    val detailListStates = remember {
        mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>()
    }
    fun detailListState(key: String) =
        detailListStates.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }
    var libraryTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // v1.0.7: la isla dinámica puede pedir abrir la pestaña de Descargas.
    androidx.compose.runtime.LaunchedEffect(pendingIslandTab) {
        pendingIslandTab?.let {
            libraryTab = it
            onConsumeIslandTab()
        }
    }
    var showNowPlaying by remember { mutableStateOf(false) }
    // v1.0.7: pantalla propia del ecualizador (overlay a pantalla completa).
    var showEqualizerScreen by remember { mutableStateOf(false) }
    // v1.0.7: pantalla de Ajustes (lo que antes vivía en el menú ⋮).
    var showSettingsScreen by remember { mutableStateOf(false) }
    // v1.0.7: la isla pide ir al Home: Biblioteca visible y sin overlays
    // (ni Now Playing, ni Ajustes, ni Ecualizador) para que la pestaña
    // pedida se abra directamente donde toca.
    androidx.compose.runtime.LaunchedEffect(pendingIslandHome) {
        if (pendingIslandHome) {
            screen = Screen.Library
            showNowPlaying = false
            showEqualizerScreen = false
            showSettingsScreen = false
            onConsumeIslandHome()
        }
    }
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

    // ---- Aviso de nueva versión: una vez al día, en silencio si falla ----
    val updateContext = LocalContext.current
    val updateScope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val dismissed = settingsStore.dismissedUpdateVersion.first()
        val lastCheck = settingsStore.lastUpdateCheckMs.first()
        val now = System.currentTimeMillis()
        if (now - lastCheck < 24L * 60L * 60L * 1000L) return@LaunchedEffect
        settingsStore.setLastUpdateCheckMs(now)
        when (val result = checkForUpdate(updateContext)) {
            is UpdateCheckResult.Available -> {
                if (result.info.version != dismissed) updateInfo = result.info
            }
            else -> {}
        }
    }

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
            // Atrás en la biblioteca -> doble confirmación para salir de la app
            var lastBackPress by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(0L)
            }
            val backContext = androidx.compose.ui.platform.LocalContext.current
            androidx.activity.compose.BackHandler(
                enabled = screen == Screen.Library && !showNowPlaying
            ) {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    (backContext as? android.app.Activity)?.finish()
                } else {
                    lastBackPress = now
                    android.widget.Toast.makeText(
                        backContext,
                        "Pulsa de nuevo para salir",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            when (val s = screen) {
                Screen.Library -> LibraryScreen(
                    viewModel = viewModel,
                    onPlayList = { songs, index, playlistId ->
                        viewModel.playSongs(songs, index, fromPlaylistId = playlistId)
                        showNowPlaying = true
                    },
                    onOpenAlbum = { screen = Screen.Album(it) },
                    onOpenFolder = { screen = Screen.Folder(it.path) },
                    onOpenArtist = { screen = Screen.Artist(it) },
                    onOpenPlaylist = { screen = Screen.Playlist(it) },
                    onOpenThemes = { screen = Screen.Themes },
                    onOpenEqualizer = { showEqualizerScreen = true },
                    onToggleIsland = onToggleIsland,
                    onOpenDonate = { screen = Screen.Donate },
                    onOpenSettings = { showSettingsScreen = true },
                    onSongMenu = { song ->
                        songMenuTarget = song
                        songMenuPlaylistId = null
                    },
                    songsListState = songsListState,
                    albumsGridState = albumsGridState,
                    artistsGridState = artistsGridState,
                    playlistsListState = playlistsListState,
                    pagerState = libraryPagerState,
                    selectedTab = libraryTab,
                    onTabSelected = { libraryTab = it },
                        youTubeManager = youTubeManager,
                        settingsStore = settingsStore,
                        isAmbientVisible = !showNowPlaying
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

                is Screen.Folder -> {
                    val folders by viewModel.foldersFlow.collectAsStateWithLifecycle()
                    val folder = folders.find { it.path == s.path }
                    if (folder == null) {
                        LaunchedEffect(Unit) { screen = Screen.Library }
                    } else {
                        DetailScreen(
                            viewModel = viewModel,
                            title = folder.displayName,
                            subtitle = "${folder.songs.size} ${if (folder.songs.size == 1) "canción" else "canciones"}",
                            songs = folder.songs,
                            coverUri = folder.songs.firstOrNull()?.albumArtUri,
                            listState = detailListState("folder:${s.path}"),
                            onBack = { screen = Screen.Library },
                            onSongMenu = { song ->
                                songMenuTarget = song
                                songMenuPlaylistId = null
                            }
                        )
                    }
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
                    val isDaily = s.id == DAILY_PLAYLIST_ID
                    val playlist = playlists.find { it.id == s.id }
                    val isDownloads = playlist?.name == "Descargas"
                    val songs = if (isFavorites) viewModel.favoriteSongs()
                    else if (isDaily) viewModel.dailyPlaylistSongs()
                    else playlist?.let { viewModel.songsOfPlaylist(it) } ?: emptyList()

                    DetailScreen(
                        viewModel = viewModel,
                        title = if (isFavorites) "Canciones favoritas"
                        else if (isDaily) "Playlist del día"
                        else playlist?.name ?: "",
                        subtitle = if (isFavorites) "Tus preferidas"
                        else if (isDaily) "Se renueva cada día"
                        else "Playlist",
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
                        // La Playlist del día es auto-generada: sus canciones
                        // no se pueden quitar a mano (rota sola cada día)
                        removableFromPlaylistId = if (isDaily) null else s.id,
                        // Solo las playlists creadas tienen foto editable;
                        // Favoritas, Descargas y la del día conservan su identidad
                        playlistCoverId = if (isFavorites || isDownloads || isDaily) null else s.id,
                        isDownloadsPlaylist = isDownloads,
                        onGoToDownloadsTab = { libraryTab = 4; screen = Screen.Library }
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

                Screen.Donate -> {
                    com.aural.player.ui.screens.DonateScreen(
                        onBack = { screen = Screen.Library }
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
            onOpenEqualizer = { showEqualizerScreen = true },
        )
    }
    // ---- v1.0.7: pantalla de Ajustes ----
    if (showSettingsScreen) {
        val islandOn by viewModel.islandEnabled.collectAsStateWithLifecycle()
        com.aural.player.ui.screens.SettingsScreen(
            onBack = { showSettingsScreen = false },
            onOpenEqualizer = { showEqualizerScreen = true },
            islandEnabled = islandOn,
            onToggleIsland = onToggleIsland,
            viewModel = viewModel
        )
    }
    // ---- v1.0.7: pantalla propia del ecualizador ----
    // Va DESPUÉS de Ajustes para que al abrirlo desde Ajustes quede encima.
    if (showEqualizerScreen) {
        com.aural.player.ui.screens.EqualizerScreen(
            onBack = { showEqualizerScreen = false }
        )
    }
    // ---- Splash de bienvenida ----
    if (showSplash) {
        SplashScreen(isHalloween = splashHalloween, isChristmas = splashChristmas)
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

    // ---- Calavera esporádica (Halloween): se asoma por un costado en
    // cualquier vista; no consume toques ----
    if (splashThemeName == AppTheme.HALLOWEEN.name && !showSplash) {
        PeekingSkull(Modifier.fillMaxSize())
    }

    // ---- Diálogo de nueva versión (chequeo automático al abrir) ----
    updateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            onDownload = { url ->
                updateInfo = null
                updateContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
            },
            onLater = {
                updateInfo = null
                updateScope.launch {
                    settingsStore.setDismissedUpdateVersion(info.version)
                }
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
