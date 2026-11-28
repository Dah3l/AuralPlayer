// ViewModel principal: biblioteca, búsqueda, playlists, favoritos y reproducción
package com.aural.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aural.player.data.Album
import com.aural.player.data.Artist
import com.aural.player.data.Folder
import com.aural.player.data.PlayHistoryStore
import com.aural.player.data.Playlist
import com.aural.player.data.PodcastClient
import com.aural.player.data.PodcastContinueItem
import com.aural.player.data.PodcastDownload
import com.aural.player.data.PodcastDownloadManager
import com.aural.player.data.PodcastEpisode
import com.aural.player.data.PodcastProgress
import com.aural.player.data.PodcastShow
import com.aural.player.data.RadioBrowserClient
import com.aural.player.data.RadioStation
import com.aural.player.data.Song
import com.aural.player.data.SettingsStore
import com.aural.player.data.SongCoverStore
import com.aural.player.data.TagEditor
import com.aural.player.data.TagWriteResult
import com.aural.player.data.TopArtist
import com.aural.player.data.TopSong
import com.aural.player.data.WeekStats
import com.aural.player.data.buildM3u
import com.aural.player.data.parseM3u
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.aural.player.alarm.AlarmConfig
import com.aural.player.alarm.AlarmScheduler
import com.aural.player.playback.AudioFileInfo
import com.aural.player.playback.BpmAnalysis
import com.aural.player.playback.PlayerUiState
import com.aural.player.playback.StopMode
import com.aural.player.youtube.YTResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Estado de la interfaz de la biblioteca. */
data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false,
    // El usuario negó el permiso y marcó "no volver a preguntar": el
    // diálogo del sistema ya no sale y hay que ir a Ajustes a mano.
    val permissionPermanentlyDenied: Boolean = false,
    val query: String = ""
) {
    /** Canciones que coinciden con la búsqueda actual. */
    val filteredSongs: List<Song>
        get() = if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
}

/**
 * ViewModel único de la aplicación. Combina el repositorio MediaStore,
 * el almacén DataStore y el gestor de reproducción.
 */
class AuralViewModel(app: Application) : AndroidViewModel(app) {

    private val auralApp = app as AuralApp
    private val repository = auralApp.repository
    private val settings = auralApp.settings

    val playback = auralApp.playback

    /** Estado reactivo del reproductor para toda la UI. */
    val playerState: StateFlow<PlayerUiState> = playback.state

    /** Id de la canción actual: cambia solo al cambiar de pista. La
     *  biblioteca lo colecciona en vez de playerState entero para no
     *  recomponer cada 500 ms con el ticker de posición. */
    val currentSongId: StateFlow<Long?> = playerState
        .map { it.currentSong?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Si suena ahora mismo: cambia solo al pausar/reanudar. */
    val isPlaying: StateFlow<Boolean> = playerState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    /** Playlists persistidas. */
    val playlists: StateFlow<List<Playlist>> = settings.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    /** Emisoras guardadas por el usuario. */
    val radioStations: StateFlow<List<RadioStation>> = settings.radioStations.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    /** Podcasts guardados por el usuario. */
    val podcastShows: StateFlow<List<PodcastShow>> = settings.podcastShows.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    /** Progreso por episodio (feedUrl|guid -> progreso). */
    val podcastProgress: StateFlow<Map<String, PodcastProgress>> =
        settings.podcastProgress.stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyMap()
        )

