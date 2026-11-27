// Biblioteca principal con pestañas: Canciones, Artistas, Álbumes y Playlists
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onOpenOnlineCollection: (com.aural.player.youtube.OnlineCollectionResult) -> Unit = {},
    onOpenRadioStation: (com.aural.player.data.RadioStation) -> Unit = {},
    onOpenPodcastShow: (com.aural.player.data.PodcastShow) -> Unit = {},
    onOpenPodcastContinue: () -> Unit = {},
    onOpenThemes: () -> Unit,
    onSongMenu: (song: Song) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onToggleIsland: () -> Unit = {},
    onOpenDonate: () -> Unit = {},
    onOpenContact: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTop10: () -> Unit = {},
    onStartDjSession: () -> Unit = {},
    onStopDjSession: () -> Unit = {},
    // Pantalla de permisos: re-pedir el diálogo o abrir Ajustes.
    onRequestAudioPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    songsListState: androidx.compose.foundation.lazy.LazyListState,
    albumsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    artistsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    playlistsListState: androidx.compose.foundation.lazy.LazyListState,
    pagerState: androidx.compose.foundation.pager.PagerState =
        // Respaldo coherente con el orden por defecto (9 pestañas con
        // Emisoras y Podcasts); MainActivity pasa el pager real con
        // pageCount = tamaño del orden elegido por el usuario.
        rememberPagerState(
            pageCount = { com.aural.player.data.defaultTabOrder.size }
        ),
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    youTubeManager: com.aural.player.youtube.YouTubeManager? = null,
    settingsStore: SettingsStore,
    // El ambiente Halloween solo corre cuando el Home está visible de verdad
    // (con el Now Playing abierto, el Home queda tapado debajo).
    isAmbientVisible: Boolean = true
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    // Solo el id de la canción actual y si suena: la biblioteca NO
    // colecciona playerState entero para no recomponer cada 500 ms
    // con el ticker de posición (el MiniPlayer y el NP sí lo necesitan).
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isDjSessionActive by viewModel.playback.endlessDjActive.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    val songs by viewModel.filteredSongsFlow.collectAsStateWithLifecycle()

    // ---- Perfil: nombre y cumpleaños (saludo personalizado) ----
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val birthday by viewModel.birthday.collectAsStateWithLifecycle()
    val seenFeatures by viewModel.seenFeatures.collectAsStateWithLifecycle()

    // ---- Modales de inicio: de uno en uno, sin solaparse ----
    // Orden: cumpleaños -> bienvenida del perfil -> novedades.
    val context = LocalContext.current
    var birthdayPending by remember { mutableStateOf(false) }
    var welcomePending by remember { mutableStateOf(false) }
    var newsPending by remember { mutableStateOf(false) }
    var newsVersion by remember { mutableStateOf("") }

    // Modal de cumpleaños: una vez al día, si hoy es la fecha guardada.
    LaunchedEffect(birthday, userName) {
        val md = birthday
        val name = userName
        if (!md.isNullOrBlank() && !name.isNullOrBlank() && isBirthdayToday(md)) {
            val today = todayDateKey()
            if (viewModel.birthdayShownDate.value != today) {
                viewModel.markBirthdayShown(today)
                birthdayPending = true
            }
        }
    }

    // Bienvenida del perfil + novedades: tras un retardo que deja que
    // DataStore cargue los valores reales.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        if (!viewModel.profileAsked.value) welcomePending = true
        // Novedades: se muestran cuando la versión guardada es distinta
        // a la actual (incluida la primera vez, que no hay nada guardado).
        // Al cerrar el modal se guarda la versión y no vuelve a salir.
        val current = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        if (current != null && viewModel.lastNewsVersion.value != current) {
            newsVersion = current
            newsPending = true
        }
    }
    val artists by viewModel.artistsFlow.collectAsStateWithLifecycle()
    val albums by viewModel.albumsFlow.collectAsStateWithLifecycle()
    val folders by viewModel.foldersFlow.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val radioStations by viewModel.radioStations.collectAsStateWithLifecycle()
    val podcastShows by viewModel.podcastShows.collectAsStateWithLifecycle()
    val podcastProgress by viewModel.podcastProgress.collectAsStateWithLifecycle()
    val radioSearchResults by viewModel.radioSearchResults.collectAsStateWithLifecycle()
    val radioSearching by viewModel.radioSearching.collectAsStateWithLifecycle()
    val radioSearchQuery by viewModel.radioSearchQuery.collectAsStateWithLifecycle()
    val podcastSearchResults by viewModel.podcastSearchResults.collectAsStateWithLifecycle()
    val podcastSearching by viewModel.podcastSearching.collectAsStateWithLifecycle()
    val podcastSearchQuery by viewModel.podcastSearchQuery.collectAsStateWithLifecycle()
    val radioSuggestions by viewModel.radioSuggestions.collectAsStateWithLifecycle()
    val radioSuggestionsLoading by viewModel.radioSuggestionsLoading
        .collectAsStateWithLifecycle()
    val podcastSuggestions by viewModel.podcastSuggestions
        .collectAsStateWithLifecycle()
    val podcastSuggestionsLoading by viewModel.podcastSuggestionsLoading
        .collectAsStateWithLifecycle()
    val savedOnlinePlaylists by viewModel.savedOnlinePlaylists
        .collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }
    val dailyPlaylist by viewModel.dailyPlaylist.collectAsStateWithLifecycle()
    val favoriteOrder by viewModel.favoriteOrder.collectAsStateWithLifecycle()
    val playlistCovers by viewModel.playlistCovers.collectAsStateWithLifecycle()
    val playCounts by viewModel.playlistPlayCounts.collectAsStateWithLifecycle()
    val downloadsListState = rememberLazyListState()
    val radioListState = rememberLazyListState()
    val podcastsListState = rememberLazyListState()
    val pillsListState = rememberLazyListState()
    // v1.0.8: orden de pestañas elegido por el usuario (claves estables).
    val tabOrder by viewModel.libraryTabOrder.collectAsStateWithLifecycle()
    val tabs = tabOrder.map { tabLabel(it) }
    // Pestaña activa acotada al rango válido: pills y pager nunca
    // reciben un índice fuera del orden actual (p. ej. tras reordenar).
    val safeSelectedTab = selectedTab.coerceIn(0, tabOrder.lastIndex.coerceAtLeast(0))
    var showTabCustomizer by remember { mutableStateOf(false) }
    // Artistas favoritos del Inicio.
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    // Escuchadas recientemente y estadísticas de la semana (Inicio).
    val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
    val weekStats by viewModel.weekStats.collectAsStateWithLifecycle()
    // La canción más escuchada de la semana, resuelta a la biblioteca
    // completa (la lista filtrada por la búsqueda podría no tenerla).
    val weekTopSong = remember(weekStats, library) {
        weekStats.topSongs.firstOrNull()?.let { top ->
            library.songs.firstOrNull { it.id == top.songId }
        }
    }

    // La fila de pills se desplaza sola para mostrar la pestaña activa
    LaunchedEffect(safeSelectedTab, tabOrder) {
        val layout = pillsListState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == safeSelectedTab }
        val fullyVisible = item != null &&
            item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) pillsListState.animateScrollToItem(safeSelectedTab)
        // Al entrar al Inicio, sus secciones se actualizan.
        if (tabOrder.getOrNull(safeSelectedTab) == "home") {
            viewModel.refreshTopArtists()
            viewModel.refreshRecentSongs()
            viewModel.refreshWeekStats()
        }
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
    // Set estable: toSet() en cada recomposición creaba una instancia
    // nueva y obligaba a SongsTab a recomponer sus filas; derivedStateOf
    // solo recalcula cuando cambia la selección de verdad.
    val selectedIdsSet by remember { derivedStateOf { selectedIds.toSet() } }
    // Cambiar de pestaña cancela la selección: no debe quedar activa
    // sobre otra vista.
    LaunchedEffect(selectedTab) {
        if (selectionMode) {
            selectionMode = false
            selectedIds.clear()
        }
    }
    // v1.0.9: eliminar las canciones seleccionadas (borra los archivos).
    var showDeleteSongsConfirm by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    // v1.0.8: crear playlist nueva desde el picker y agregarle la selección.
    var pendingIdsForNewPlaylist by remember { mutableStateOf<List<Long>?>(null) }
    var idsBeforePlaylistCreate by remember { mutableStateOf<Set<String>?>(null) }
    // v1.0.7: ocultar carpeta (long-press en Carpetas).
    var folderHideTarget by remember { mutableStateOf<com.aural.player.data.Folder?>(null) }
    var manualUpdateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    // v1.0.9: confirmación del sistema para borrar archivos (API 30+).
    // Al volver con OK se confirma la eliminación en el ViewModel.
    val deleteSongsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.confirmDelete(pendingDeleteIds)
            android.widget.Toast.makeText(
                appContext,
                if (pendingDeleteIds.size == 1) "Canción eliminada"
                else "${pendingDeleteIds.size} canciones eliminadas",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        pendingDeleteIds = emptyList()
        selectionMode = false
        selectedIds.clear()
    }
    // ---- Búsqueda unificada 1.3.0: local + online ----
    val onlineResultsFlow = remember(youTubeManager) {
        youTubeManager?.onlineResults
            ?: kotlinx.coroutines.flow.MutableStateFlow(
                com.aural.player.youtube.OnlineSearchResults()
            )
    }
    val onlineSearchingFlow = remember(youTubeManager) {
        youTubeManager?.onlineSearching
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }
    val onlineErrorFlow = remember(youTubeManager) {
        youTubeManager?.onlineError
            ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
    val onlineResults by onlineResultsFlow.collectAsStateWithLifecycle()
    val onlineSearching by onlineSearchingFlow.collectAsStateWithLifecycle()
    val onlineError by onlineErrorFlow.collectAsStateWithLifecycle()
    val streamingNoticePending by viewModel.streamingNoticePending.collectAsStateWithLifecycle()
    var onlineMenuTarget by remember {
        mutableStateOf<com.aural.player.youtube.YTResult?>(null)
    }
    var loadingCollection by remember { mutableStateOf(false) }
    // Selector de fuente de búsqueda: Locales por defecto, Online opcional.
    var searchOnline by remember { mutableStateOf(false) }
    var onlineSongsUi by remember { mutableStateOf<List<Song>>(emptyList()) }
    // La búsqueda online está "pendiente" hasta que los
    // resultados correspondan a la consulta actual y la
    // búsqueda haya terminado: en ese tramo se muestran
    // skeletons, nunca el estado vacío (antes el aviso de
    // "sin resultados" salía y luego aparecía el contenido).
    val onlineQuery = library.query.trim()
    val onlinePending = searchOnline && onlineQuery.isNotBlank() &&
        viewModel.hasInternet() &&
        (onlineSearching || onlineResults.query != onlineQuery)
    val onlineSongsPending = onlinePending ||
        (onlineResults.query == onlineQuery &&
            onlineResults.songs.isNotEmpty() && onlineSongsUi.isEmpty())
    LaunchedEffect(onlineResults.songs) {
        onlineSongsUi = onlineResults.songs.map { viewModel.registerOnlineSong(it) }
    }
    LaunchedEffect(library.query, searchOnline) {
        val query = library.query.trim()
        if (query.isBlank() || !searchOnline) {
            youTubeManager?.clearOnlineResults()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(450)
        if (viewModel.hasInternet()) {
            youTubeManager?.searchUnified(query)
        } else {
            youTubeManager?.clearOnlineResults()
        }
    }
    // Sugerencias online sin texto: se calculan desde la
    // escucha del usuario (Top, recientes y favoritos) la
    // primera vez que se entra al lado Online.
    var onlineSuggestions by remember {
        mutableStateOf<com.aural.player.youtube.OnlineSearchResults?>(null)
    }
    var onlineSuggestionsLoading by remember { mutableStateOf(false) }
    var onlineSuggestionsSongsUi by remember {
        mutableStateOf<List<Song>>(emptyList())
    }
    LaunchedEffect(onlineSuggestions?.songs) {
        onlineSuggestionsSongsUi = onlineSuggestions?.songs
            ?.map { viewModel.registerOnlineSong(it) } ?: emptyList()
    }
    LaunchedEffect(searchOnline, library.query, safeSelectedTab) {
        if (!searchOnline || library.query.isNotBlank()) return@LaunchedEffect
        val tabKey = tabOrder.getOrNull(safeSelectedTab)
        if (tabKey !in setOf("songs", "artists", "albums", "playlists")) {
            return@LaunchedEffect
        }
        if (!viewModel.hasInternet()) return@LaunchedEffect
        if (onlineSuggestions != null || onlineSuggestionsLoading) {
            return@LaunchedEffect
        }
        val seeds = (
            topArtists.map { it.name } +
                recentSongs.map { it.artist } +
                viewModel.favoriteSongs().map { it.artist }
            ).map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        if (seeds.isEmpty()) {
            onlineSuggestions =
                com.aural.player.youtube.OnlineSearchResults()
            return@LaunchedEffect
        }
        onlineSuggestionsLoading = true
        onlineSuggestions = youTubeManager?.searchSuggestions(seeds)
            ?: com.aural.player.youtube.OnlineSearchResults()
        onlineSuggestionsLoading = false
    }
    // Descubrimiento de Emisoras/Podcasts: en Online con texto
    // busca contra Radio Browser / el directorio; en Online
    // sin texto carga sugerencias; en Locales se ve la
    // colección guardada (filtrada si hay texto).
    LaunchedEffect(library.query, safeSelectedTab, tabOrder, searchOnline) {
        val query = library.query.trim()
        val tabKey = tabOrder.getOrNull(safeSelectedTab)
        if (!searchOnline || (tabKey != "radio" && tabKey != "podcasts")) {
            viewModel.clearCollectionSearch()
            return@LaunchedEffect
        }
        if (!viewModel.hasInternet()) {
            viewModel.clearCollectionSearch()
            return@LaunchedEffect
        }
        if (query.isBlank()) {
            if (tabKey == "radio") viewModel.loadRadioSuggestions()
            else viewModel.loadPodcastSuggestions()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(450)
        if (tabKey == "radio") viewModel.searchRadio(query)
        else viewModel.searchPodcasts(query)
    }
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

    // Reproduce un álbum/playlist/artista online desde su pestaña.
    val playOnlineCollection: (com.aural.player.youtube.OnlineCollectionResult) -> Unit =
        { collection ->
            refreshScope.launch {
                loadingCollection = true
                val results = youTubeManager?.fetchCollectionSongs(collection)
                    ?: emptyList()
                loadingCollection = false
                if (results.isEmpty()) {
                    viewModel.playback.showMessage("No se pudo cargar")
                } else {
                    val registered = results.map {
                        viewModel.registerOnlineSong(it)
                    }
                    viewModel.playOnlineSongs(registered, 0)
                }
            }
        }

    // "Para ti": la Playlist del día VA PRIMERA (si tiene canciones);
    // después, las más sonadas. Siempre 2 tarjetas. Sin datos todavía:
    // Favoritas y Descargas.
    val downloadsPlaylist = playlists.firstOrNull { it.name == "Descargas" }
    // remember: "Para ti" se recalcula solo cuando cambia lo que la
    // alimenta, no en cada recomposición. favoriteSongs() construye dos
    // mapas de toda la biblioteca y recentlyAddedSongs() ordena todo de
    // nuevo: sin remember eso corría 2 veces por segundo con música.
    val topPlaylists: List<TopPlaylist> = remember(
        playlists, playCounts, playlistCovers, customCovers,
        favorites, favoriteOrder, dailyPlaylist, songs
    ) {
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
                text = greetingForHour(userName, birthday),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Búsqueda siempre visible: superficie propia (surfaceContainerHigh) ----
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (library.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Borrar búsqueda",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Rayita divisoria entre la X y el micrófono.
                            VerticalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        IconButton(onClick = { viewModel.openAudioSearch() }) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = "Identificar canción",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { searchFocused = it.isFocused }
            )
        }

        // ---- Pills de filtros: tira agrupada sobre surfaceContainerHigh ----
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
        LazyRow(
            state = pillsListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items(tabs.size) { i ->
                val selected = safeSelectedTab == i
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
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
                        .background(Color.Transparent)
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
        } // Cierra la tira de pestañas agrupada

        // Selector Locales/Online fijo: siempre visible en
        // Canciones, Artistas, Álbumes, Playlists, Emisoras y
        // Podcasts, haya o no texto. Inicio, Descargas y
        // Carpetas no tienen subpestañas.
        val currentTabKey = tabOrder.getOrNull(safeSelectedTab)
        if (currentTabKey in setOf(
                "songs", "artists", "albums", "playlists", "radio", "podcasts"
            )
        ) {
            SearchSourceSelector(
                online = searchOnline,
                onChange = { searchOnline = it }
            )
            if (searchOnline) {
                OnlineVpnNoticeBanner(viewModel)
            }
        }

        // ---- Contenido por pestaña (con pull-to-refresh) ----
        // Lámina surfaceContainerLowest con esquinas superiores redondeadas:
        // cada lista vive sobre su propia superficie, separada de la cabecera.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .pullRefresh(pullState)
        ) {
        when {
            library.isLoading -> BoxCentered {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            !library.permissionGranted && library.songs.isEmpty() -> PermissionRequiredState(
                permanentlyDenied = library.permissionPermanentlyDenied,
                onRequestPermission = onRequestAudioPermission,
                onOpenSettings = onOpenAppSettings
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
                        if (safeSelectedTab != pagerState.currentPage) {
                            onTabSelected(pagerState.currentPage)
                        }
                    } else if (pagerState.currentPage == pagerTarget) {
                        pagerTarget = null
                    }
                }
                LaunchedEffect(safeSelectedTab) {
                    try {
                        if (pagerState.currentPage != safeSelectedTab) {
                            if (pagerReady) {
                                pagerTarget = safeSelectedTab
                                try {
                                    pagerState.animateScrollToPage(safeSelectedTab)
                                } finally {
                                    pagerTarget = null
                                }
                            } else {
                                pagerState.scrollToPage(safeSelectedTab)
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
                    when (tabOrder.getOrNull(page)) {
                        "home" -> HomeTab(
                            topPlaylists = topPlaylists,
                            onOpenPlaylist = onOpenPlaylist,
                            onPlayPlaylist = { id ->
                                val toPlay = when (id) {
                                    FAVORITES_ID -> viewModel.favoriteSongs()
                                    DAILY_PLAYLIST_ID -> viewModel.dailyPlaylistSongs()
                                    RECENTLY_ADDED_ID -> viewModel.recentlyAddedSongs()
                                    else -> playlists.firstOrNull { it.id == id }?.let { viewModel.songsOfPlaylist(it) }
                                        ?: emptyList()
                                }
                                if (toPlay.isNotEmpty()) onPlayList(toPlay, 0, id)
                            },
                            onSeeAllPlaylists = { onTabSelected(tabOrder.indexOf("playlists")) },
                            onDjSession = onStartDjSession,
                            isDjSessionActive = isDjSessionActive,
                            onStopDjSession = onStopDjSession,
                            topArtists = topArtists,
                            onOpenArtist = onOpenArtist,
                            songs = songs,
                            customCovers = customCovers,
                            recentSongs = recentSongs,
                            onPlayRecent = { index -> onPlayList(recentSongs, index, null) },
                            weekStats = weekStats,
                            weekTopSong = weekTopSong,
                            onPlayWeekTopSong = {
                                weekTopSong?.let { onPlayList(listOf(it), 0, null) }
                            },
                            onShuffleAll = { viewModel.playAllShuffled() },
                            onOpenFavorites = { onOpenPlaylist(FAVORITES_ID) },
                            onOpenTop10 = onOpenTop10,
                            onOpenDownloads = { onTabSelected(tabOrder.indexOf("downloads")) }
                        )

                        "songs" -> if (searchOnline) {
                            val showingSuggestions = library.query.isBlank()
                            val songsUi = if (showingSuggestions) {
                                onlineSuggestionsSongsUi
                            } else {
                                onlineSongsUi
                            }
                            val suggestionsPending = showingSuggestions &&
                                (onlineSuggestionsLoading ||
                                    (onlineSuggestions?.songs?.isNotEmpty() == true &&
                                        onlineSuggestionsSongsUi.isEmpty()))
                            Column(Modifier.fillMaxSize()) {
                                OnlineSearchNotice(
                                    hasInternet = viewModel.hasInternet(),
                                    onlineError = onlineError
                                )
                                if (((onlineSongsPending || suggestionsPending) ||
                                        loadingCollection) &&
                                    songsUi.isEmpty()
                                ) {
                                    OnlineSkeletonList()
                                } else if (songsUi.isEmpty()) {
                                    OnlineEmptyState()
                                } else {
                                    SongsTab(
                                        songs = songsUi,
                                        favorites = favorites,
                                        currentId = currentSongId,
                                        isPlaying = isPlaying,
                                        onPlay = { song ->
                                            viewModel.playOnlineSongs(
                                                songsUi,
                                                songsUi.indexOfFirst {
                                                    it.id == song.id
                                                }.coerceAtLeast(0)
                                            )
                                        },
                                        onFavorite = viewModel::toggleFavorite,
                                        onMenu = { song ->
                                            onlineMenuTarget = (
                                                onlineResults.songs +
                                                    (onlineSuggestions?.songs
                                                        ?: emptyList())
                                                ).firstOrNull {
                                                    it.videoId == song.onlineVideoId
                                                }
                                        },
                                        listState = songsListState,
                                        customCovers = customCovers,
                                        customMetadata = customMetadata,
                                        emptyText = ONLINE_EMPTY_TEXT
                                    )
                                }
                            }
                        } else SongsTab(
                            songs = songs,
                            favorites = favorites,
                            currentId = currentSongId,
                            isPlaying = isPlaying,
                            onPlay = { song ->
                                val list = songs
                                onPlayList(list, list.indexOfFirst { it.id == song.id }, null)
                            },
                            onFavorite = viewModel::toggleFavorite,
                            onMenu = onSongMenu,
                            listState = songsListState,
                            customCovers = customCovers,
                            customMetadata = customMetadata,
                            selectionMode = selectionMode,
                            selectedIds = selectedIdsSet,
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

                        "artists" -> if (searchOnline) {
                            val artistsUi = if (library.query.isBlank()) {
                                onlineSuggestions?.artists ?: emptyList()
                            } else {
                                onlineResults.artists
                            }
                            Column(Modifier.fillMaxSize()) {
                                OnlineSearchNotice(
                                    hasInternet = viewModel.hasInternet(),
                                    onlineError = onlineError
                                )
                                if (((onlinePending || onlineSuggestionsLoading) ||
                                        loadingCollection) &&
                                    artistsUi.isEmpty()
                                ) {
                                    OnlineSkeletonGrid(110.dp)
                                } else {
                                    OnlineArtistsTab(
                                        artists = artistsUi,
                                        onOpen = onOpenOnlineCollection,
                                        gridState = artistsGridState
                                    )
                                }
                            }
                        } else ArtistsTab(
                            artists = artists,
                            onOpen = { name -> onOpenArtist(name) },
                            gridState = artistsGridState
                        )

                        "albums" -> if (searchOnline) {
                            val albumsUi = if (library.query.isBlank()) {
                                onlineSuggestions?.albums ?: emptyList()
                            } else {
                                onlineResults.albums
                            }
                            Column(Modifier.fillMaxSize()) {
                                OnlineSearchNotice(
                                    hasInternet = viewModel.hasInternet(),
                                    onlineError = onlineError
                                )
                                if (((onlinePending || onlineSuggestionsLoading) ||
                                        loadingCollection) &&
                                    albumsUi.isEmpty()
                                ) {
                                    OnlineSkeletonGrid(150.dp)
                                } else {
                                    OnlineAlbumsTab(
                                        albums = albumsUi,
                                        onOpen = onOpenOnlineCollection,
                                        gridState = albumsGridState
                                    )
                                }
                            }
                        } else AlbumsTab(
                            albums = albums,
                            onOpen = { title -> onOpenAlbum(title) },
                            gridState = albumsGridState
                        )

                        "playlists" -> if (searchOnline) {
                            val playlistsUi = if (library.query.isBlank()) {
                                onlineSuggestions?.playlists ?: emptyList()
                            } else {
                                onlineResults.playlists
                            }
                            Column(Modifier.fillMaxSize()) {
                                OnlineSearchNotice(
                                    hasInternet = viewModel.hasInternet(),
                                    onlineError = onlineError
                                )
                                if (((onlinePending || onlineSuggestionsLoading) ||
                                        loadingCollection) &&
                                    playlistsUi.isEmpty()
                                ) {
                                    OnlineSkeletonList()
                                } else {
                                    OnlinePlaylistsTab(
                                        playlists = playlistsUi,
                                        onOpen = onOpenOnlineCollection,
                                        onPlay = playOnlineCollection,
                                        listState = playlistsListState,
                                        savedUrls = savedOnlinePlaylists
                                            .map { it.url }.toSet(),
                                        onToggleSaved = {
                                            viewModel.toggleSavedOnlinePlaylist(it)
                                        }
                                    )
                                }
                            }
                        } else {
                            val visiblePlaylists = if (library.query.isBlank()) {
                                playlists
                            } else {
                                playlists.filter {
                                    it.name.contains(library.query, true)
                                }
                            }
                            if (library.query.isNotBlank() && visiblePlaylists.isEmpty()) {
                                EmptyState("Sin resultados para tu búsqueda")
                            } else {
                            val downloadsPl = playlists.firstOrNull { it.name == "Descargas" }
                            val dailySongs = viewModel.dailyPlaylistSongs()
                            val recentSongs = viewModel.recentlyAddedSongs()
                            PlaylistsTab(
                                playlists = visiblePlaylists,
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
                                recentSongCount = recentSongs.size,
                                recentCoverUri = recentSongs.firstOrNull()?.let { song ->
                                    customCovers[song.id] ?: song.albumArtUri
                                },
                                onOpenRecent = { onOpenPlaylist(RECENTLY_ADDED_ID) },
                                listState = playlistsListState,
                                playlistCovers = playlistCovers,
                                firstSongCover = { pl ->
                                    viewModel.songsOfPlaylist(pl).firstOrNull()?.let { song ->
                                        customCovers[song.id] ?: song.albumArtUri
                                    }
                                },
                                onRemoveCover = { pl -> viewModel.setPlaylistCover(pl.id, "") },
                                showFixedEntries = library.query.isBlank(),
                                savedOnlinePlaylists = if (
                                    library.query.isBlank()
                                ) {
                                    savedOnlinePlaylists
                                } else {
                                    savedOnlinePlaylists.filter {
                                        it.title.contains(library.query, true)
                                    }
                                },
                                onOpenSavedOnlinePlaylist = { saved ->
                                    onOpenOnlineCollection(
                                        com.aural.player.youtube.OnlineCollectionResult(
                                            kind = com.aural.player.youtube
                                                .OnlineCollectionKind.PLAYLIST,
                                            url = saved.url,
                                            title = saved.title,
                                            subtitle = saved.subtitle,
                                            thumbnailUrl = saved.thumbnailUrl
                                        )
                                    )
                                },
                                onPlaySavedOnlinePlaylist = { saved ->
                                    playOnlineCollection(
                                        com.aural.player.youtube.OnlineCollectionResult(
                                            kind = com.aural.player.youtube
                                                .OnlineCollectionKind.PLAYLIST,
                                            url = saved.url,
                                            title = saved.title,
                                            subtitle = saved.subtitle,
                                            thumbnailUrl = saved.thumbnailUrl
                                        )
                                    )
                                },
                                onRemoveSavedOnlinePlaylist = { saved ->
                                    viewModel.removeSavedOnlinePlaylist(saved.url)
                                }
                            )
                            }
                        }
                    "radio" -> if (searchOnline) {
                            val showingSuggestions = library.query.isBlank()
                            RadioSearchTab(
                                stations = if (showingSuggestions) {
                                    radioSuggestions
                                } else {
                                    radioSearchResults
                                },
                                savedIds = radioStations.map { it.id }.toSet(),
                                searching = if (showingSuggestions) {
                                    radioSuggestionsLoading
                                } else {
                                    radioSearching
                                },
                                pending = !showingSuggestions &&
                                    radioSearchQuery != library.query.trim(),
                                hasInternet = viewModel.hasInternet(),
                                onPlay = { viewModel.playRadioStation(it) },
                                onToggleSaved = { station ->
                                    if (radioStations.any { it.id == station.id }) {
                                        viewModel.removeRadioStation(station)
                                    } else {
                                        viewModel.addRadioStation(station)
                                    }
                                },
                                listState = radioListState
                            )
                        } else RadioTab(
                            stations = if (library.query.isBlank()) {
                                radioStations
                            } else {
                                radioStations.filter {
                                    it.name.contains(library.query, true) ||
                                        it.country.contains(library.query, true) ||
                                        it.genre.contains(library.query, true)
                                }
                            },
                            onPlay = { viewModel.playRadioStation(it) },
                            onRemove = { viewModel.removeRadioStation(it) },
                            onSearch = {
                                onTabSelected(tabOrder.indexOf("radio"))
                                searchFocusRequester.requestFocus()
                            },
                            listState = radioListState,
                            onOpenDetail = onOpenRadioStation
                        )
                    "podcasts" -> if (searchOnline) {
                            val showingSuggestions = library.query.isBlank()
                            PodcastSearchTab(
                                shows = if (showingSuggestions) {
                                    podcastSuggestions
                                } else {
                                    podcastSearchResults
                                },
                                savedFeedUrls = podcastShows.map { it.feedUrl }.toSet(),
                                searching = if (showingSuggestions) {
                                    podcastSuggestionsLoading
                                } else {
                                    podcastSearching
                                },
                                pending = !showingSuggestions &&
                                    podcastSearchQuery != library.query.trim(),
                                hasInternet = viewModel.hasInternet(),
                                onOpen = onOpenPodcastShow,
                                onPlay = { viewModel.playPodcastShow(it) },
                                onToggleSaved = { show ->
                                    if (podcastShows.any { it.feedUrl == show.feedUrl }) {
                                        viewModel.removePodcastShow(show)
                                    } else {
                                        viewModel.addPodcastShow(show)
                                    }
                                },
                                listState = podcastsListState
                            )
                        } else PodcastsTab(
                            shows = if (library.query.isBlank()) {
                                podcastShows
                            } else {
                                podcastShows.filter {
                                    it.title.contains(library.query, true) ||
                                        it.author.contains(library.query, true)
                                }
                            },
                            progress = podcastProgress,
                            onPlay = { viewModel.playPodcastShow(it) },
                            onRemove = { viewModel.removePodcastShow(it) },
                            onSearch = {
                                onTabSelected(tabOrder.indexOf("podcasts"))
                                searchFocusRequester.requestFocus()
                            },
                            listState = podcastsListState,
                            onOpenDetail = onOpenPodcastShow,
                            onOpenContinue = onOpenPodcastContinue
                        )
                    "downloads" -> {
                            val ytm = youTubeManager
                            if (ytm != null) {
                                DownloadsScreen(
                                    youTubeManager = ytm,
                                    viewModel = viewModel,
                                    listState = downloadsListState,
                                    onDownloadComplete = { fileName, mediaId, result, tagsEmbedded ->
                                        if (fileName != null) {
                                            viewModel.addDownloadToPlaylist(
                                                fileName, mediaId, result, tagsEmbedded
                                            )
                                            // Si los tags no quedaron en el
                                            // archivo (p. ej. .webm), la
                                            // metadata queda en los datos
                                            // propios de la app.
                                            if (!tagsEmbedded) {
                                                viewModel.applyYouTubeMetadataFallback(
                                                    mediaId, result.title, result.artist,
                                                    result.thumbnailUrl
                                                )
                                            }
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

        StreamingNoticeDialog(viewModel)

        // ---- Menú ⋮ de una canción online de la búsqueda ----
        onlineMenuTarget?.let { result ->
            OnlineSongMenuSheet(
                result = result,
                playlists = playlists,
                viewModel = viewModel,
                youTubeManager = youTubeManager,
                onDismiss = { onlineMenuTarget = null }
            )
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
                    text = { Text("Tu Top 10") },
                    trailingIcon = if (FeatureKeys.TOP10 !in seenFeatures) {
                        { FeatureDot() }
                    } else null,
                    onClick = {
                        showGeneralMenu = false
                        viewModel.markFeatureSeen(FeatureKeys.TOP10)
                        onOpenTop10()
                    }
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
                // v1.0.9: "Todas" vive aquí arriba, junto al conteo — la
                // barra de abajo queda con 4 acciones y no se aprieta.
                TextButton(
                    onClick = {
                        // Selecciona las canciones VISIBLES (respeta el
                        // filtro/búsqueda activo, que ya viene en `songs`).
                        selectedIds.addAll(songs.map { it.id }.filter { it !in selectedIds })
                    },
                    enabled = songs.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Todas")
                }
            }
        }
    }
    } // v1.0.7: cierra Box de la barra de selección

    // ---- v1.0.7: barra de acciones inferior (zona del pulgar) ----
    // 100dp de aire abajo para quedar sobre el mini-reproductor. La
    // barra va centrada con márgenes laterales iguales y acciones con
    // el mismo ancho: sin el hueco asimétrico de la derecha, que
    // apretaba la última acción y partía su texto.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 100.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
    androidx.compose.animation.AnimatedVisibility(
        visible = selectionMode,
        enter = androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.slideOutVertically { it }
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Playlist",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { showPlaylistPicker = true }
                )
                SelectionActionButton(
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
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
                SelectionActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Delete,
                    label = "Eliminar",
                    enabled = selectedIds.isNotEmpty(),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        pendingDeleteIds = selectedIds.toList()
                        showDeleteSongsConfirm = true
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

    // v1.0.9: confirmación antes de borrar los archivos seleccionados.
    if (showDeleteSongsConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteSongsConfirm = false
                pendingDeleteIds = emptyList()
            },
            title = { Text("Eliminar canciones") },
            text = {
                Text(
                    "¿Eliminar ${pendingDeleteIds.size} " +
                        "${if (pendingDeleteIds.size == 1) "canción" else "canciones"}? " +
                        "Se borrarán los archivos del dispositivo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSongsConfirm = false
                    val ids = pendingDeleteIds
                    val request = viewModel.buildDeleteRequest(ids)
                    if (request != null) {
                        deleteSongsLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                        )
                    } else {
                        // API <30: el borrado directo ya se hizo en el ViewModel.
                        android.widget.Toast.makeText(
                            appContext,
                            if (ids.size == 1) "Canción eliminada"
                            else "${ids.size} canciones eliminadas",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        pendingDeleteIds = emptyList()
                        selectionMode = false
                        selectedIds.clear()
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteSongsConfirm = false
                    pendingDeleteIds = emptyList()
                }) { Text("Cancelar") }
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
                    items(playlists.distinctBy { it.id }, key = { it.id }) { pl ->
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

    // ---- Modales de inicio (uno a la vez, en orden) ----
    when {
        birthdayPending -> {
            BirthdayModal(
                displayName = birthdayDisplayName(userName ?: "", birthday ?: ""),
                onDismiss = { birthdayPending = false }
            )
        }
        welcomePending -> {
            ProfileFormDialog(
                initialName = "",
                initialBirthday = null,
                title = "¿Cómo te llamas?",
                subtitle = "Para saludarte por tu nombre y felicitarte en tu cumpleaños. " +
                    "Todo queda guardado solo en tu teléfono.",
                confirmLabel = "Guardar",
                showSkip = true,
                showDelete = false,
                onSkip = { welcomePending = false; viewModel.setProfileAsked() },
                onDelete = {},
                onDismiss = { welcomePending = false; viewModel.setProfileAsked() },
                onConfirm = { name, md ->
                    welcomePending = false
                    viewModel.saveUserProfile(name, md)
                }
            )
        }
        newsPending -> {
            NewsModal(
                versionName = newsVersion,
                onDismiss = {
                    newsPending = false
                    viewModel.setLastNewsVersion(newsVersion)
                }
            )
        }
    }

}


/** ID virtual para la lista de favoritos dentro de las playlists. */
const val FAVORITES_ID = "favorites"

/** ID virtual para la Playlist del día dentro de las playlists. */
const val DAILY_PLAYLIST_ID = "daily_playlist"

/** ID virtual para Agregadas recientemente dentro de las playlists. v1.0.9. */
const val RECENTLY_ADDED_ID = "recently_added"

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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSeeAll) { Text("Ver todas") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            topPlaylists.forEach { tp ->
                // Tarjeta definida: fondo propio + borde 1dp + radio 16dp,
                // título y ▶ dentro de la portada (como la referencia)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                    // Borde 1dp por encima de la portada: la tarjeta no se
                    // confunde con el fondo ni cuando la foto la llena entera
                    // (un borde en la cadena de modifiers quedaría tapado
                    // por la imagen, que se dibuja encima).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta hero de la Sesión DJ: degradado del color del tema, pill
 * superior, título grande y botón pastilla. Cuando la sesión está
 * activa lo muestra y ofrece detenerla; el botón principal siempre
 * arranca una lista NUEVA (cada toque = lista distinta).
 */
@Composable
private fun DjSessionCard(
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        primary.copy(alpha = 0.32f),
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .border(1.dp, primary.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        // Decoración: círculo de color arriba a la derecha, como el mesh.
        Box(
            Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = (-48).dp)
                .background(primary.copy(alpha = 0.18f), CircleShape)
        )
        Column {
            // Pill superior.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "SESIÓN INFINITA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Sesión DJ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Que la música no pare",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isActive) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(primary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Sesión activa",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Solo el botón arranca o para: la tarjeta ya no es un botón
            // entero. Inactiva: "Nueva sesión". Activa: SOLO "Detener",
            // que para la música por completo (Stop).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary)
                    .clickable { if (isActive) onStop() else onStart() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (isActive) {
                    // Cuadrito de stop dibujado (sin depender de un icono).
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(onPrimary, RoundedCornerShape(2.dp))
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isActive) "Detener" else "Nueva sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onPrimary
                )
            }
        }
    }
}

// --------------------------- Pestaña: Inicio ---------------------------
// El inicio de Aural: lo más escuchado, la Sesión DJ y los artistas
// favoritos. Sin barra A-Z (como Descargas y Playlists): es contenido
// corto y curado, no una lista larga.

@Composable
private fun HomeTab(
    topPlaylists: List<TopPlaylist>,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onSeeAllPlaylists: () -> Unit,
    onDjSession: () -> Unit,
    isDjSessionActive: Boolean,
    onStopDjSession: () -> Unit,
    topArtists: List<TopArtist>,
    onOpenArtist: (String) -> Unit,
    songs: List<Song>,
    customCovers: Map<Long, String>,
    recentSongs: List<Song>,
    onPlayRecent: (Int) -> Unit,
    weekStats: WeekStats,
    weekTopSong: Song?,
    onPlayWeekTopSong: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTop10: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val homeListState = rememberLazyListState()
    // La animación del odómetro solo corre cuando la tarjeta de la semana
    // está COMPLETAMENTE visible: ni un píxel asomando, ni tapada por el
    // MiniPlayer (los 100dp de aire inferior). Si corriera al abrir el
    // Inicio, nadie vería la animación.
    val density = LocalDensity.current
    val bottomClearancePx = with(density) { 100.dp.toPx() }.toInt()
    val weekFullyVisible by remember {
        derivedStateOf {
            val info = homeListState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == "week_stats" }
            item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset - bottomClearancePx
        }
    }
    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        if (topPlaylists.isNotEmpty()) {
            item(key = "para_ti") {
                MostPlayedSection(
                    topPlaylists = topPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlayPlaylist = onPlayPlaylist,
                    onSeeAll = onSeeAllPlaylists,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp)
                )
            }
        }
        // Tarjeta Sesión DJ: va DEBAJO de "Para ti", como tarjeta hero.
        item(key = "dj") {
            DjSessionCard(
                isActive = isDjSessionActive,
                onStart = onDjSession,
                onStop = onStopDjSession,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
            )
        }
        if (topArtists.isNotEmpty()) {
            item(key = "artists") {
                FavoriteArtistsSection(
                    topArtists = topArtists,
                    onOpenArtist = onOpenArtist,
                    songs = songs,
                    customCovers = customCovers,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
                )
            }
        }
        if (recentSongs.isNotEmpty()) {
            item(key = "recent") {
                RecentlyPlayedSection(
                    recentSongs = recentSongs,
                    customCovers = customCovers,
                    onPlayRecent = onPlayRecent,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }
        }
        if (weekStats.totalSeconds > 0) {
            item(key = "week_stats") {
                WeekStatsSection(
                    weekStats = weekStats,
                    weekTopSong = weekTopSong,
                    customCovers = customCovers,
                    onPlayWeekTopSong = onPlayWeekTopSong,
                    odometerVisible = weekFullyVisible,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
                )
            }
        }
        item(key = "quick") {
            QuickActionsSection(
                onShuffleAll = onShuffleAll,
                onOpenFavorites = onOpenFavorites,
                onOpenTop10 = onOpenTop10,
                onOpenDownloads = onOpenDownloads,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
            )
        }
    }
}

/** "3 h 20 min" / "45 min" / "30 s" para el tiempo escuchado. */
private fun formatListenedTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        m > 0 -> "$m min"
        else -> "$seconds s"
    }
}

@Composable
private fun FavoriteArtistsSection(
    topArtists: List<TopArtist>,
    onOpenArtist: (String) -> Unit,
    songs: List<Song>,
    customCovers: Map<Long, String>,
    modifier: Modifier = Modifier
) {
    // Portada de cada artista: la de su canción más escuchada (o la nota
    // musical solo si ese artista no tiene ninguna portada).
    val songsById = remember(songs) { songs.associateBy { it.id } }
    // Tarjeta definida: fondo propio + borde 1dp + radio 16dp.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            "Tus artistas favoritos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        topArtists.forEach { artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenArtist(artist.name) }
                    .padding(vertical = 10.dp)
            ) {
                val coverUri = artist.topSongId?.let { id ->
                    customCovers[id] ?: songsById[id]?.albumArtUri
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (!coverUri.isNullOrBlank()) {
                        AsyncImage(
                            model = android.net.Uri.parse(coverUri),
                            contentDescription = "Portada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        "${artist.songCount} ${if (artist.songCount == 1) "canción" else "canciones"} · ${formatListenedTime(artist.seconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedSection(
    recentSongs: List<Song>,
    customCovers: Map<Long, String>,
    onPlayRecent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Escuchadas recientemente",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            itemsIndexed(recentSongs, key = { _, song -> song.id }) { index, song ->
                // Sin clip en la columna: el recorte redondeado es solo de
                // la portada. (El clip en la columna recortaba también el
                // nombre del artista y la esquina de la portada.)
                Column(
                    Modifier
                        .width(112.dp)
                        .clickable { onPlayRecent(index) }
                ) {
                    // Portada de la canción (o nota musical solo si no hay).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        val cover = customCovers[song.id] ?: song.albumArtUri
                        if (!cover.isNullOrBlank()) {
                            AsyncImage(
                                model = android.net.Uri.parse(cover),
                                contentDescription = "Portada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Número con efecto odómetro: sube rodando desde 0 hasta [value] cuando
 * [visible] es true, con una animación suave. Si no está visible, vuelve
 * a 0 para que la próxima vez se vea la animación entera.
 */
@Composable
private fun OdometerNumber(
    value: Int,
    visible: Boolean,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val animated by animateIntAsState(
        targetValue = if (visible) value else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "odometer"
    )
    Text(
        text = animated.toString(),
        style = style,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun WeekStatsSection(
    weekStats: WeekStats,
    weekTopSong: Song?,
    customCovers: Map<Long, String>,
    onPlayWeekTopSong: () -> Unit,
    odometerVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    // Tarjeta definida: fondo propio + borde 1dp + radio 16dp.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            "Tu semana en Aural",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        // Tiempo escuchado, con odómetro (horas y minutos suben por separado).
        val hours = (weekStats.totalSeconds / 3600).toInt()
        val minutes = ((weekStats.totalSeconds % 3600) / 60).toInt()
        Row(verticalAlignment = Alignment.Bottom) {
            OdometerNumber(
                value = hours,
                visible = odometerVisible,
                style = MaterialTheme.typography.headlineMedium,
                color = primary
            )
            Text(
                " h ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OdometerNumber(
                value = minutes,
                visible = odometerVisible,
                style = MaterialTheme.typography.headlineMedium,
                color = primary
            )
            Text(
                " min",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OdometerNumber(
                value = weekStats.distinctSongs,
                visible = odometerVisible,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                " ${if (weekStats.distinctSongs == 1) "canción distinta" else "canciones distintas"} esta semana",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val top = weekStats.topSongs.firstOrNull()
        if (top != null && weekTopSong != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlayWeekTopSong() }
                    .padding(vertical = 4.dp)
            ) {
                // Portada de la más escuchada (o nota si no hay).
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                    val cover = customCovers[weekTopSong.id] ?: weekTopSong.albumArtUri
                    if (!cover.isNullOrBlank()) {
                        AsyncImage(
                            model = android.net.Uri.parse(cover),
                            contentDescription = "Portada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "La más escuchada",
                        style = MaterialTheme.typography.labelMedium,
                        color = primary
                    )
                    Text(
                        top.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        top.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onShuffleAll: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTop10: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            label = "Aleatorio",
            onClick = onShuffleAll,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        QuickActionButton(
            label = "Favoritas",
            onClick = onOpenFavorites,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        QuickActionButton(
            label = "Top 10",
            onClick = onOpenTop10,
            modifier = Modifier.weight(1f)
        ) {
            // El "10" dibujado, sin depender de un icono de trofeo.
            Text(
                "10",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        QuickActionButton(
            label = "Descargas",
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Saludo según la hora del día, estilo cálido. Con nombre guardado,
 * saludo personalizado con el primer nombre (o "Purry" en el caso especial).
 */
private fun greetingForHour(userName: String?, birthday: String?): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val base = when (hour) {
        in 5..11 -> "Buenos días"
        in 12..17 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val display = greetingDisplayName(userName, birthday)
    return if (display == null) base
    else "$base, $display. ¿Qué quieres escuchar hoy?"
}

/**
 * Botón de la barra de acciones de selección múltiple:
 * icono grande + etiqueta, pensado para la zona del pulgar.
 */
@Composable
private fun SelectionActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
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

/**
 * Pantalla de permiso de audio: explica por qué hace falta y da el
 * camino para concederlo — el diálogo del sistema, o los Ajustes si el
 * usuario marcó "no volver a preguntar" (ahí el diálogo ya no sale).
 * Al volver de Ajustes con el permiso concedido, MainActivity.onResume
 * escanea la biblioteca sola.
 */
@Composable
private fun PermissionRequiredState(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Permiso necesario",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "AuralPlayer necesita acceso a tus archivos de audio para mostrar tus canciones. Sin ese permiso la biblioteca queda vacía.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (permanentlyDenied) "Abrir Ajustes" else "Dar permiso")
            }
            if (permanentlyDenied) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        "En Ajustes toca Permisos → Música y audio → Permitir, y vuelve a la app."
                    else
                        "En Ajustes toca Permisos → Archivos → Permitir, y vuelve a la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
