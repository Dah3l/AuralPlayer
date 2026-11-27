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
import com.aural.player.ui.screens.RECENTLY_ADDED_ID
import com.aural.player.ui.screens.LibraryScreen
import com.aural.player.ui.screens.OnlineSongMenuSheet
import com.aural.player.ui.screens.PodcastDetailScreen
import com.aural.player.ui.screens.RadioStationDetailScreen
import com.aural.player.data.PodcastShow
import com.aural.player.data.RadioStation
import com.aural.player.youtube.OnlineCollectionResult
import com.aural.player.youtube.YTResult
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
    data object Contact : Screen()
    data class Album(val title: String) : Screen()
    data class Folder(val path: String) : Screen()
    data class Artist(val name: String) : Screen()
    data class Playlist(val id: String) : Screen()
    data class OnlineCollection(val collection: OnlineCollectionResult) : Screen()
    data class RadioStationDetail(val station: RadioStation) : Screen()
    data class PodcastDetail(val show: PodcastShow) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: AuralViewModel by viewModels()
    private lateinit var youTubeManager: com.aural.player.youtube.YouTubeManager

    /** El permiso de audio ya se pidió al menos una vez (para detectar el "no volver a preguntar"). */
    private var audioPermissionRequestedBefore = false

    /** Launcher de permisos: audio + notificaciones según la versión. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Solo manda el permiso de AUDIO: el de notificaciones no debe
        // enmascarar una negativa de audio (antes se miraba con `any` y
        // la app creía tener permiso cuando no lo tenía).
        val audio = audioPermission()
        val granted = results[audio] == true
        // "No volver a preguntar": ya se pidió antes y el sistema ya no
        // muestra el diálogo (shouldShowRequestPermissionRationale = false).
        val permanentlyDenied = !granted && audioPermissionRequestedBefore &&
            !shouldShowRequestPermissionRationale(audio)
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }

    /**
     * Al volver a la app (p. ej. de Ajustes tras conceder el permiso a
     * mano), re-comprueba el permiso de audio y escanea si hacía falta.
     */
    override fun onResume() {
        super.onResume()
        if (!viewModel.library.value.permissionGranted &&
            isGranted(audioPermission()) &&
            viewModel.library.value.songs.isEmpty()
        ) {
            viewModel.onPermissionResult(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAudioSearchIntent(intent)
        handleIslandIntent(intent)
        handleAuralLinkIntent(intent)
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
                        onRequestAudioPermission = { requestAudioPermission() },
                        onOpenAppSettings = { openAppSettings() },
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
        handleAuralLinkIntent(intent)
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

    /**
     * v1.0.9: Aural link — un enlace compartido abre la app en Descargas
     * buscando la canción. Reusa requestYouTubeSearch: LibraryScreen cambia
     * sola a la pestaña y busca (el mismo mecanismo del reconocimiento).
     */
    private fun handleAuralLinkIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val query = com.aural.player.AuralLink.parseToQuery(intent.data) ?: return
        pendingIslandTab = 4 // pestaña Descargas
        pendingIslandHome = true
        viewModel.requestYouTubeSearch(query)
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
                "Activa \"Permitir mostrar sobre otras apps\" y vuelve a tocar Dynamic Aural",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val currentlyEnabled = viewModel.islandEnabled.value
        viewModel.setIslandEnabled(!currentlyEnabled)
        android.widget.Toast.makeText(
            this,
            if (!currentlyEnabled) "Dynamic Aural activado" else "Dynamic Aural desactivado",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Solicita READ_MEDIA_AUDIO en Android 13+ o READ_EXTERNAL_STORAGE
     * en versiones anteriores; añade POST_NOTIFICATIONS si hace falta.
     */
    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        val audioPermission = audioPermission()
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
            audioPermissionRequestedBefore = true
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    /** Permiso de audio según la versión: READ_MEDIA_AUDIO (13+) o READ_EXTERNAL_STORAGE. */
    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Vuelve a pedir solo el permiso de audio (botón "Dar permiso" de la pantalla de permisos). */
    private fun requestAudioPermission() {
        audioPermissionRequestedBefore = true
        permissionLauncher.launch(arrayOf(audioPermission()))
    }

    /** Abre los Ajustes de la app para conceder el permiso a mano (botón "Abrir Ajustes"). */
    private fun openAppSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
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
    // Pantalla de permisos: re-pedir el diálogo o abrir Ajustes.
    onRequestAudioPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    pendingIslandTab: Int? = null,
    onConsumeIslandTab: () -> Unit = {},
    pendingIslandHome: Boolean = false,
    onConsumeIslandHome: () -> Unit = {}) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }
    // El splash se oculta cuando la biblioteca termina de cargar, con
    // un mínimo de 600 ms para que el logo se lea y 1,8 s de tope:
    // antes era 1,8 s fijos aunque el escaneo terminara en 200 ms.
    val splashThemeName by settingsStore.appTheme.collectAsState(initial = "BOSQUE")
    val splashHalloween = splashThemeName == AppTheme.HALLOWEEN.name
    val splashChristmas = splashThemeName == AppTheme.CHRISTMAS.name
    val splashStartedAt = remember { System.currentTimeMillis() }
    androidx.compose.runtime.LaunchedEffect(library.isLoading) {
        if (!library.isLoading) {
            val elapsed = System.currentTimeMillis() - splashStartedAt
            if (elapsed < 600) kotlinx.coroutines.delay(600 - elapsed)
            showSplash = false
        }
    }
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
    val libraryPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 7 })
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
    // v1.1.0: pantalla "Tu Top 10" (overlay a pantalla completa).
    var showTop10Screen by remember { mutableStateOf(false) }
    // v1.0.7: la isla pide ir al Home: Biblioteca visible y sin overlays
    // (ni Now Playing, ni Ajustes, ni Ecualizador) para que la pestaña
    // pedida se abra directamente donde toca.
    androidx.compose.runtime.LaunchedEffect(pendingIslandHome) {
        if (pendingIslandHome) {
            screen = Screen.Library
            showNowPlaying = false
            showEqualizerScreen = false
            showSettingsScreen = false
            showTop10Screen = false
            onConsumeIslandHome()
        }
    }
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    // Playlist desde la que se abrió el menú de canción (null si fue desde
    // la biblioteca, un álbum o un artista): cambia "Añadir a playlist"
    // por "Eliminar de la lista"
    var songMenuPlaylistId by remember { mutableStateOf<String?>(null) }

    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
        val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
        val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playbackMessage by viewModel.playback.message.collectAsStateWithLifecycle()
    val messageContext = LocalContext.current
    LaunchedEffect(playbackMessage) {
        val text = playbackMessage ?: return@LaunchedEffect
        android.widget.Toast.makeText(
            messageContext, text, android.widget.Toast.LENGTH_SHORT
        ).show()
        viewModel.playback.clearMessage()
    }

    // Diagnóstico de la escalera agotada: modal con el detalle
    // completo por cliente y botón de copiar. Queda hasta que
    // el usuario lo cierra; no se pierde al saltar de canción.
    val diagnosticReport by viewModel.playback.diagnosticReport.collectAsStateWithLifecycle()
    diagnosticReport?.let { report ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Diagnóstico de reproducción") },
            text = {
                Text(
                    report,
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    android.widget.Toast.makeText(
                        messageContext,
                        "Diagnóstico copiado",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Copiar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.playback.clearDiagnosticReport() }) {
                    Text("Cerrar")
                }
            }
        )
    }

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
        containerColor = MaterialTheme.colorScheme.background
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
                    onOpenOnlineCollection = { screen = Screen.OnlineCollection(it) },
                    onOpenRadioStation = { screen = Screen.RadioStationDetail(it) },
                    onOpenPodcastShow = { screen = Screen.PodcastDetail(it) },
                    onOpenThemes = { screen = Screen.Themes },
                    onOpenEqualizer = { showEqualizerScreen = true },
                    onToggleIsland = onToggleIsland,
                    onOpenDonate = { screen = Screen.Donate },
                    onOpenContact = { screen = Screen.Contact },
                    onOpenSettings = { showSettingsScreen = true },
                    onOpenTop10 = { showTop10Screen = true },
                    onStartDjSession = {
                        if (viewModel.startDjSession()) showNowPlaying = true
                    },
                    onStopDjSession = { viewModel.stopDjSession() },
                    onRequestAudioPermission = onRequestAudioPermission,
                    onOpenAppSettings = onOpenAppSettings,
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
                    // remember: la rama se recompone con el ticker de 500 ms
                    // del reproductor; sin esto, filtrar+ordenar corría
                    // 2 veces por segundo.
                    val songs = remember(allSongs, s.title) {
                        allSongs.filter { it.album == s.title }.sortedBy { it.trackNumber }
                    }
                    DetailScreen(
                        viewModel = viewModel,
                        title = s.title,
                        subtitle = songs.firstOrNull()?.artist ?: "",
                        songs = songs,
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
                    // remember: igual que Álbum (ver arriba).
                    val songs = remember(allSongs, s.name) {
                        allSongs.filter { it.artist == s.name }
                    }
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
                    // Suscrito al flujo de canciones (como Álbum y Artista):
                    // al editar la metadata, la lista se actualiza sola
                    // sin salir y volver a entrar.
                    val allSongs by viewModel.songsFlow.collectAsStateWithLifecycle()
                    val dailyPlaylist by viewModel.dailyPlaylist.collectAsStateWithLifecycle()
                    val favoriteOrder by viewModel.favoriteOrder.collectAsStateWithLifecycle()
                    val isFavorites = s.id == FAVORITES_ID
                    val isDaily = s.id == DAILY_PLAYLIST_ID
                    val isRecent = s.id == RECENTLY_ADDED_ID
                    val playlist = playlists.find { it.id == s.id }
                    val isDownloads = playlist?.name == "Descargas"
                    // remember: favoriteSongs() construye dos mapas de toda
                    // la biblioteca y recentlyAddedSongs() ordena todo de
                    // nuevo; sin esto corrían 2 veces por segundo con el
                    // ticker del reproductor. Las claves cubren todo lo
                    // que alimenta el cálculo (favoritos, su orden, la
                    // del día, las playlists y la biblioteca).
                    val songs = remember(
                        allSongs, favorites, favoriteOrder, dailyPlaylist, playlists, s.id
                    ) {
                        if (isFavorites) viewModel.favoriteSongs()
                        else if (isDaily) viewModel.dailyPlaylistSongs()
                        else if (isRecent) viewModel.recentlyAddedSongs()
                        else playlist?.let { pl ->
                            val byId = allSongs.associateBy { it.id }
                            pl.songIds.mapNotNull { byId[it] }
                        } ?: emptyList()
                    }

                    DetailScreen(
                        viewModel = viewModel,
                        title = if (isFavorites) "Canciones favoritas"
                        else if (isDaily) "Playlist del día"
                        else if (isRecent) "Agregadas recientemente"
                        else playlist?.name ?: "",
                        subtitle = if (isFavorites) "Tus preferidas"
                        else if (isDaily) "Se renueva cada día"
                        else if (isRecent) "Las últimas que llegaron a tu biblioteca"
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
                        // La Playlist del día y Agregadas recientemente son
                        // auto-generadas: sus canciones no se pueden quitar
                        // a mano (rotan solas)
                        removableFromPlaylistId = if (isDaily || isRecent) null else s.id,
                        // v1.0.9: solo Agregadas recientemente lleva el
                        // selector de periodo.
                        showPeriodFilter = isRecent,
                        // Solo las playlists creadas tienen foto editable;
                        // Favoritas, Descargas y la del día conservan su identidad
                        playlistCoverId = if (isFavorites || isDownloads || isDaily) null else s.id,
                        isDownloadsPlaylist = isDownloads,
                        onGoToDownloadsTab = {
                            libraryTab = viewModel.libraryTabOrder.value.indexOf("downloads")
                            screen = Screen.Library
                        }
                    )
                }

                is Screen.OnlineCollection -> {
                    var results by remember(s.collection.url) {
                        mutableStateOf<List<YTResult>>(emptyList())
                    }
                    var songs by remember(s.collection.url) {
                        mutableStateOf<List<Song>>(emptyList())
                    }
                    var loading by remember(s.collection.url) { mutableStateOf(true) }
                    var menuTarget by remember(s.collection.url) {
                        mutableStateOf<YTResult?>(null)
                    }
                    LaunchedEffect(s.collection.url) {
                        loading = true
                        val fetched = youTubeManager?.fetchCollectionSongs(s.collection) ?: emptyList()
                        results = fetched
                        songs = fetched.map { viewModel.registerOnlineSong(it) }
                        loading = false
                        if (fetched.isEmpty()) {
                            viewModel.playback.showMessage("No se pudo cargar")
                        }
                    }
                    DetailScreen(
                        viewModel = viewModel,
                        title = s.collection.title,
                        subtitle = s.collection.subtitle,
                        songs = songs,
                        coverUri = s.collection.thumbnailUrl
                            ?: songs.firstOrNull()?.albumArtUri,
                        listState = detailListState("online:${s.collection.url}"),
                        onBack = { screen = Screen.Library },
                        onSongMenu = { song ->
                            menuTarget = results.firstOrNull {
                                it.videoId == song.onlineVideoId
                            }
                        },
                        isLoading = loading,
                        onPlaySongs = { list, index, shuffle ->
                            viewModel.playOnlineSongs(list, index, shuffle)
                        }
                    )
                    menuTarget?.let { result ->
                        OnlineSongMenuSheet(
                            result = result,
                            playlists = playlists,
                            viewModel = viewModel,
                            youTubeManager = youTubeManager,
                            onDismiss = { menuTarget = null }
                        )
                    }
                }

                is Screen.RadioStationDetail -> {
                    val stations by viewModel.radioStations.collectAsStateWithLifecycle()
                    val saved = stations.any { it.id == s.station.id }
                    var nowPlaying by remember(s.station.id) {
                        mutableStateOf<String?>(null)
                    }
                    LaunchedEffect(s.station.id) {
                        nowPlaying = viewModel.radioNowPlaying(s.station)
                    }
                    RadioStationDetailScreen(
                        station = s.station,
                        saved = saved,
                        nowPlaying = nowPlaying,
                        onBack = { screen = Screen.Library },
                        onPlay = { viewModel.playRadioStation(s.station) },
                        onToggleSaved = {
                            if (saved) viewModel.removeRadioStation(s.station)
                            else viewModel.addRadioStation(s.station)
                        }
                    )
                }

                is Screen.PodcastDetail -> {
                    val shows by viewModel.podcastShows.collectAsStateWithLifecycle()
                    val progress by viewModel.podcastProgress.collectAsStateWithLifecycle()
                    val saved = shows.any { it.feedUrl == s.show.feedUrl }
                    var episodes by remember(s.show.feedUrl) {
                        mutableStateOf<List<com.aural.player.data.PodcastEpisode>>(emptyList())
                    }
                    var loading by remember(s.show.feedUrl) { mutableStateOf(true) }
                    LaunchedEffect(s.show.feedUrl) {
                        loading = true
                        episodes = viewModel.podcastEpisodes(s.show)
                        loading = false
                    }
                    PodcastDetailScreen(
                        show = s.show,
                        episodes = episodes,
                        loading = loading,
                        progress = progress,
                        saved = saved,
                        onBack = { screen = Screen.Library },
                        onPlayShow = { viewModel.playPodcastShow(s.show) },
                        onPlayEpisode = { episode ->
                            viewModel.playPodcastEpisode(s.show, episode)
                        },
                        onToggleSaved = {
                            if (saved) viewModel.removePodcastShow(s.show)
                            else viewModel.addPodcastShow(s.show)
                        },
                        listState = detailListState("podcast:${s.show.feedUrl}")
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

                Screen.Contact -> {
                    com.aural.player.ui.screens.ContactScreen(
                        onBack = { screen = Screen.Library }
                    )
                }
            }

            // Mini-reproductor flotante sobre la lista: sin barra debajo,
            // la lista se ve por detrás y por los lados, solo él flota.
            AnimatedVisibility(
                visible = playerState.currentSong != null && !showNowPlaying,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MiniPlayer(
                    state = playerState,
                    onOpen = { showNowPlaying = true },
                    onTogglePlay = viewModel.playback::togglePlayPause,
                    customCoverUri = playerState.currentSong?.let { customCovers[it.id] },
                    customMetadata = playerState.currentSong?.let { customMetadata[it.id] }
                )
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
        val islandLook by viewModel.islandAppearance.collectAsStateWithLifecycle()
        val cometOn by viewModel.cometEnabled.collectAsStateWithLifecycle()
        val lightColor by viewModel.auralLightColor.collectAsStateWithLifecycle()
        val crossfadeOn by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
        val crossfadeSecs by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
        com.aural.player.ui.screens.SettingsScreen(
            onBack = { showSettingsScreen = false },
            onOpenEqualizer = { showEqualizerScreen = true },
            crossfadeEnabled = crossfadeOn,
            onToggleCrossfade = { viewModel.setCrossfadeEnabled(!crossfadeOn) },
            crossfadeSeconds = crossfadeSecs,
            onCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
            islandEnabled = islandOn,
            onToggleIsland = onToggleIsland,
            islandAppearance = islandLook,
            onIslandAppearance = { viewModel.setIslandAppearance(it) },
            cometEnabled = cometOn,
            onToggleComet = { viewModel.setCometEnabled(!cometOn) },
            auralLightColor = lightColor,
            onLightColorChange = { viewModel.setAuralLightColor(it) },
            viewModel = viewModel
        )
    }
    // ---- v1.1.0: pantalla "Tu Top 10" ----
    if (showTop10Screen) {
        com.aural.player.ui.screens.Top10Screen(
            onBack = { showTop10Screen = false },
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
            onShareAuralLink = {
                val link = com.aural.player.AuralLink.shareText(song, customMetadata[song.id])
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, link)
                }
                updateContext.startActivity(
                    android.content.Intent.createChooser(shareIntent, "Compartir Aural link")
                )
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
    onShareAuralLink: () -> Unit = {},
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
        TextButton(onClick = onShareAuralLink, modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)) {
            Text("Compartir Aural link")
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