    /** Descargas de episodios: registro y progreso en curso. */
    private val podcastDownloadManager = PodcastDownloadManager(app)
    val podcastDownloads: StateFlow<Map<String, PodcastDownload>> =
        settings.podcastDownloads.stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyMap()
        )
    val podcastDownloadProgress: StateFlow<Map<String, Float>> =
        podcastDownloadManager.progress
    val podcastDownloadingKeys: StateFlow<Set<String>> =
        podcastDownloadManager.downloadingKeys
    val podcastDownloadDetails:
        StateFlow<Map<String, com.aural.player.data.ActiveDownload>> =
        podcastDownloadManager.details

    /** Playlists online guardadas como referencia. */
    val savedOnlinePlaylists:
        StateFlow<List<com.aural.player.data.SavedOnlinePlaylist>> =
        settings.savedOnlinePlaylists.stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyList()
        )

    fun toggleSavedOnlinePlaylist(
        playlist: com.aural.player.youtube.OnlineCollectionResult
    ) {
        viewModelScope.launch {
            if (savedOnlinePlaylists.value.any { it.url == playlist.url }) {
                settings.removeSavedOnlinePlaylist(playlist.url)
            } else {
                settings.addSavedOnlinePlaylist(
                    com.aural.player.data.SavedOnlinePlaylist(
                        url = playlist.url,
                        title = playlist.title,
                        subtitle = playlist.subtitle,
                        thumbnailUrl = playlist.thumbnailUrl
                    )
                )
            }
        }
    }

    fun removeSavedOnlinePlaylist(url: String) {
        viewModelScope.launch { settings.removeSavedOnlinePlaylist(url) }
    }

    /** Cancela la descarga de un episodio en curso. */
    fun cancelPodcastDownload(key: String) {
        podcastDownloadManager.cancel(key)
    }

    fun cancelPodcastDownloadForSong(song: Song) {
        val feedUrl = song.podcastFeedUrl ?: return
        val guid = song.podcastGuid ?: return
        podcastDownloadManager.cancel(
            PodcastEpisode.progressKey(feedUrl, guid)
        )
    }

    /** Descubrimiento en búsqueda: resultados y estado por
     *  chip, con la consulta que los produjo para no mostrar
     *  un vacío adelantado mientras la búsqueda corre. */
    private val _radioSearchResults = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioSearchResults: StateFlow<List<RadioStation>> =
        _radioSearchResults.asStateFlow()
    private val _radioSearching = MutableStateFlow(false)
    val radioSearching: StateFlow<Boolean> = _radioSearching.asStateFlow()
    private val _radioSearchQuery = MutableStateFlow("")
    val radioSearchQuery: StateFlow<String> = _radioSearchQuery.asStateFlow()
    private var radioSearchJob: Job? = null

    private val _podcastSearchResults = MutableStateFlow<List<PodcastShow>>(emptyList())
    val podcastSearchResults: StateFlow<List<PodcastShow>> =
        _podcastSearchResults.asStateFlow()
    private val _podcastSearching = MutableStateFlow(false)
    val podcastSearching: StateFlow<Boolean> = _podcastSearching.asStateFlow()
    private val _podcastSearchQuery = MutableStateFlow("")
    val podcastSearchQuery: StateFlow<String> = _podcastSearchQuery.asStateFlow()
    private var podcastSearchJob: Job? = null

    /** Caché de navegación de Emisoras/Podcasts: resultados
     *  por consulta en memoria de sesión. Cambiar de pestaña
     *  o subpestaña no vuelve a buscar; solo un texto nuevo. */
    private val radioSearchCache = mutableMapOf<String, List<RadioStation>>()
    private val podcastSearchCache = mutableMapOf<String, List<PodcastShow>>()

    fun searchRadio(query: String) {
        val term = query.trim()
        if (term.isBlank()) {
            _radioSearchQuery.value = ""
            _radioSearching.value = false
            return
        }
        val key = term.lowercase()
        radioSearchCache[key]?.let { cached ->
            radioSearchJob?.cancel()
            _radioSearchResults.value = cached
            _radioSearchQuery.value = term
            _radioSearching.value = false
            return
        }
        if (_radioSearching.value && _radioSearchQuery.value == term) return
        radioSearchJob?.cancel()
        _radioSearching.value = true
        radioSearchJob = viewModelScope.launch {
            val results = RadioBrowserClient.search(term)
                .distinctBy { it.id }
            radioSearchCache[key] = results
            _radioSearchResults.value = results
            _radioSearchQuery.value = term
            _radioSearching.value = false
        }
    }

    fun searchPodcasts(query: String) {
        val term = query.trim()
        if (term.isBlank()) {
            _podcastSearchQuery.value = ""
            _podcastSearching.value = false
            return
        }
        val key = term.lowercase()
        podcastSearchCache[key]?.let { cached ->
            podcastSearchJob?.cancel()
            _podcastSearchResults.value = cached
            _podcastSearchQuery.value = term
            _podcastSearching.value = false
            return
        }
        if (_podcastSearching.value && _podcastSearchQuery.value == term) return
        podcastSearchJob?.cancel()
        _podcastSearching.value = true
        podcastSearchJob = viewModelScope.launch {
            val results = PodcastClient.searchPodcasts(term)
                .distinctBy { it.feedUrl }
            podcastSearchCache[key] = results
            _podcastSearchResults.value = results
            _podcastSearchQuery.value = term
            _podcastSearching.value = false
        }
    }

    fun clearCollectionSearch() {
        // Ya no borra resultados: salir de Emisoras/Podcasts
        // o pasar a Locales no obliga a buscar de nuevo al
        // volver. Solo se limpia la consulta activa.
        _radioSearchQuery.value = ""
        _podcastSearchQuery.value = ""
        _radioSearching.value = false
        _podcastSearching.value = false
    }

    /** Sugerencias online sin texto: emisoras cubanas y de los
     *  géneros de la colección; podcasts relacionados con lo
     *  guardado y categorías en español. */
    private val _radioSuggestions = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioSuggestions: StateFlow<List<RadioStation>> =
        _radioSuggestions.asStateFlow()
    private val _radioSuggestionsLoading = MutableStateFlow(false)
    val radioSuggestionsLoading: StateFlow<Boolean> =
        _radioSuggestionsLoading.asStateFlow()
    private var radioSuggestionsJob: Job? = null

    private val _podcastSuggestions = MutableStateFlow<List<PodcastShow>>(emptyList())
    val podcastSuggestions: StateFlow<List<PodcastShow>> =
        _podcastSuggestions.asStateFlow()
    private val _podcastSuggestionsLoading = MutableStateFlow(false)
    val podcastSuggestionsLoading: StateFlow<Boolean> =
        _podcastSuggestionsLoading.asStateFlow()
    private var podcastSuggestionsJob: Job? = null

    /** Las sugerencias se cargan una vez por sesión y quedan:
     *  cambiar de pestaña no las recarga. */
    private var radioSuggestionsAttempted = false
    private var podcastSuggestionsAttempted = false

    fun loadRadioSuggestions() {
        if (radioSuggestionsJob?.isActive == true) return
        if (radioSuggestionsAttempted) return
        radioSuggestionsAttempted = true
        _radioSuggestionsLoading.value = true
        radioSuggestionsJob = viewModelScope.launch {
            try {
                val genres = radioStations.value
                    .flatMap { it.genre.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(2)
                // En paralelo y con techo: la carga siempre
                // termina y el esqueleto sale al estado guía
                // si no llega contenido.
                val results = withTimeoutOrNull(25_000L) {
                    coroutineScope {
                        val cuban = async {
                            RadioBrowserClient.searchByCountry("Cuba", limit = 30)
                        }
                        val byGenre = genres.map { genre ->
                            async {
                                RadioBrowserClient.searchByTag(genre, limit = 20)
                            }
                        }.awaitAll().flatten()
                        (cuban.await() + byGenre)
                            .distinctBy { it.id }
                            .take(50)
                    }
                } ?: emptyList()
                _radioSuggestions.value = results
            } finally {
                _radioSuggestionsLoading.value = false
            }
        }
    }

    fun loadPodcastSuggestions() {
        if (podcastSuggestionsJob?.isActive == true) return
        if (podcastSuggestionsAttempted) return
        podcastSuggestionsAttempted = true
        _podcastSuggestionsLoading.value = true
        podcastSuggestionsJob = viewModelScope.launch {
            try {
                val savedTerms = podcastShows.value
                    .map { it.title }
                    .filter { it.isNotBlank() }
                    .take(2)
                val terms = (savedTerms + listOf("noticias", "comedia", "deporte"))
                    .distinct()
                    .take(4)
                val results = withTimeoutOrNull(25_000L) {
                    coroutineScope {
                        terms.map { term ->
                            async {
                                PodcastClient.searchPodcasts(term, limit = 15)
                            }
                        }.awaitAll().flatten()
                    }
                } ?: emptyList()
                _podcastSuggestions.value = results
                    .distinctBy { it.feedUrl }
                    .take(40)
            } finally {
                _podcastSuggestionsLoading.value = false
            }
        }
    }

    /** Sugerencias musicales Online (Canciones/Artistas/
     *  Álbumes/Playlists y tarjetas del Inicio): viven en el
     *  ViewModel para sobrevivir a la navegación. Se cargan
     *  una vez por sesión y solo se refrescan si cambian las
     *  semillas de escucha (Top/recientes/favoritos). */
    private val _musicSuggestions =
        MutableStateFlow<com.aural.player.youtube.OnlineSearchResults?>(null)
    val musicSuggestions: StateFlow<com.aural.player.youtube.OnlineSearchResults?> =
        _musicSuggestions.asStateFlow()
    private val _musicSuggestionsSongsUi = MutableStateFlow<List<Song>>(emptyList())
    val musicSuggestionsSongsUi: StateFlow<List<Song>> =
        _musicSuggestionsSongsUi.asStateFlow()
    private val _musicSuggestionsLoading = MutableStateFlow(false)
    val musicSuggestionsLoading: StateFlow<Boolean> =
        _musicSuggestionsLoading.asStateFlow()
    private var musicSuggestionsJob: Job? = null
    private var musicSuggestionsSeedKey: String? = null

    fun loadMusicSuggestions(
        youTubeManager: com.aural.player.youtube.YouTubeManager?,
        seeds: List<String>
    ) {
        val cleanSeeds = seeds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        val seedKey = cleanSeeds.joinToString("|")
        if (musicSuggestionsSeedKey == seedKey &&
            _musicSuggestions.value != null
        ) return
        if (musicSuggestionsJob?.isActive == true &&
            musicSuggestionsSeedKey == seedKey
        ) return
        // Semillas nuevas: la carga anterior ya no vale.
        musicSuggestionsJob?.cancel()
        musicSuggestionsSeedKey = seedKey
        if (cleanSeeds.isEmpty() || youTubeManager == null) {
            _musicSuggestions.value =
                com.aural.player.youtube.OnlineSearchResults()
            _musicSuggestionsSongsUi.value = emptyList()
            _musicSuggestionsLoading.value = false
            return
        }
        _musicSuggestionsLoading.value = true
        musicSuggestionsJob = viewModelScope.launch {
            try {
                val loaded = withTimeoutOrNull(30_000L) {
                    youTubeManager.searchSuggestions(cleanSeeds)
                } ?: com.aural.player.youtube.OnlineSearchResults()
                _musicSuggestions.value = loaded
                _musicSuggestionsSongsUi.value =
                    registerOnlineSongs(loaded.songs)
                youTubeManager.prefetchVisibleSongs(loaded.songs)
            } finally {
                _musicSuggestionsLoading.value = false
            }
        }
    }

    /** Caché de detalles de podcast/emisora: una vez cargados
     *  quedan en memoria; salir y volver no los recarga. El
     *  progreso y las descargas se leen de sus flujos, así
     *  que la ficha sigue al día sin recargar la lista. */
    private val podcastEpisodesCache =
        mutableMapOf<String, List<PodcastEpisode>>()
    private val radioVersionsCache =
        mutableMapOf<String, List<RadioStation>>()

    fun cachedPodcastEpisodes(show: PodcastShow): List<PodcastEpisode>? =
        podcastEpisodesCache[show.feedUrl]

    fun cachedRadioStationVersions(station: RadioStation): List<RadioStation>? =
        radioVersionsCache[station.id]

    /** Episodios nuevos en los podcasts guardados (para el
     *  badge del Inicio): episodios publicados después del
     *  más reciente con progreso de ese programa y aún no
     *  escuchados; sin progreso, cuenta el último episodio. */
    private val _podcastNewEpisodeCount = MutableStateFlow(0)
    val podcastNewEpisodeCount: StateFlow<Int> =
        _podcastNewEpisodeCount.asStateFlow()
    private var podcastNewCountJob: Job? = null
    private var podcastNewCountSignature: String? = null

    fun refreshPodcastNewEpisodes() {
        val shows = podcastShows.value
        val progress = podcastProgress.value
        val signature = shows.map { it.feedUrl }.sorted().joinToString("|") +
            "#" + progress.values.maxOfOrNull { it.updatedAtMs }
        if (podcastNewCountSignature == signature) return
        if (podcastNewCountJob?.isActive == true) return
        podcastNewCountSignature = signature
        podcastNewCountJob = viewModelScope.launch {
            var total = 0
            for (show in shows) {
                val episodes = podcastEpisodes(show)
                if (episodes.isEmpty()) continue
                val threshold = progress.values
                    .filter { it.feedUrl == show.feedUrl }
                    .maxOfOrNull { it.publishedMs } ?: 0L
                total += if (threshold <= 0L) {
                    1
                } else {
                    episodes.count { episode ->
                        episode.publishedMs > threshold &&
                            progress[PodcastEpisode.progressKey(
                                show.feedUrl, episode.guid
                            )]?.listened != true
                    }
                }
            }
            _podcastNewEpisodeCount.value = total
        }
    }

    /** Episodios de un podcast para su ficha (cacheados). */
    suspend fun podcastEpisodes(show: PodcastShow): List<PodcastEpisode> {
        podcastEpisodesCache[show.feedUrl]?.let { return it }
        val episodes = PodcastClient.fetchEpisodes(
            show.feedUrl, limit = 100, settings = settings
        )
            .distinctBy { PodcastEpisode.progressKey(show.feedUrl, it.guid) }
        if (episodes.isNotEmpty()) {
            podcastEpisodesCache[show.feedUrl] = episodes
        }
        return episodes
    }

    /** «Sonando ahora» (ICY) de una emisora, si lo ofrece. */
    suspend fun radioNowPlaying(station: RadioStation): String? =
        RadioBrowserClient.fetchNowPlaying(station.streamUrl)

    /** Versiones de la misma emisora en Radio Browser (mismo
     *  nombre normalizado), de mayor a menor bitrate. */
    suspend fun radioStationVersions(station: RadioStation): List<RadioStation> {
        radioVersionsCache[station.id]?.let { return it }
        val versions = radioStationVersionsFresh(station)
        if (versions.isNotEmpty()) {
            radioVersionsCache[station.id] = versions
        }
        return versions
    }

    private suspend fun radioStationVersionsFresh(station: RadioStation): List<RadioStation> {
        fun normalized(name: String): String = name.lowercase()
            .replace(Regex("[^a-z0-9áéíóúñ ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val targetName = normalized(station.name)
        val versions = RadioBrowserClient.searchByName(station.name, limit = 100)
            .filter { candidate ->
                normalized(candidate.name) == targetName &&
                    (station.country.isBlank() || candidate.country.isBlank() ||
                        candidate.country.equals(station.country, ignoreCase = true))
            }
        return (versions + station)
            .distinctBy { it.streamUrl }
            .sortedByDescending { it.bitrateKbps }
    }

    /** Escoge una versión de emisora: queda guardada en la
     *  colección sustituyendo a la anterior. */
    fun selectRadioStationVersion(old: RadioStation, version: RadioStation) {
        viewModelScope.launch { settings.replaceRadioStation(old.id, version) }
    }

    /** Episodios empezados para «Sigue escuchando»: TODOS los
     *  que tengan progreso guardado, pertenezcan o no a
     *  podcasts agregados como «míos». Los datos salen del
     *  propio registro de progreso; los registros viejos sin
     *  metadatos se resuelven contra los podcasts guardados. */
    private var continueCache: List<PodcastContinueItem>? = null
    private var continueCacheSignature: String? = null

    private fun continueSignature(): String =
        podcastProgress.value.entries
            .sortedBy { it.key }
            .joinToString("|") {
                "${it.key}:${it.value.updatedAtMs}:${it.value.positionMs}:${it.value.listened}"
            } + "#" + podcastShows.value.map { it.feedUrl }.sorted().joinToString("|")

    /** «Sigue escuchando» ya calculado, si el progreso no
     *  cambió desde la última vez (sin recargar al volver). */
    fun cachedContinueListening(): List<PodcastContinueItem>? =
        if (continueCacheSignature == continueSignature()) continueCache else null

    suspend fun continueListeningEpisodes(): List<PodcastContinueItem> {
        cachedContinueListening()?.let { return it }
        val signature = continueSignature()
        val items = continueListeningFresh()
        continueCache = items
        continueCacheSignature = signature
        return items
    }

    private suspend fun continueListeningFresh(): List<PodcastContinueItem> =
        coroutineScope {
            val progress = podcastProgress.value
            val started = progress.filterValues {
                !it.listened && it.positionMs > 0L
            }
            val fromRegistry = started.mapNotNull { (key, saved) ->
                if (saved.audioUrl.isBlank() || saved.episodeTitle.isBlank()) {
                    return@mapNotNull null
                }
                val feedUrl = saved.feedUrl.ifBlank {
                    key.substringBeforeLast("|")
                }
                val guid = saved.guid.ifBlank {
                    key.substringAfterLast("|")
                }
                val show = PodcastShow(
                    id = feedUrl,
                    title = saved.showTitle.ifBlank { "Podcast" },
                    feedUrl = feedUrl,
                    imageUrl = saved.showImageUrl,
                    author = saved.showAuthor
                )
                val episode = PodcastEpisode(
                    guid = guid,
                    title = saved.episodeTitle,
                    publishedMs = saved.publishedMs,
                    durationMs = saved.durationMs,
                    audioUrl = saved.audioUrl,
                    imageUrl = saved.episodeImageUrl,
                    audioBytes = saved.audioBytes,
                    audioMimeType = saved.audioMimeType
                )
                PodcastContinueItem(show, episode, saved)
            }
            val coveredKeys = fromRegistry.map {
                PodcastEpisode.progressKey(it.show.feedUrl, it.episode.guid)
            }.toSet()
            val legacy = podcastShows.value.map { show ->
                async {
                    PodcastClient.fetchEpisodes(
                        show.feedUrl, limit = 100, settings = settings
                    )
                        .distinctBy { PodcastEpisode.progressKey(show.feedUrl, it.guid) }
                        .mapNotNull { episode ->
                            val key = PodcastEpisode.progressKey(
                                show.feedUrl, episode.guid
                            )
                            val saved = started[key]
                            if (saved == null || key in coveredKeys) {
                                null
                            } else {
                                PodcastContinueItem(show, episode, saved)
                            }
                        }
                }
            }.awaitAll().flatten()
            (fromRegistry + legacy)
                .distinctBy {
                    PodcastEpisode.progressKey(it.show.feedUrl, it.episode.guid)
                }
                .sortedWith(
                    compareByDescending<PodcastContinueItem> {
                        it.progress.updatedAtMs
                    }.thenByDescending { it.episode.publishedMs }
                )
        }

    fun downloadPodcastEpisode(show: PodcastShow, episode: PodcastEpisode) {
        podcastDownloadManager.download(show, episode)
    }

    fun deletePodcastDownload(show: PodcastShow, episode: PodcastEpisode) {
        val key = PodcastEpisode.progressKey(show.feedUrl, episode.guid)
        deletePodcastDownloadByKey(key)
    }

    /** Descarga el episodio que suena (desde el Now Playing). */
    fun downloadPodcastForSong(song: Song) {
        val (show, episode) = podcastShowEpisodeForSong(song) ?: return
        podcastDownloadManager.download(show, episode)
    }

    /** Elimina la descarga del episodio que suena. */
    fun deletePodcastDownloadForSong(song: Song) {
        val feedUrl = song.podcastFeedUrl ?: return
        val guid = song.podcastGuid ?: return
        deletePodcastDownloadByKey(PodcastEpisode.progressKey(feedUrl, guid))
    }

    private fun deletePodcastDownloadByKey(key: String) {
        viewModelScope.launch {
            podcastDownloads.value[key]?.let { download ->
                podcastDownloadManager.deleteDownload(download)
            }
            settings.removePodcastDownload(key)
            playback.showMessage("Descarga eliminada")
        }
    }

    private fun podcastShowEpisodeForSong(
        song: Song
    ): Pair<PodcastShow, PodcastEpisode>? {
        val feedUrl = song.podcastFeedUrl ?: return null
        val guid = song.podcastGuid ?: return null
        val audioUrl = song.podcastAudioUrl ?: song.uri
        val show = podcastShows.value.firstOrNull { it.feedUrl == feedUrl }
            ?: PodcastShow(
                id = feedUrl,
                title = song.artist,
                feedUrl = feedUrl,
                imageUrl = song.albumArtUri,
                author = song.album
            )
        val episode = PodcastEpisode(
            guid = guid,
            title = song.title,
            durationMs = song.durationMs,
            audioUrl = audioUrl,
            imageUrl = song.albumArtUri
        )
        return show to episode
    }

    /** Episodio en reproducción (songId -> feedUrl|guid) para
     *  guardar su progreso al pausar y cada pocos segundos. */
    private val podcastEpisodeKeys = mutableMapOf<Long, Pair<String, String>>()
    private val podcastProgressSavedAt = mutableMapOf<Long, Long>()

    /** Despertador configurado (null = sin alarma). */
    val alarm: StateFlow<AlarmConfig?> = settings.alarm.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Nombre del usuario (null = no dado). */
    val userName: StateFlow<String?> = settings.userName.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Cumpleaños "MM-dd" (null = no dado). */
    val birthday: StateFlow<String?> = settings.birthday.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Si ya se mostró la tarjeta de bienvenida del perfil. */
    val profileAsked: StateFlow<Boolean> = settings.profileAsked.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    /** Fecha en que se mostró el modal de cumpleaños por última vez. */
    val birthdayShownDate: StateFlow<String?> = settings.birthdayShownDate.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Funciones nuevas ya vistas (sin puntito). */
    val seenFeatures: StateFlow<Set<String>> = settings.seenFeatures.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptySet()
    )

    /** Última versión con novedades mostradas. */
    val lastNewsVersion: StateFlow<String?> = settings.lastNewsVersion.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Parada programada activa (null = ninguna). */
    val stopMode: StateFlow<StopMode?> = playback.stopMode

    /** Registro de escucha y Top 10. */
    private val playHistory = PlayHistoryStore(auralApp)
    private val _topSongs = MutableStateFlow<List<TopSong>>(emptyList())
    val topSongs: StateFlow<List<TopSong>> = _topSongs.asStateFlow()

    /** Los 5 artistas más escuchados (para "Tus artistas favoritos" del Inicio). */
    private val _topArtists = MutableStateFlow<List<TopArtist>>(emptyList())
    val topArtists: StateFlow<List<TopArtist>> = _topArtists.asStateFlow()

    /** Playlist del día: UNA sola instancia { fecha, ids } que rota a diario. */
    val dailyPlaylist: StateFlow<SettingsStore.DailyPlaylist?> = settings.dailyPlaylist.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val lrcMap: StateFlow<Map<Long, String>> = settings.lrcMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    init {
        // Progreso de podcast: se guarda al pausar y cada 5 s
        // de avance mientras el episodio suena.
        viewModelScope.launch {
            playerState.collect { state ->
                val song = state.currentSong ?: return@collect
                val (feedUrl, guid) = podcastEpisodeKeys[song.id] ?: return@collect
                val position = state.positionMs
                val lastSaved = podcastProgressSavedAt[song.id] ?: -1L
                val shouldSave = !state.isPlaying ||
                    lastSaved < 0L ||
                    kotlin.math.abs(position - lastSaved) >= 5_000L
                if (shouldSave && position > 0L) {
                    podcastProgressSavedAt[song.id] = position
                    val listened = state.durationMs > 0L &&
                        position >= state.durationMs - 10_000L
                    val showEpisode = podcastShowEpisodeForSong(song)
                    val show = showEpisode?.first
                    val episode = showEpisode?.second
                    settings.setPodcastProgress(
                        feedUrl = feedUrl,
                        guid = guid,
                        positionMs = position,
                        listened = listened,
                        showTitle = show?.title ?: song.artist,
                        showAuthor = show?.author ?: song.album,
                        showImageUrl = show?.imageUrl ?: song.albumArtUri,
                        episodeTitle = episode?.title ?: song.title,
                        episodeImageUrl = episode?.imageUrl ?: song.albumArtUri,
                        audioUrl = episode?.audioUrl
                            ?: song.podcastAudioUrl ?: "",
                        durationMs = episode?.durationMs
                            ?.takeIf { it > 0L } ?: song.durationMs,
                        audioBytes = episode?.audioBytes ?: 0L,
                        audioMimeType = episode?.audioMimeType ?: "",
                        publishedMs = episode?.publishedMs ?: 0L
                    )
                }
            }
        }
        // Podcast descargado = offline automático: si el
        // episodio que suena termina de descargarse, la
        // reproducción pasa al archivo local en la misma
        // posición.
        viewModelScope.launch {
            var previousKeys = emptySet<String>()
            podcastDownloads.collect { downloads ->
                val newKeys = downloads.keys - previousKeys
                previousKeys = downloads.keys
                if (newKeys.isEmpty()) return@collect
                val current = playerState.value.currentSong
                    ?: return@collect
                if (!current.isPodcastEpisode) return@collect
                val feedUrl = current.podcastFeedUrl ?: return@collect
                val guid = current.podcastGuid ?: return@collect
                val key = PodcastEpisode.progressKey(feedUrl, guid)
                if (key !in newKeys) return@collect
                val localUri = downloads[key]?.uri ?: return@collect
                if (current.uri == localUri) return@collect
                val (show, episode) = podcastShowEpisodeForSong(current)
                    ?: return@collect
                playback.replaceCurrentSongSource(
                    episode.toSong(show, localUri)
                )
                playback.showMessage("Episodio descargado: suena sin conexión")
            }
        }
        // Crear la playlist "Descargas" si no existe (como Favoritas, desde la instalación)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // esperar a que cargue settings
            if (playlists.value.none { it.name == "Descargas" }) {
                settings.createPlaylist("Descargas")
            }
        }
        // Migración de portadas a archivo local (ver migrateRemoteCovers).
        viewModelScope.launch { migrateRemoteCovers() }
        // Rotación de la Playlist del día: si la guardada no es de hoy,
        // se REGENERA con 30 aleatorias distintas (o las que haya si la
        // biblioteca tiene menos; vacía si no hay canciones) y la anterior
        // DESAPARECE (sin historial). Se chequea cada vez que la
        // biblioteca emite, así cubre el arranque y cambios de día.
        viewModelScope.launch {
            _library.collect { lib ->
                val songs = lib.songs
                if (songs.isEmpty()) return@collect
                val today = java.time.LocalDate.now().toString()
                if (dailyPlaylist.value?.date != today) {
                    val ids = songs.shuffled().take(30).map { it.id }
                    settings.setDailyPlaylist(today, ids)
                }
            }
        }
    }

    /**
     * Migración de portadas (1.3.0): las portadas personalizadas que
     * quedaron guardadas como enlace (http/https, de antes de la
     * 1.2.0 o de una copia local que falló) o como referencia de
     * galería (content://) se descargan/copian al almacenamiento
     * local y el mapa pasa a guardar la ruta local (file://).
     * Sin esto, esas portadas solo se veían con internet: la app las
     * cargaba del enlace en cada visita. Corre en cada arranque pero
     * solo procesa las entradas que aún no son locales; la que falla
     * (sin conexión, enlace muerto) queda como está y se reintenta
     * en el próximo arranque — se cura solo.
     */
    private suspend fun migrateRemoteCovers() {
        withContext(Dispatchers.IO) {
            val covers = settings.customCovers.first()
            if (covers.isEmpty()) return@withContext
            val app = getApplication<Application>()
            for ((songId, value) in covers) {
                val local = when {
                    value.startsWith("http://") || value.startsWith("https://") ->
                        SongCoverStore.saveFromUrl(app, songId, value)
                    value.startsWith("content://") ->
                        SongCoverStore.saveFromContentUri(app, songId, Uri.parse(value))
                    else -> null // file:// o ruta: ya es local
                }
                // Directo a SettingsStore (no al wrapper de esta clase,
                // que trataría la ruta como un enlace más a descargar).
                if (local != null) settings.setCustomCover(songId, local)
            }
        }
    }

    /** IDs de canciones favoritas. */
    val favorites: StateFlow<Set<Long>> = settings.favorites.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()
    )
    /** Tema seleccionado de la app. */
    val appTheme: StateFlow<String> = settings.appTheme.stateIn(
        viewModelScope, SharingStarted.Eagerly, "BOSQUE"
    )

    fun setAppTheme(themeName: String) {
        viewModelScope.launch {
            settings.setAppTheme(themeName)
        }
    }

    /** Portadas personalizadas: songId -> URL o ruta local (file://). */
    val customCovers: StateFlow<Map<Long, String>> = settings.customCovers.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    fun setCustomCover(songId: Long, coverUrl: String) {
        if (coverUrl.isBlank()) {
            // Quitar: borra la entrada y el archivo local (lo hace SettingsStore).
            viewModelScope.launch { settings.setCustomCover(songId, "") }
            return
        }
        // v1.2.0: la portada se descarga y se guarda en el teléfono; si la
        // descarga falla, se guarda el enlace como antes (sin perderla).
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val local = SongCoverStore.saveFromUrl(app, songId, coverUrl)
            settings.setCustomCover(songId, local ?: coverUrl)
        }
    }

    /** Portada elegida desde la galería: se copia a la carpeta local de la app. */
    fun setCustomCoverFromGallery(songId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val local = SongCoverStore.saveFromContentUri(app, songId, uri)
            settings.setCustomCover(songId, local ?: uri.toString())
        }
    }

    /** Orden de favoritos elegido con drag (vacío = orden de biblioteca). */
    val favoriteOrder: StateFlow<List<Long>> = settings.favoriteOrder.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    fun setFavoriteOrder(songIds: List<Long>) {
        viewModelScope.launch { settings.setFavoriteOrder(songIds) }
    }

    /** Fotos de playlists creadas: playlistId -> URI. */
    val playlistCovers: StateFlow<Map<String, String>> = settings.playlistCovers.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    fun setPlaylistCover(playlistId: String, coverUri: String) {
        viewModelScope.launch { settings.setPlaylistCover(playlistId, coverUri) }
    }

    /** Reproducciones por playlist: playlistId -> veces que se puso a sonar. */
    val playlistPlayCounts: StateFlow<Map<String, Long>> = settings.playlistPlayCounts.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    /** Metadatos personalizados: songId -> (título, artista, álbum). */
    val customMetadata: StateFlow<Map<Long, Triple<String, String, String>>> =
        settings.customMetadata.stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyMap()
        )

    fun setCustomMetadata(songId: Long, title: String, artist: String, album: String) {
        viewModelScope.launch { settings.setCustomMetadata(songId, title, artist, album) }
    }

    // ------------------- Edición de tags reales (v1.2.0) -------------------

    /** Resultado de guardar una edición, para avisar en la UI. */
    sealed class TagWriteOutcome {
        object FileSaved : TagWriteOutcome()
        object SavedInAppOnly : TagWriteOutcome()
    }

    private val _tagWriteEvents = MutableSharedFlow<TagWriteOutcome>(extraBufferCapacity = 1)
    val tagWriteEvents: SharedFlow<TagWriteOutcome> = _tagWriteEvents.asSharedFlow()

    /** Edición esperando el permiso de escritura de MediaStore. */
    private var pendingTagEdit: PendingTagEdit? = null

    private data class PendingTagEdit(
        val songId: Long,
        val uri: String,
        val title: String,
        val artist: String,
        val album: String
    )

    /**
     * Guarda la edición escribiendo los tags en el archivo. Si falta el
     * permiso de MediaStore, guarda la edición pendiente y devuelve el
     * PendingIntent para que la UI lance el diálogo del sistema; tras
     * concederlo, la UI llama a [confirmTagWrite]. Si el archivo no se
     * puede escribir, guarda solo en la app (comportamiento de siempre)
     * y devuelve null.
     */
    suspend fun saveMetadataToFile(
        song: Song,
        title: String,
        artist: String,
        album: String
    ): PendingIntent? {
        val coverArt = withContext(Dispatchers.IO) { resolveCoverArt(song, song.id) }
        return when (TagEditor.writeTags(getApplication(), song.uri, title, artist, album, coverArt)) {
            TagWriteResult.Success -> {
                // El archivo ya lleva la verdad: limpiar el overlay,
                // refrescar la biblioteca en memoria al instante y
                // re-escanear para dejar todo consistente. El parche se
                // repite tras el escaneo por si MediaStore devolvió la
                // fila vieja (re-indexando el archivo reemplazado).
                settings.setCustomMetadata(song.id, "", "", "")
                _tagWriteEvents.emit(TagWriteOutcome.FileSaved)
                refreshLibrarySong(song, song.id, title, artist, album)
                rescan { refreshLibrarySong(song, song.id, title, artist, album) }
                null
            }
            TagWriteResult.NeedsPermission -> {
                pendingTagEdit = PendingTagEdit(song.id, song.uri, title, artist, album)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        MediaStore.createWriteRequest(
                            getApplication<Application>().contentResolver,
                            listOf(Uri.parse(song.uri))
                        )
                    } catch (e: Exception) {
                        applyOverlayFallback(song.id, title, artist, album)
                        null
                    }
                } else {
                    applyOverlayFallback(song.id, title, artist, album)
                    null
                }
            }
            TagWriteResult.Failed -> {
                applyOverlayFallback(song.id, title, artist, album)
                null
            }
        }
    }

    /** Tras conceder el permiso, reintenta la escritura pendiente. */
    fun confirmTagWrite() {
        val pending = pendingTagEdit ?: return
        pendingTagEdit = null
        viewModelScope.launch {
            val song = _library.value.songs.find { it.id == pending.songId }
                ?: playback.state.value.queue.find { it.id == pending.songId }
            val coverArt = withContext(Dispatchers.IO) { resolveCoverArt(song, pending.songId) }
            when (TagEditor.writeTags(getApplication(), pending.uri, pending.title, pending.artist, pending.album, coverArt)) {
                TagWriteResult.Success -> {
                    settings.setCustomMetadata(pending.songId, "", "", "")
                    _tagWriteEvents.emit(TagWriteOutcome.FileSaved)
                    refreshLibrarySong(null, pending.songId, pending.title, pending.artist, pending.album)
                    rescan { refreshLibrarySong(null, pending.songId, pending.title, pending.artist, pending.album) }
                }
                else -> applyOverlayFallback(pending.songId, pending.title, pending.artist, pending.album)
            }
        }
    }

    /**
     * Bytes + MIME de la portada que la canción muestra en Aural, para
     * incrustarla en el archivo. Orden: portada personalizada guardada
     * en el teléfono; portada personalizada por enlace (se descarga y
     * se guarda local, como el ítem 1); arte del álbum del sistema.
     * Null si no hay ninguna portada que incrustar.
     */
    private suspend fun resolveCoverArt(song: Song?, songId: Long): Pair<ByteArray, String>? {
        val bytes = resolveCoverBytes(song, songId)
        if (bytes == null || bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) return null
        val isPng = bytes.size > 8 && bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        return bytes to if (isPng) "image/png" else "image/jpeg"
    }

    private suspend fun resolveCoverBytes(song: Song?, songId: Long): ByteArray? {
        val app = getApplication<Application>()
        val value = customCovers.value[songId]
        if (!value.isNullOrBlank()) {
            val bytes = try {
                when {
                    value.startsWith("file://") ->
                        Uri.parse(value).path?.let { File(it).readBytes() }
                    value.startsWith("content://") ->
                        app.contentResolver.openInputStream(Uri.parse(value))?.use { it.readBytes() }
                    value.startsWith("http://") || value.startsWith("https://") ->
                        SongCoverStore.saveFromUrl(app, songId, value)?.let { local ->
                            Uri.parse(local).path?.let { File(it).readBytes() }
                        }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        // Sin portada personalizada: el arte del álbum del sistema.
        val albumArt = song?.albumArtUri
        if (!albumArt.isNullOrBlank()) {
            return try {
                app.contentResolver.openInputStream(Uri.parse(albumArt))?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /** El usuario negó el permiso: la edición queda guardada solo en la app. */
    fun discardTagWrite() {
        val pending = pendingTagEdit ?: return
        pendingTagEdit = null
        viewModelScope.launch {
            applyOverlayFallback(pending.songId, pending.title, pending.artist, pending.album)
        }
    }

    /** Fallback: guardar la edición solo en la app (overlay) y avisar. */
    private suspend fun applyOverlayFallback(songId: Long, title: String, artist: String, album: String) {
        settings.setCustomMetadata(songId, title, artist, album)
        _tagWriteEvents.emit(TagWriteOutcome.SavedInAppOnly)
    }

    /**
     * Parchea la canción en la biblioteca en memoria (reproductor y lista)
     * con los metadatos recién escritos, para que el cambio se vea al
     * instante sin depender del re-escaneo de MediaStore. Si la canción
     * no está en la lista (MediaStore la está re-indexando), se repone.
     */
    private fun refreshLibrarySong(base: Song?, songId: Long, title: String, artist: String, album: String) {
        // La edición manual manda: la canción sale de pendientes para
        // que una fusión posterior no reimponga la metadata de la descarga.
        pendingDownloadSongs.remove(songId)
        val song = base
            ?: _library.value.songs.find { it.id == songId }
            ?: playback.state.value.queue.find { it.id == songId }
            ?: return
        val updated = song.copy(title = title, artist = artist, album = album)
        playback.refreshSongMetadata(updated)
        val songs = _library.value.songs
        _library.value = _library.value.copy(
            songs = if (songs.any { it.id == songId }) {
                songs.map { s -> if (s.id == songId) updated else s }
            } else {
                (songs + updated).sortedBy { it.title }
            }
        )
    }


    // --------------------------- Permisos y escaneo ---------------------------

    /** Indica al ViewModel que el permiso fue concedido; lanza el escaneo. */
    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        _library.value = _library.value.copy(
            permissionGranted = granted,
            permissionPermanentlyDenied = permanentlyDenied
        )
        if (granted && _library.value.songs.isEmpty()) rescan()
        // Sin permiso el escaneo no corre: apagar el loader para que la
        // pantalla de permiso se vea (antes quedaba girando para siempre).
        else if (!granted) _library.value = _library.value.copy(isLoading = false)
    }

    /** Vuelve a escanear MediaStore (se llama al iniciar y al pedirlo el usuario). */
    // Descargas recientes aún sin indexar por MediaStore: se mantienen en
    // la biblioteca hasta que un escaneo las recoja (ver insertDownloadedSong).
    private val pendingDownloadSongs = mutableMapOf<Long, Pair<Song, Long>>()

    fun rescan(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            // Actualización silenciosa: el loader de pantalla completa solo en
            // la primera carga; si ya hay canciones, se actualiza por debajo
            // sin tumbar la UI (evita flashes al descargar de YouTube).
            val firstLoad = _library.value.songs.isEmpty()
            if (firstLoad) _library.value = _library.value.copy(isLoading = true)
            try {
                val songs = mergePendingDownloads(repository.scanSongs())
                playback.setLibrary(songs)
                _library.value = _library.value.copy(songs = songs, isLoading = false)
                tryRestoreLastPlayback(songs)
                onDone?.invoke()
            } finally {
                // Si el escaneo falla, el loader no se queda pegado.
                _library.value = _library.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Mezcla las descargas pendientes con el escaneo: las que MediaStore
     * ya indexó salen de pendientes (manda la fila escaneada); las que
     * aún no aparecen se conservan para que un rescan temprano no las
     * haga desaparecer de la biblioteca. Caducan a los 15 minutos.
     */
    private fun mergePendingDownloads(scanned: List<Song>): List<Song> {
        val now = System.currentTimeMillis()
        pendingDownloadSongs.entries.removeAll { now - it.value.second > 15 * 60 * 1000 }
        if (pendingDownloadSongs.isEmpty()) return scanned
        // Una descarga pendiente MANDA sobre la fila escaneada: el escáner
        // de Android reescribe TITLE/ARTIST desde el archivo crudo sin
        // tags y la fila queda mal hasta su segunda pasada. Cuando la fila
        // ya trae el mismo título y artista que la descarga, MediaStore
        // está al día y la descarga sale de pendientes.
        val byId = scanned.associateBy { it.id }
        pendingDownloadSongs.entries.removeAll { (id, pending) ->
            byId[id]?.let {
                it.title == pending.first.title && it.artist == pending.first.artist
            } == true
        }
        if (pendingDownloadSongs.isEmpty()) return scanned
        val merged = scanned.map { s -> pendingDownloadSongs[s.id]?.first ?: s } +
            pendingDownloadSongs.values.map { it.first }.filter { it.id !in byId }
        return merged.sortedBy { it.title }
    }

    /** La restauración al abrir solo se intenta una vez por proceso. */
    private var resumeAttempted = false

    /**
     * Reanudar al abrir (v1.0.6): si hay una última reproducción guardada
     * y la canción sigue en la biblioteca, carga su cola en pausa para que
     * el mini reproductor aparezca sin sonar. Si la canción ya no existe,
     * no se hace nada (sin crash).
     */
    private fun tryRestoreLastPlayback(songs: List<Song>) {
        if (resumeAttempted) return
        resumeAttempted = true
        if (playback.state.value.currentSong != null) return
        viewModelScope.launch {
            val last = try {
                settings.lastPlayback.first()
            } catch (e: Exception) {
                null
            } ?: return@launch
            // Si el usuario ya puso algo a sonar mientras se leía, no pisarlo
            if (playback.state.value.currentSong != null) return@launch
            val onlineById = try {
                settings.onlineSongs.first().values.associate { it.id to it.toSong() }
            } catch (e: Exception) {
                emptyMap()
            }
            val byId = songs.associateBy { it.id } + onlineById
            val song = byId[last.songId] ?: return@launch
            val queue = last.queueIds.mapNotNull { byId[it] }
            val index = queue.indexOf(song)
            if (index < 0) return@launch
            playback.restoreQueue(queue, index)
        }
    }

    /** Actualiza el texto de búsqueda en vivo. */
    fun setQuery(q: String) {
        _library.value = _library.value.copy(query = q)
    }

    // ----------------------------- Consultas -----------------------------

    /** FUENTE ÚNICA: canciones con metadatos personalizados aplicados.
     *  Toda la UI debe usar este Flow. Se actualiza automáticamente cuando
     *  cambian la biblioteca o los metadatos personalizados. */
    /** Carpetas ocultas por el usuario. v1.0.7. */
    val hiddenFolders: StateFlow<Set<String>> = settings.hiddenFolders.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptySet()
    )

    val songsFlow: StateFlow<List<Song>> = combine(
        _library,
        customMetadata
    ) { library, custom ->
        if (custom.isEmpty()) library.songs
        else library.songs.map { song ->
            val meta = custom[song.id] ?: return@map song
            song.copy(
                title = meta.first.ifBlank { song.title },
                artist = meta.second.ifBlank { song.artist },
                album = meta.third.ifBlank { song.album }
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Canciones filtradas por búsqueda (usa metadatos efectivos,
     *  sin las canciones de carpetas ocultas). */
    val filteredSongsFlow: StateFlow<List<Song>> = combine(
        songsFlow,
        _library,
        hiddenFolders
    ) { songs, library, hidden ->
        val visible = if (hidden.isEmpty()) songs
        else songs.filter { it.folderPath !in hidden }
        val query = library.query
        val filtered = if (query.isBlank()) visible
        else visible.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
        filtered.sortedBy { it.title.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Artistas agrupados (usa metadatos efectivos). */
    val artistsFlow: StateFlow<List<Artist>> = filteredSongsFlow
        .map { repository.groupByArtist(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Álbumes agrupados (usa metadatos efectivos). */
    val albumsFlow: StateFlow<List<Album>> = filteredSongsFlow
        .map { repository.groupByAlbum(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Carpetas agrupadas (sin las ocultas por el usuario). v1.0.7. */
    val foldersFlow: StateFlow<List<Folder>> = combine(
        filteredSongsFlow,
        hiddenFolders
    ) { songs, hidden ->
        repository.groupByFolder(songs).filterNot { it.path in hidden }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Oculta o muestra una carpeta de la biblioteca. v1.0.7. */
    fun toggleHiddenFolder(path: String) {
        viewModelScope.launch { settings.toggleHiddenFolder(path) }
    }

    /** Isla dinámica activada. v1.0.7. */
    val islandEnabled: StateFlow<Boolean> = settings.islandEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    /** Activa o desactiva la isla dinámica. v1.0.7. */
    fun setIslandEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setIslandEnabled(enabled) }
    }

    /** Aspecto de Dynamic Aural: "auto" o "classic". */
    val islandAppearance: StateFlow<String> = settings.islandAppearance.stateIn(
        viewModelScope, SharingStarted.Eagerly, "auto"
    )

    fun setIslandAppearance(mode: String) {
        viewModelScope.launch { settings.setIslandAppearance(mode) }
    }

    /** Fundido entre canciones activado. */
    val crossfadeEnabled: StateFlow<Boolean> = settings.crossfadeEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCrossfadeEnabled(enabled) }
    }

    /** Duración del fundido en segundos (1..12). */
    val crossfadeSeconds: StateFlow<Int> = settings.crossfadeSeconds.stateIn(
        viewModelScope, SharingStarted.Eagerly, 5
    )

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settings.setCrossfadeSeconds(seconds) }
    }

    /** Mezcla DJ activada (implica fundido; el fundido solo sigue
     *  siendo la opción sencilla). */
    val djMixEnabled: StateFlow<Boolean> = settings.djMixEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun setDjMixEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setDjMixEnabled(enabled) }
    }

    /** Calidad de descarga de YouTube ("data_saver" o "quality"). */
    val downloadQuality: StateFlow<String> = settings.downloadQuality.stateIn(
        viewModelScope, SharingStarted.Eagerly, "data_saver"
    )

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { settings.setDownloadQuality(quality) }
    }

    /** Calidad de streaming ("data_saver" o "quality"). */
    val streamingQuality: StateFlow<String> = settings.streamingQuality.stateIn(
        viewModelScope, SharingStarted.Eagerly, "data_saver"
    )

    fun setStreamingQuality(quality: String) {
        viewModelScope.launch {
            settings.setStreamingQuality(quality)
            // La próxima resolución ya usa la calidad nueva;
            // la canción que suena no se reinicia.
            com.aural.player.youtube.YouTubeStreamResolver.clearAll()
        }
    }

    /** Streaming solo con Wi-Fi. */
    val streamingWifiOnly: StateFlow<Boolean> = settings.streamingWifiOnly.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun setStreamingWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setStreamingWifiOnly(enabled) }
    }

    /** Aviso VPN de la primera búsqueda online ya descartado. */
    val onlineVpnNoticeSeen: StateFlow<Boolean> = settings.onlineVpnNoticeSeen.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    fun dismissOnlineVpnNotice() {
        viewModelScope.launch { settings.setOnlineVpnNoticeSeen() }
    }

    /** Canciones online registradas (id negativo -> registro). */
    val onlineSongs: StateFlow<Map<Long, com.aural.player.data.OnlineSongRecord>> =
        settings.onlineSongs.stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyMap()
        )

    /** Registra un resultado online y devuelve su Song estable. */
    /** Canciones online ya registradas en esta sesión: al
     *  volver a unos resultados cacheados se pintan al
     *  instante, sin pasar por DataStore ni por esqueletos. */
    private val registeredOnlineSongs = mutableMapOf<String, Song>()

    suspend fun registerOnlineSong(result: YTResult): Song {
        registeredOnlineSongs[result.videoId]?.let { return it }
        val song = settings.registerOnlineSong(
            videoId = result.videoId,
            title = result.title,
            artist = result.artist,
            durationMs = result.durationSec * 1000L,
            thumbnailUrl = result.thumbnailUrl
        )
        registeredOnlineSongs[result.videoId] = song
        return song
    }

    suspend fun registerOnlineSongs(results: List<YTResult>): List<Song> =
        results.map { registerOnlineSong(it) }

    /** Lista ya registrada de esos resultados, o null si
     *  falta alguna por registrar. */
    fun peekOnlineSongs(results: List<YTResult>): List<Song>? {
        if (results.isEmpty()) return emptyList()
        val songs = results.map { registeredOnlineSongs[it.videoId] }
        return if (songs.all { it != null }) songs.filterNotNull() else null
    }

    /** Aviso de datos del primer streaming esperando confirmación. */
    private val _streamingNoticePending = MutableStateFlow(false)
    val streamingNoticePending: StateFlow<Boolean> = _streamingNoticePending.asStateFlow()
    private var pendingStreamingAction: (() -> Unit)? = null

    fun confirmStreamingNotice() {
        val action = pendingStreamingAction
        pendingStreamingAction = null
        _streamingNoticePending.value = false
        viewModelScope.launch { settings.setStreamingDataNoticeSeen() }
        action?.invoke()
    }

    fun cancelStreamingNotice() {
        pendingStreamingAction = null
        _streamingNoticePending.value = false
    }

    /** True si hay red con internet (para la búsqueda unificada). */
    fun hasInternet(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Puerta del streaming: respeta "solo Wi-Fi" y muestra el aviso
     * de consumo de datos una sola vez, al primer streaming con
     * datos móviles.
     */
    private fun withStreamingGate(action: () -> Unit) {
        viewModelScope.launch {
            val wifiOnly = settings.streamingWifiOnly.first()
            val unmetered = isUnmeteredNetwork()
            if (wifiOnly && !unmetered) {
                playback.showMessage("Streaming solo con Wi-Fi")
                return@launch
            }
            val seen = settings.streamingDataNoticeSeen.first()
            if (!unmetered && !seen) {
                pendingStreamingAction = action
                _streamingNoticePending.value = true
                return@launch
            }
            action()
        }
    }

    /** Reproduce canciones online (cola mixta: la cola actual se reemplaza). */
    fun playOnlineSongs(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withStreamingGate {
            playback.playQueue(songs, index.coerceIn(0, songs.size - 1), shuffle)
        }
    }

    /** Reproduce una emisora guardada con su URL directa. */
    fun playRadioStation(station: RadioStation) {
        withStreamingGate {
            playback.playQueue(listOf(station.toSong()), 0, false)
        }
    }

    fun addRadioStation(station: RadioStation) {
        viewModelScope.launch { settings.addRadioStation(station) }
    }

    fun removeRadioStation(station: RadioStation) {
        viewModelScope.launch { settings.removeRadioStation(station.id) }
    }

    /**
     * Reproduce el episodio más reciente de un podcast guardado,
     * reanudando en el progreso guardado si lo hay.
     */
    fun playPodcastShow(show: PodcastShow) {
        viewModelScope.launch {
            val episodes = PodcastClient.fetchEpisodes(
                show.feedUrl, limit = 20, settings = settings
            )
            val episode = episodes.firstOrNull() ?: return@launch
            playPodcastEpisode(show, episode)
        }
    }

    /** Reproduce un episodio desde su ficha: descargado suena
     *  local sin puerta; en línea pasa la puerta de streaming
     *  (solo Wi-Fi / aviso de datos). */
    fun playPodcastEpisode(show: PodcastShow, episode: PodcastEpisode) {
        val key = PodcastEpisode.progressKey(show.feedUrl, episode.guid)
        if (podcastDownloads.value.containsKey(key)) {
            startPodcastEpisode(show, episode)
        } else {
            withStreamingGate { startPodcastEpisode(show, episode) }
        }
    }

    private fun startPodcastEpisode(show: PodcastShow, episode: PodcastEpisode) {
        val key = PodcastEpisode.progressKey(show.feedUrl, episode.guid)
        val localUri = podcastDownloads.value[key]?.uri
        val song = episode.toSong(show, localUri)
        podcastEpisodeKeys[song.id] = show.feedUrl to episode.guid
        val saved = podcastProgress.value[key]
        val resumeMs = if (saved != null && !saved.listened) saved.positionMs else 0L
        playback.playQueue(listOf(song), 0, false, startPositionMs = resumeMs)
    }

    fun addPodcastShow(show: PodcastShow) {
        viewModelScope.launch { settings.addPodcastShow(show) }
    }

    fun removePodcastShow(show: PodcastShow) {
        viewModelScope.launch { settings.removePodcastShow(show.feedUrl) }
    }

    /** Añade una online a la cola (al final o como siguiente). */
    fun enqueueOnlineSong(song: Song, next: Boolean) {
        withStreamingGate {
            if (next) playback.enqueueNext(song) else playback.enqueue(song)
        }
    }

    /** BPM por canción (songId -> BPM real). */
    val bpmMap: StateFlow<Map<Long, Float>> = settings.bpmMap.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    /** Aural Light de Dynamic Aural activado. */
    val cometEnabled: StateFlow<Boolean> = settings.cometEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    fun setCometEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCometEnabled(enabled) }
    }

    /** Color de Aural Light: "auto" o un hex de otro tema. */
    val auralLightColor: StateFlow<String> = settings.auralLightColor.stateIn(
        viewModelScope, SharingStarted.Eagerly, "auto"
    )

    fun setAuralLightColor(color: String) {
        viewModelScope.launch { settings.setAuralLightColor(color) }
    }

    // ------------------------- v1.0.8 -------------------------

    /** Orden de pestañas de la biblioteca elegido por el usuario. */
    val libraryTabOrder: StateFlow<List<String>> = settings.libraryTabOrder.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.aural.player.data.defaultTabOrder
    )

    fun setLibraryTabOrder(order: List<String>) {
        viewModelScope.launch { settings.setLibraryTabOrder(order) }
    }

    /** Canciones visibles: con metadatos efectivos y sin las de carpetas ocultas.
     *  Es la base de todas las listas derivadas (recientes, favoritas, del día,
     *  playlists): lo oculto no aparece en ninguna lista. v1.0.9. */
    private fun visibleSongs(): List<Song> {
        val hidden = hiddenFolders.value
        val songs = songsFlow.value
        return if (hidden.isEmpty()) songs else songs.filter { it.folderPath !in hidden }
    }

    // Mapa id→canción con caché: se reconstruye solo cuando cambia la lista
    // de la biblioteca o las carpetas ocultas (evita reconstruirlo en cada
    // llamada durante la composición de las pantallas de playlist).
    // Solo incluye canciones visibles: lo oculto no se resuelve nunca.
    private var libraryByIdCache: Triple<List<Song>, Set<String>, Map<Long, Song>>? = null
    private var libraryByIdOnlineCache: Map<Long, com.aural.player.data.OnlineSongRecord>? = null
    private fun libraryById(): Map<Long, Song> {
        val songs = songsFlow.value
        val hidden = hiddenFolders.value
        val online = onlineSongs.value
        val cached = libraryByIdCache
        if (cached != null && cached.first === songs && cached.second === hidden &&
            libraryByIdOnlineCache === online
        ) return cached.third
        val visible = if (hidden.isEmpty()) songs else songs.filter { it.folderPath !in hidden }
        // Locales visibles + online registradas: playlists y
        // favoritos resuelven ambas sin mezclar espacios de id.
        val merged = visible.associateBy { it.id } +
            online.values.associate { it.id to it.toSong() }
        libraryByIdOnlineCache = online
        return merged.also { libraryByIdCache = Triple(songs, hidden, it) }
    }

    fun favoriteSongs(): List<Song> {
        val order = favoriteOrder.value.withIndex().associate { it.value to it.index }
        val songs = visibleSongs()
        val indexById = songs.withIndex().associate { it.value.id to it.index }
        return libraryById().values
            .filter { favorites.value.contains(it.id) }
            .sortedWith(
                compareBy<Song> { order[it.id] ?: Int.MAX_VALUE }
                    .thenBy { indexById[it.id] ?: Int.MAX_VALUE }
            )
    }

    fun songsOfPlaylist(playlist: Playlist): List<Song> {
        val byId = libraryById()
        return playlist.songIds.mapNotNull { byId[it] }
    }

    /** Canciones de la Playlist del día (resueltas contra la biblioteca). */
    fun dailyPlaylistSongs(): List<Song> {
        val byId = libraryById()
        return (dailyPlaylist.value?.songIds ?: emptyList()).mapNotNull { byId[it] }
    }

    /**
     * Agregadas recientemente: TODAS las canciones VISIBLES ordenadas por fecha
     * de agregado a MediaStore (DATE_ADDED), de la más nueva a la más vieja.
     * El periodo (semana/mes/año/todas) se filtra en la UI. v1.0.9.
     */
    fun recentlyAddedSongs(): List<Song> =
        visibleSongs().sortedByDescending { it.dateAddedSec }

    /**
     * Guarda la Playlist del día como permanente: crea una copia ESTÁTICA
     * en las playlists normales (con el nombre indicado, único). La del
     * día sigue su rotación normal.
     */
    fun saveDailyPlaylistAsPermanent(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val ids = dailyPlaylist.value?.songIds ?: return
        viewModelScope.launch {
            val existing = playlists.value.map { it.name.lowercase() }.toSet()
            var finalName = clean
            var n = 2
            while (existing.contains(finalName.lowercase())) {
                finalName = "$clean ($n)"
                n++
            }
            val id = settings.createPlaylist(finalName)
            settings.setPlaylistSongs(id, ids)
        }
    }

    // ----------------------------- Acciones -----------------------------

    fun playSongs(
        songs: List<Song>,
        index: Int = 0,
        shuffle: Boolean = false,
        fromPlaylistId: String? = null
    ) {
        // Contar la reproducción para la sección "Para ti"
        if (fromPlaylistId != null) {
            viewModelScope.launch { settings.incrementPlaylistPlayCount(fromPlaylistId) }
        }
        playback.playQueue(songs, index, shuffle)
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { settings.toggleFavorite(songId) }
    }

    /** Favorito de una canción online: se registra primero
     *  (id negativo estable) y entra en Favoritas igual que
     *  una local; favoriteSongs() la resuelve desde el
     *  registro online y suena desde ahí. */
    fun toggleFavoriteOnline(result: YTResult) {
        viewModelScope.launch {
            val song = registerOnlineSong(result)
            settings.toggleFavorite(song.id)
        }
    }

    /**
     * Sesión DJ infinita: toda la biblioteca mezclada en una cola que no
     * se acaba nunca — el servicio la reordena sola por tempo a medida que
     * se analizan los BPM y la rellena sola cuando se va acabando. Enciende
     * la Mezcla DJ, cancela la parada programada y arranca la sesión en
     * el servicio. La sesión se detiene al poner cualquier otra cola.
     */
    /** BPM ya calculados (id -> BPM crudo); alimenta la cola inicial de la Sesión DJ. */
    private val bpmMapFlow: StateFlow<Map<Long, Float>> = settings.bpmMap.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    /**
     * Arranca la Sesión DJ infinita. La cola se arma AQUÍ — toda la
     * biblioteca, con BPM primero en cadena de tempo desde una al azar
     * (cada toque = lista nueva) y las sin BPM después, mezcladas — y se
     * pone con playDjQueue, el mismo camino que tocar una canción. El
     * servicio solo recibe el flag y arranca el motor (reorden por tempo
     * + relleno); ya no hay comando que se pueda perder en silencio.
     * Si la biblioteca está vacía no se abre el Now Playing.
     */
    fun startDjSession(): Boolean {
        val songs = library.value.songs
        if (songs.isEmpty()) return false
        viewModelScope.launch { settings.setDjMixEnabled(true) }
        playback.setStopMode(null)
        val bpmById = bpmMapFlow.value
        val analyzedIds = songs.filter { bpmById.containsKey(it.id) }.map { it.id }
        val unanalyzed = songs.filterNot { bpmById.containsKey(it.id) }.shuffled()
        val startId = analyzedIds.randomOrNull()
        val chainedIds = if (startId != null) {
            BpmAnalysis.orderByTempo(analyzedIds, bpmById, BpmAnalysis.foldBpm(bpmById[startId] ?: 120f))
        } else {
            emptyList()
        }
        val byId = songs.associateBy { it.id }
        val orderedSongs = chainedIds.mapNotNull { byId[it] } + unanalyzed
        playback.playDjQueue(orderedSongs)
        playback.setEndlessDj(true)
        return true
    }

    /** Detiene la Sesión DJ infinita (la cola sigue como esté). */
    /**
     * Detener la Sesión DJ: para la MÚSICA por completo (Stop), no solo
     * la sesión — la tarjeta vuelve a mostrar "Nueva sesión".
     */
    fun stopDjSession() {
        playback.stop()
    }

    /** Datos de archivo de una canción (para "Información de la canción"). */
    suspend fun loadSongFileInfo(song: Song): AudioFileInfo? = withContext(Dispatchers.IO) {
        try {
            BpmAnalysis.readAudioFileInfo(getApplication(), Uri.parse(song.uri))
        } catch (_: Exception) {
            null
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { settings.createPlaylist(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { settings.deletePlaylist(id) }
    }

    /**
     * Prepara la eliminación de canciones (borra los ARCHIVOS del
     * dispositivo). En API 30+ devuelve el PendingIntent del sistema: la
     * UI lo lanza y el usuario confirma en el diálogo del sistema; al
     * volver con RESULT_OK hay que llamar a [confirmDelete]. En API <30
     * borra directo y devuelve null (ya se llamó a [confirmDelete]).
     * v1.0.9.
     */
    fun buildDeleteRequest(ids: List<Long>): PendingIntent? {
        if (ids.isEmpty()) return null
        val byId = libraryById()
        val uris = ids.mapNotNull { byId[it]?.uri?.let { uri -> Uri.parse(uri) } }
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, uris)
            } catch (e: Exception) {
                null
            }
        } else {
            viewModelScope.launch {
                var deleted = 0
                uris.forEach { uri ->
                    try {
                        if (getApplication<Application>().contentResolver.delete(uri, null, null) > 0) deleted++
                    } catch (_: Exception) {
                    }
                }
                if (deleted > 0) confirmDelete(ids)
            }
            null
        }
    }

    /**
     * Tras confirmar la eliminación (diálogo del sistema o borrado
     * directo): limpia favoritos, playlists, metadatos, portadas y letras
     * de esos ids, las saca de la cola y reescanea la biblioteca. v1.0.9.
     */
    fun confirmDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        playback.removeSongsFromQueue(set)
        viewModelScope.launch {
            settings.removeFavorites(set)
            settings.removeSongsFromAllPlaylists(set)
            // Un solo edit para metadata+portada+letra de todas (antes:
            // 3 rewrites de ajustes por canción).
            settings.clearSongData(ids)
            rescan()
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        viewModelScope.launch { settings.renamePlaylist(id, newName) }
    }

    fun setLrcForSong(songId: Long, lrcPath: String) {
        viewModelScope.launch { settings.setLrcForSong(songId, lrcPath) }
    }

    fun removeLrcForSong(songId: Long) {
        viewModelScope.launch { settings.removeLrcForSong(songId) }
    }

    /**
     * ¿Título y artista sirven para buscar la letra? MediaStore marca
     * los desconocidos con "Artista misterioso" (título y artista).
     */
    private fun hasSearchableMetadata(title: String, artist: String): Boolean {
        val badTitle = title.isBlank() || title == "Artista misterioso" || title == "Sin título"
        val badArtist = artist.isBlank() || artist == "Artista misterioso" || artist == "Artista desconocido"
        return !badTitle && !badArtist
    }

    /**
     * Letra automática al abrir el Now Playing (como en PC): si no hay
     * una guardada y los metadatos sirven, se busca en línea y se guarda.
     * No se busca al reproducir en segundo plano. La UI se entera sola:
     * guardar actualiza lrcMap y el NP recarga la línea.
     */
    fun ensureLyrics(song: Song) {
        if (lrcMap.value.containsKey(song.id)) return
        if (!hasSearchableMetadata(song.title, song.artist)) return
        viewModelScope.launch {
            searchAndSaveLyrics(song.id, song.title, song.artist, song.durationMs)
        }
    }

    /** Posición en vivo del reproductor (sin esperar al ticker de 500 ms). */
    fun currentPositionMs(): Long = playback.currentPositionMs()

    /**
     * Metadatos de una descarga de YouTube cuando NO se pudieron
     * incrustar en el archivo (p. ej. .webm, que jaudiotagger no
     * escribe): quedan en los datos propios de la app, que es lo
     * que se ve en pantalla, y la miniatura como portada propia.
     */
    fun applyYouTubeMetadataFallback(songId: Long, title: String, artist: String, thumbnailUrl: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setCustomMetadata(songId, title, artist, "")
            if (!thumbnailUrl.isNullOrBlank()) {
                val app = getApplication<Application>()
                SongCoverStore.saveFromUrl(app, songId, thumbnailUrl)?.let { local ->
                    settings.setCustomCover(songId, local)
                }
            }
        }
    }

    /**
     * Busca la letra en línea (LRCLIB + respaldo) y la guarda asociada a la canción.
     * La duración (si se pasa) afina la búsqueda: LRCLIB la usa para desempatar.
     * Devuelve true si se encontró y guardó.
     */
    suspend fun searchAndSaveLyrics(songId: Long, track: String, artist: String, durationMs: Long? = null): Boolean {
        return try {
            val durationSec = durationMs?.takeIf { it > 0 }?.let { (it / 1000).toInt() }
            val lrcContent = com.aural.player.data.LrcLibClient.searchLyrics(artist, track, durationSec)
                ?.takeIf { it.isNotBlank() }
                ?: return false
            // Guardar el .lrc en almacenamiento interno (hilo IO: antes
            // se escribía en el hilo principal).
            val file = java.io.File(
                getApplication<android.app.Application>().filesDir,
                "lyrics_$songId.lrc"
            )
            withContext(Dispatchers.IO) { file.writeText(lrcContent) }
            settings.setLrcForSong(songId, file.absolutePath)
            true
        } catch (e: Exception) {
            android.util.Log.e("AuralViewModel", "Error guardando letra: ${e.message}")
            false
        }
    }

    /**
     * Escanea el dispositivo buscando archivos .lrc.
     * Devuelve lista de (nombre, ruta).
     */
    suspend fun scanLrcFiles(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val resolver = getApplication<Application>().contentResolver
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                android.provider.MediaStore.Files.FileColumns.DATA
            )
            val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%.lrc")
            resolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                projection, selection, args, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val path = cursor.getString(dataIdx) ?: continue
                    result.add(name to path)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuralViewModel", "Error escaneando .lrc: ${e.message}")
        }
        result.sortedBy { it.first.lowercase() }
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch { settings.addToPlaylist(playlistId, songId) }
    }

    /** Añade varias canciones a una playlist (selección múltiple). v1.0.7. */
    fun addAllToPlaylist(playlistId: String, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch { settings.addAllToPlaylist(playlistId, songIds) }
    }

    /** Marca varias canciones como favoritas (selección múltiple). v1.0.7. */
    fun addFavorites(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch { settings.addFavorites(songIds) }
    }

    /**
     * Agrega una canción recién descargada a la playlist "Descargas", por su
     * ID exacto de MediaStore. Crea la playlist si no existe. La canción se
     * inserta en la biblioteca al instante (insertDownloadedSong): no se
     * espera a que MediaStore la indexe, porque el escaneo la descarta
     * mientras no tenga duración.
     */
    fun addDownloadToPlaylist(
        fileName: String?,
        mediaId: Long,
        result: YTResult,
        tagsEmbedded: Boolean
    ) {
        if (mediaId <= 0L) return
        viewModelScope.launch {
            var playlistId = playlists.value.firstOrNull { it.name == "Descargas" }?.id
            if (playlistId == null) {
                settings.createPlaylist("Descargas")
                // La tienda deduplica por nombre; esperar a verla en el estado
                var espera = 0
                while (playlistId == null && espera < 10) {
                    kotlinx.coroutines.delay(200)
                    playlistId = playlists.value.firstOrNull { it.name == "Descargas" }?.id
                    espera++
                }
            }
            val destino = playlistId ?: return@launch
            // La canción entra en la biblioteca AL INSTANTE (ver
            // insertDownloadedSong): no se espera al escaneo.
            insertDownloadedSong(mediaId, result, tagsEmbedded)
            addToPlaylist(destino, mediaId)
        }
    }

    /**
     * Inserta una descarga recién terminada en la biblioteca en memoria
     * con su metadata correcta, sin esperar a MediaStore. El insert pone
     * título y artista bien, pero el escáner de Android los reescribe
     * desde el archivo crudo sin tags (título = nombre del archivo,
     * artista desconocido) y los tags correctos solo llegan a la fila en
     * su segunda pasada. Por eso antes la biblioteca mostraba la metadata
     * mal hasta reiniciar o descargar otra. La duración sale del resultado
     * de YouTube; la portada, de la miniatura cuando los tags quedaron
     * incrustados (la miniatura solo cubre el hueco hasta que MediaStore
     * indexe el archivo y su portada). Queda en pendientes para que un
     * rescan temprano no la borre ni la devuelva con la metadata mala.
     */
    private fun insertDownloadedSong(mediaId: Long, result: YTResult, tagsEmbedded: Boolean) {
        val song = Song(
            id = mediaId,
            title = result.title,
            artist = result.artist,
            album = "",
            albumId = 0L,
            durationMs = result.durationSec.coerceAtLeast(0L) * 1000L,
            uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
            ).toString(),
            albumArtUri = null,
            folderPath = "Music/Aural/",
            dateAddedSec = System.currentTimeMillis() / 1000L
        )
        val songs = (_library.value.songs.filter { it.id != mediaId } + song)
            .sortedBy { it.title }
        _library.value = _library.value.copy(songs = songs)
        playback.refreshSongMetadata(song)
        pendingDownloadSongs[mediaId] = song to System.currentTimeMillis()
        if (tagsEmbedded && !result.thumbnailUrl.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                SongCoverStore.saveFromUrl(
                    getApplication<Application>(), mediaId, result.thumbnailUrl
                )?.let { local ->
                    settings.setCustomCover(mediaId, local)
                }
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch { settings.removeFromPlaylist(playlistId, songId) }
    }

    fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { settings.moveSongInPlaylist(playlistId, fromIndex, toIndex) }
    }

    /** Guarda el orden completo de una playlist (la UI reordena en local y confirma al soltar). */
    fun setPlaylistOrder(playlistId: String, songIds: List<Long>) {
        viewModelScope.launch { settings.setPlaylistSongs(playlistId, songIds) }
    }

    // ------------------- Importar / exportar playlists (.m3u) -------------------

    private fun toastOnMain(context: android.content.Context, msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Exporta una playlist a un archivo .m3u en la carpeta Descargas. */
    fun exportPlaylist(context: android.content.Context, playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = playlists.value.firstOrNull { it.id == playlistId }
                ?: return@launch
            val songs = songsOfPlaylist(playlist)
            if (songs.isEmpty()) {
                toastOnMain(context, "La playlist está vacía")
                return@launch
            }
            try {
                val m3u = buildM3u(songs)
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${playlist.name}.m3u")
                    put(MediaStore.Downloads.MIME_TYPE, "audio/x-mpegurl")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@launch
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(m3u.toByteArray())
                }
                toastOnMain(context, "Playlist exportada a Descargas")
            } catch (e: Exception) {
                android.util.Log.e("AuralViewModel", "Error exportando: ${e.message}")
                toastOnMain(context, "Error al exportar la playlist")
            }
        }
    }

    /** Importa un archivo .m3u: crea una playlist con las canciones que existan en la biblioteca. */
    fun importM3u(context: android.content.Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText() ?: return@launch
                // Nombre: el del archivo elegido (sin extensión); si el
                // archivo trae título #PLAYLIST, ese; si no, fecha.
                val fileName = try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                } catch (e: Exception) { null }
                    ?.let {
                        if (it.endsWith(".m3u", ignoreCase = true) ||
                            it.endsWith(".m3u8", ignoreCase = true)
                        ) it.substringBeforeLast(".") else it
                    }
                    ?.takeIf { it.isNotBlank() }
                val playlistTitle = content.lineSequence()
                    .firstOrNull { it.startsWith("#PLAYLIST:") }
                    ?.removePrefix("#PLAYLIST:")?.trim()
                    ?.takeIf { it.isNotBlank() }
                val baseName = fileName ?: playlistTitle ?: run {
                    val date = java.text.SimpleDateFormat(
                        "dd/MM", java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    "Importada $date"
                }
                // Evitar mezclar: si el nombre existe, «Nombre 2», «Nombre 3»…
                var playlistName = baseName
                var suffix = 2
                val existingNames = playlists.value.map { it.name }.toSet()
                while (playlistName in existingNames) {
                    playlistName = "$baseName $suffix"
                    suffix++
                }
                val entries = parseM3u(content)
                if (entries.isEmpty()) {
                    toastOnMain(context, "No se encontraron canciones en el archivo")
                    return@launch
                }
                val allSongs = _library.value.songs
                val matchedIds = entries.mapNotNull { entry ->
                    allSongs.firstOrNull { song ->
                        song.title.equals(entry.title, ignoreCase = true) ||
                            (entry.artist.isNotBlank() &&
                                song.title.contains(entry.title, ignoreCase = true) &&
                                song.artist.contains(entry.artist, ignoreCase = true))
                    }?.id
                }.distinct()
                if (matchedIds.isEmpty()) {
                    toastOnMain(context, "Ninguna canción del archivo está en tu biblioteca")
                    return@launch
                }
                val newId = settings.createPlaylist(playlistName)
                matchedIds.forEach { settings.addToPlaylist(newId, it) }
                toastOnMain(
                    context,
                    "Playlist importada: ${matchedIds.size} de ${entries.size} canciones"
                )
            } catch (e: Exception) {
                android.util.Log.e("AuralViewModel", "Error importando: ${e.message}")
                toastOnMain(context, "Error al importar el archivo")
            }
        }
    }

    // ------------------- Búsqueda por audio -------------------

    private val _showAudioSearch = MutableStateFlow(false)
    val showAudioSearch: StateFlow<Boolean> = _showAudioSearch.asStateFlow()

    fun openAudioSearch() { _showAudioSearch.value = true }
    fun closeAudioSearch() { _showAudioSearch.value = false }

    /**
     * Solicitud pendiente de buscar en YouTube (viene del reconocimiento
     * por audio). LibraryScreen la consume y abre la pestaña Descargas.
     */
    private val _audioSearchRequest = MutableStateFlow<String?>(null)
    val audioSearchRequest: StateFlow<String?> = _audioSearchRequest.asStateFlow()

    fun requestYouTubeSearch(query: String) { _audioSearchRequest.value = query }
    fun consumeAudioSearchRequest() { _audioSearchRequest.value = null }

    // ---- Temporizador de suspensión ----
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        playback.setStopMode(null)
        _sleepTimerMinutes.value = minutes
        sleepTimerJob = viewModelScope.launch {
            kotlinx.coroutines.delay(minutes * 60_000L)
            if (playerState.value.isPlaying) {
                playback.togglePlayPause()
            }
            _sleepTimerMinutes.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMinutes.value = null
        playback.setStopMode(null)
    }

    /** Parada al terminar la canción o la lista (cancela el temporizador por minutos). */
    fun setStopMode(mode: StopMode?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMinutes.value = null
        playback.setStopMode(mode)
    }

    // ---- Despertador ----

    /** Guarda la alarma y la programa (o la cancela si quedó desactivada). */
    fun saveAlarm(config: AlarmConfig) {
        viewModelScope.launch {
            settings.setAlarm(config)
            if (config.enabled) {
                AlarmScheduler.schedule(auralApp, config)
            } else {
                AlarmScheduler.cancel(auralApp)
            }
        }
    }

    // ---- Tu Top 10 ----

    /** Recarga las 10 canciones más escuchadas. */
    fun refreshTopSongs() {
        viewModelScope.launch {
            _topSongs.value = playHistory.getTopSongs()
        }
    }

    /** Recarga los 5 artistas más escuchados. */
    fun refreshTopArtists() {
        viewModelScope.launch {
            _topArtists.value = playHistory.getTopArtists()
        }
    }

    /** Las 10 últimas canciones que sonaron (para "Escuchadas recientemente" del Inicio). */
    private val _recentSongs = MutableStateFlow<List<Song>>(emptyList())
    val recentSongs: StateFlow<List<Song>> = _recentSongs.asStateFlow()

    /** Recarga las canciones recientes: del historial a la biblioteca (las que ya no existen, fuera). */
    fun refreshRecentSongs() {
        viewModelScope.launch {
            // Sin repetidas: la fila de recientes usa el id como
            // clave y un duplicado crashearía al hacer scroll.
            val ids = playHistory.getRecentSongs().map { it.songId }.distinct()
            val byId = library.value.songs.associateBy { it.id }
            _recentSongs.value = ids.mapNotNull { byId[it] }
        }
    }

    /** Estadísticas de escucha de los últimos 7 días (para "Tu semana en Aural"). */
    private val _weekStats = MutableStateFlow(WeekStats())
    val weekStats: StateFlow<WeekStats> = _weekStats.asStateFlow()

    /** Recarga las estadísticas de la semana. */
    fun refreshWeekStats() {
        viewModelScope.launch {
            _weekStats.value = playHistory.getWeekStats()
        }
    }

    /** Reproduce toda la biblioteca mezclada. */
    fun playAllShuffled() {
        val songs = library.value.songs
        if (songs.isEmpty()) return
        playback.playQueue(songs.shuffled(), 0, false)
    }

    // ---- Perfil (nombre y cumpleaños) ----

    /** Guarda nombre y cumpleaños (birthdayMd null = sin fecha). */
    fun saveUserProfile(name: String, birthdayMd: String?) {
        viewModelScope.launch { settings.setUserProfile(name, birthdayMd) }
    }

    /** Marca la tarjeta de bienvenida como vista. */
    fun setProfileAsked() {
        viewModelScope.launch { settings.setProfileAsked() }
    }

    /** Borra nombre y cumpleaños del teléfono. */
    fun clearUserProfile() {
        viewModelScope.launch { settings.clearUserProfile() }
    }

    /** Marca el día en que se mostró el modal de cumpleaños. */
    fun markBirthdayShown(dateKey: String) {
        viewModelScope.launch { settings.setBirthdayShown(dateKey) }
    }

    // ---- Novedades ----

    /** Marca una función nueva como vista (su puntito desaparece). */
    fun markFeatureSeen(key: String) {
        viewModelScope.launch { settings.markFeatureSeen(key) }
    }

    /** Guarda la versión de las novedades mostradas. */
    fun setLastNewsVersion(version: String) {
        viewModelScope.launch { settings.setLastNewsVersion(version) }
    }

    // ---- Respaldo ----

    /** Biblioteca actual para el respaldo (tags originales); re-escanea si aún no cargó. */
    private suspend fun currentSongsForBackup(): List<Song> {
        val loaded = songsFlow.value
        return loaded.ifEmpty { repository.scanSongs() }
    }

    /** Exporta el respaldo como JSON v2 (lo escribe quien llama). */
    suspend fun exportBackupJson(): String {
        val history = playHistory.readRawJson().ifBlank { null }
        return settings.exportBackupJson(currentSongsForBackup(), history)
    }

    /** Importa un respaldo v1 o v2; false si el JSON no es válido. */
    suspend fun importBackupJson(json: String): Boolean {
        val result = settings.importBackupJson(json, currentSongsForBackup())
        if (result.ok) {
            result.historyJson?.let { playHistory.writeRawJson(it) }
            // Si el respaldo traía una alarma, hay que reprogramarla.
            val config = settings.alarm.first()
            if (config != null && config.enabled) {
                AlarmScheduler.schedule(auralApp, config)
            } else {
                AlarmScheduler.cancel(auralApp)
            }
        }
        return result.ok
    }
}
