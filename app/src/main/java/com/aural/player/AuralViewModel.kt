// ViewModel principal: biblioteca, búsqueda, playlists, favoritos y reproducción
package com.aural.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aural.player.data.Album
import com.aural.player.data.Artist
import com.aural.player.data.Folder
import com.aural.player.data.Playlist
import com.aural.player.data.Song
import com.aural.player.data.buildM3u
import com.aural.player.data.parseM3u
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.aural.player.playback.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    val lrcMap: StateFlow<Map<Long, String>> = settings.lrcMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    init {
        // Crear la playlist "Descargas" si no existe (como Favoritas, desde la instalación)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // esperar a que cargue settings
            if (playlists.value.none { it.name == "Descargas" }) {
                settings.createPlaylist("Descargas")
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
        viewModelScope.launch { settings.setAppTheme(themeName) }
    }

    /** Portadas personalizadas: songId -> URL. */
    val customCovers: StateFlow<Map<Long, String>> = settings.customCovers.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    fun setCustomCover(songId: Long, coverUrl: String) {
        viewModelScope.launch { settings.setCustomCover(songId, coverUrl) }
    }


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

    fun folders(): List<Folder> = repository.groupByFolder(_library.value.filteredSongs)

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

    fun setLrcForSong(songId: Long, lrcPath: String) {
        viewModelScope.launch { settings.setLrcForSong(songId, lrcPath) }
    }

    fun removeLrcForSong(songId: Long) {
        viewModelScope.launch { settings.removeLrcForSong(songId) }
    }

    /**
     * Busca la letra en línea (LRCLIB) y la guarda asociada a la canción.
     * Devuelve true si se encontró y guardó.
     */
    suspend fun searchAndSaveLyrics(songId: Long, track: String, artist: String): Boolean {
        return try {
            val lrcContent = com.aural.player.data.LrcLibClient.searchLyrics(artist, track)
                ?: return false
            // Guardar el .lrc en almacenamiento interno
            val file = java.io.File(
                getApplication<android.app.Application>().filesDir,
                "lyrics_$songId.lrc"
            )
            file.writeText(lrcContent)
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
                val date = java.text.SimpleDateFormat(
                    "dd/MM", java.util.Locale.getDefault()
                ).format(java.util.Date())
                val newId = settings.createPlaylist("Importada $date")
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
