package com.aural.player.desktop

import com.aural.player.shared.AppTheme
import com.aural.player.shared.DesktopPrefs
import com.aural.player.shared.LibraryData
import com.aural.player.shared.LibraryStore
import com.aural.player.shared.LrcLibClient
import com.aural.player.shared.PlayHistoryStore
import com.aural.player.shared.PlaybackState
import com.aural.player.shared.Playlist
import com.aural.player.shared.PrefsStore
import com.aural.player.shared.Song
import com.aural.player.shared.TopSong
import java.io.File
import javax.swing.JFileChooser
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado de la app de escritorio: biblioteca escaneada, cola y
 * transporte. El motor es [VlcPlayer]; la UI solo observa el estado
 * y manda intenciones.
 */
class DesktopPlayer(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val vlc = VlcPlayer(
        onFinished = { next(auto = true) },
        onError = { path -> _state.update { it.copy(error = "No se pudo reproducir: $path. Registro VLC: ${VlcPlayer.LOG_PATH}") } }
    )
    private var ticker: Job? = null

    // Datos locales de la PC: historial de escucha (Top 10),
    // preferencias (tema, volumen, silencio) y biblioteca
    // (carpetas + playlists). Misma carpeta.
    private val dataDir = File(System.getProperty("user.home"), ".auralplayer").absolutePath
    private val history = PlayHistoryStore(dataDir)
    private val prefs = PrefsStore(dataDir)
    private val library = LibraryStore(dataDir)

    // Segundos escuchados pendientes de registrar: se acumulan en
    // el ticker y se guardan cada 30 s o al cambiar de canción.
    private var pendingSong: Song? = null
    private var pendingSeconds = 0.0

    /** Escanea las carpetas de música en segundo plano e inicializa VLC. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                // Primero las preferencias guardadas (tema, volumen,
                // silencio), para que la ventana abra como quedó.
                try {
                    prefs.load()?.let { saved ->
                        val theme = AppTheme.entries.firstOrNull { it.name == saved.themeName }
                            ?: AppTheme.BOSQUE
                        _state.update {
                            it.copy(
                                theme = theme,
                                volume = saved.volume.coerceIn(0, 100),
                                muted = saved.muted
                            )
                        }
                    }
                } catch (t: Throwable) {
                    // Sin preferencias guardadas: se abre con los valores base
                }
                // Carpetas + playlists guardadas (o la carpeta Música
                // por defecto la primera vez).
                val savedLibrary = try { library.load() } catch (t: Throwable) { null }
                val folders = savedLibrary?.folders
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(LibraryScanner.defaultMusicDir().absolutePath)
                _state.update {
                    it.copy(folders = folders, playlists = savedLibrary?.playlists ?: emptyList())
                }
                // Después el escaneo (en tandas: la lista se va
                // llenando) y después VLC, para que un fallo del
                // motor nunca deje la pantalla en blanco.
                val songs = scanFolders(folders)
                val vlcOk = vlc.init()
                if (vlcOk) {
                    vlc.setVolume(_state.value.volume)
                    if (_state.value.muted) vlc.toggleMute()
                }
                _state.update {
                    it.copy(
                        songs = songs,
                        queue = songs,
                        scanning = false,
                        error = when {
                            !vlcOk && vlc.initError != null ->
                                "No se pudo iniciar VLC: ${vlc.initError} (${vlc.diagnostics})"
                            !vlcOk ->
                                "No se encontraron las librerías de VLC. ${vlc.diagnostics}"
                            else -> null
                        }
                    )
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                _state.update {
                    it.copy(scanning = false, error = "No se pudo leer tu música: ${t.message}")
                }
            }
        }
    }

    /** Escanea todas las carpetas, sin duplicar rutas (pueden solaparse). */
    private fun scanFolders(folders: List<String>): List<Song> {
        val found = LinkedHashMap<String, Song>()
        for (folder in folders) {
            val dir = File(folder)
            if (!dir.isDirectory) continue
            LibraryScanner.scan(dir) { batch ->
                var changed = false
                for (song in batch) {
                    if (found.put(song.filePath, song) == null) changed = true
                }
                if (changed) {
                    _state.update {
                        it.copy(
                            songs = found.values.sortedBy { s -> s.title.lowercase() },
                            scanning = true
                        )
                    }
                }
            }
        }
        return found.values.sortedBy { it.title.lowercase() }
    }

    /** Re-escanea tras añadir o quitar una carpeta, sin perder la reproducción. */
    private fun rescan(folders: List<String>) {
        scope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(scanning = true) }
                val playingPath = _state.value.currentSong?.filePath
                val wasPlaying = _state.value.isPlaying
                val songs = scanFolders(folders)
                val index = songs.indexOfFirst { it.filePath == playingPath }
                if (playingPath != null && index < 0) vlc.stop()
                _state.update {
                    it.copy(
                        songs = songs,
                        queue = songs,
                        scanning = false,
                        currentIndex = if (playingPath != null) index else it.currentIndex,
                        isPlaying = if (playingPath != null) (index >= 0 && wasPlaying) else it.isPlaying,
                        positionMs = if (playingPath != null && index < 0) 0L else it.positionMs
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(scanning = false, error = "No se pudo leer tu música: ${t.message}")
                }
            }
        }
    }

    /** Libera el motor y detiene el ticker. Se llama al cerrar la ventana. */
    fun dispose() {
        flushHistory()
        ticker?.cancel()
        ticker = null
        vlc.release()
    }

    /** Reproduce la canción [index] de la lista [list]: esa lista queda como cola. */
    fun playFrom(list: List<Song>, index: Int) {
        val song = list.getOrNull(index) ?: return
        flushHistory()
        if (!vlc.play(song.filePath)) {
            _state.update { it.copy(error = "No se pudo reproducir ${song.title}") }
            return
        }
        _state.update {
            it.copy(
                queue = list,
                currentIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = song.durationMs,
                error = null
            )
        }
        startTicker()
    }

    fun toggle() {
        val current = _state.value
        if (current.currentIndex < 0) {
            if (current.songs.isNotEmpty()) playFrom(current.songs, 0)
            return
        }
        vlc.toggle()
        _state.update { it.copy(isPlaying = vlc.isPlaying) }
    }

    fun next(auto: Boolean = false) {
        val current = _state.value
        val nextIndex = current.currentIndex + 1
        if (nextIndex < current.queue.size) {
            playFrom(current.queue, nextIndex)
        } else if (auto) {
            vlc.stop()
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    fun previous() {
        val current = _state.value
        if (current.positionMs > 3000L) {
            playFrom(current.queue, current.currentIndex)
        } else if (current.currentIndex > 0) {
            playFrom(current.queue, current.currentIndex - 1)
        }
    }

    fun seek(fraction: Float) {
        vlc.seek(fraction)
        _state.update {
            it.copy(positionMs = (fraction * it.durationMs).toLong().coerceAtLeast(0L))
        }
    }

    fun setTheme(theme: AppTheme) {
        _state.update { it.copy(theme = theme) }
        persistPrefs()
    }

    fun setVolume(value: Int) {
        val clamped = value.coerceIn(0, 100)
        vlc.setVolume(clamped)
        _state.update { it.copy(volume = clamped) }
        persistPrefs()
    }

    fun toggleMute() {
        _state.update { it.copy(muted = vlc.toggleMute()) }
        persistPrefs()
    }

    /** Busca la letra de una canción en línea (LRCLIB + respaldo). */
    suspend fun fetchLyrics(song: Song): String? {
        return try {
            LrcLibClient.searchLyrics(song.artist, song.title, (song.durationMs / 1000).toInt())
        } catch (t: Throwable) {
            null
        }
    }

    /** Las canciones más escuchadas (Top 10) según el historial local. */
    suspend fun topSongs(limit: Int = 10): List<TopSong> {
        return try {
            history.getTopSongs(limit)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Abre el explorador para añadir una carpeta de música y re-escanea. */
    fun addFolder() {
        val picked = pickFolder() ?: return
        val normalized = picked.trimEnd('/', '\\')
        val current = _state.value.folders
        if (current.any { it.trimEnd('/', '\\').equals(normalized, ignoreCase = true) }) return
        val updated = current + picked
        _state.update { it.copy(folders = updated) }
        saveLibrary(updated, _state.value.playlists)
        rescan(updated)
    }

    /** Quita una carpeta del escaneo (las canciones dejan de listarse) y re-escanea. */
    fun removeFolder(path: String) {
        val updated = _state.value.folders - path
        if (updated.size == _state.value.folders.size) return
        _state.update { it.copy(folders = updated) }
        saveLibrary(updated, _state.value.playlists)
        rescan(updated)
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val playlist = Playlist(
            id = System.currentTimeMillis().toString() + "-" + Random.nextInt(1000),
            name = trimmed
        )
        val updated = _state.value.playlists + playlist
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    /** Crea la playlist y añade la canción en el mismo gesto. */
    fun createPlaylistAndAdd(name: String, song: Song) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val playlist = Playlist(
            id = System.currentTimeMillis().toString() + "-" + Random.nextInt(1000),
            name = trimmed,
            songPaths = listOf(song.filePath)
        )
        val updated = _state.value.playlists + playlist
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    fun renamePlaylist(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val updated = _state.value.playlists.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    fun deletePlaylist(id: String) {
        val updated = _state.value.playlists.filter { it.id != id }
        if (updated.size == _state.value.playlists.size) return
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    fun addToPlaylist(id: String, song: Song) {
        val updated = _state.value.playlists.map {
            if (it.id == id && song.filePath !in it.songPaths) {
                it.copy(songPaths = it.songPaths + song.filePath)
            } else {
                it
            }
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    fun removeFromPlaylist(id: String, song: Song) {
        val updated = _state.value.playlists.map {
            if (it.id == id) it.copy(songPaths = it.songPaths - song.filePath) else it
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary(_state.value.folders, updated)
    }

    private fun saveLibrary(folders: List<String>, playlists: List<Playlist>) {
        scope.launch(Dispatchers.IO) {
            try {
                library.save(LibraryData(folders, playlists))
            } catch (t: Throwable) {
                // Guardar la biblioteca nunca debe romper la app
            }
        }
    }

    /** Selector de carpetas del sistema (Swing, solo directorios). */
    private fun pickFolder(): String? {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Elige una carpeta con música"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun persistPrefs() {
        val s = _state.value
        scope.launch(Dispatchers.IO) {
            prefs.save(DesktopPrefs(s.theme.name, s.volume, s.muted))
        }
    }

    /** Guarda los segundos acumulados en el historial y limpia el conteo. */
    private fun flushHistory() {
        val song = pendingSong
        val whole = pendingSeconds.toLong()
        pendingSong = null
        pendingSeconds = 0.0
        if (song == null || whole <= 0) return
        scope.launch(Dispatchers.IO) {
            try {
                history.record(song.id, song.title, song.artist, whole)
            } catch (t: Throwable) {
                // El historial nunca debe romper la reproducción
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(500L)
                val vlcDuration = vlc.durationMs
                _state.update {
                    it.copy(
                        isPlaying = vlc.isPlaying,
                        positionMs = vlc.positionMs,
                        durationMs = if (vlcDuration > 0) vlcDuration else it.durationMs
                    )
                }
                // Historial: solo se cuenta mientras suena.
                val st = _state.value
                val song = if (st.isPlaying) st.currentSong else null
                if (song != null) {
                    if (pendingSong?.id != song.id) {
                        flushHistory()
                        pendingSong = song
                    }
                    pendingSeconds += 0.5
                    if (pendingSeconds >= 30.0) flushHistory()
                } else if (pendingSong != null) {
                    flushHistory()
                }
            }
        }
    }
}
