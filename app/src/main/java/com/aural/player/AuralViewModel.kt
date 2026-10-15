// ViewModel principal: biblioteca, búsqueda, playlists, favoritos y reproducción
package com.aural.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aural.player.data.Album
import com.aural.player.data.Artist
import com.aural.player.data.Playlist
import com.aural.player.data.Song
import com.aural.player.playback.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la interfaz de la biblioteca. */
data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false,
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

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    /** Playlists persistidas. */
    val playlists: StateFlow<List<Playlist>> = settings.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    /** IDs de canciones favoritas. */
    val favorites: StateFlow<Set<Long>> = settings.favorites.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()
    )

    // --------------------------- Permisos y escaneo ---------------------------

    /** Indica al ViewModel que el permiso fue concedido; lanza el escaneo. */
    fun onPermissionResult(granted: Boolean) {
        _library.value = _library.value.copy(permissionGranted = granted)
        if (granted && _library.value.songs.isEmpty()) rescan()
    }

    /** Vuelve a escanear MediaStore (se llama al iniciar y al pedirlo el usuario). */
    fun rescan() {
        viewModelScope.launch {
            _library.value = _library.value.copy(isLoading = true)
            val songs = repository.scanSongs()
            playback.setLibrary(songs)
            _library.value = _library.value.copy(songs = songs, isLoading = false)
        }
    }

    /** Actualiza el texto de búsqueda en vivo. */
    fun setQuery(q: String) {
        _library.value = _library.value.copy(query = q)
    }

    // ----------------------------- Consultas -----------------------------

    fun artists(): List<Artist> = repository.groupByArtist(_library.value.filteredSongs)

    fun albums(): List<Album> = repository.groupByAlbum(_library.value.filteredSongs)

    fun favoriteSongs(): List<Song> =
        _library.value.songs.filter { favorites.value.contains(it.id) }

    fun songsOfPlaylist(playlist: Playlist): List<Song> {
        val byId = _library.value.songs.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    // ----------------------------- Acciones -----------------------------

    fun playSongs(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) =
        playback.playQueue(songs, index, shuffle)

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { settings.toggleFavorite(songId) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { settings.createPlaylist(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { settings.deletePlaylist(id) }
    }

    fun renamePlaylist(id: String, newName: String) {
        viewModelScope.launch { settings.renamePlaylist(id, newName) }
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch { settings.addToPlaylist(playlistId, songId) }
    }

    /**
     * Agrega una canción recién descargada a la playlist "Descargas".
     * Crea la playlist si no existe. Busca la canción por nombre de archivo.
     */
    fun addDownloadToPlaylist(fileName: String) {
        viewModelScope.launch {
            rescan()
            kotlinx.coroutines.delay(500)
            val songs = _library.value.songs
            val baseName = fileName.substringBeforeLast(".").lowercase()
            // Buscar por título contenido en el nombre del archivo
            var song = songs.firstOrNull {
                it.title.lowercase() in baseName || baseName in it.title.lowercase()
            }
            // Fallback: la de mayor ID (más reciente)
            if (song == null) {
                song = songs.maxByOrNull { it.id }
            }
            if (song == null) return@launch

            val target = song
            // Buscar o crear la playlist "Descargas"
            var playlistId = playlists.value.firstOrNull { it.name == "Descargas" }?.id
            if (playlistId == null) {
                createPlaylist("Descargas")
                kotlinx.coroutines.delay(500)
                playlistId = playlists.value.firstOrNull { it.name == "Descargas" }?.id
            }
            if (playlistId != null) {
                addToPlaylist(playlistId, target.id)
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch { settings.removeFromPlaylist(playlistId, songId) }
    }

    fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { settings.moveSongInPlaylist(playlistId, fromIndex, toIndex) }
    }

    // ---- Temporizador de suspensión ----
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
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
    }
}
