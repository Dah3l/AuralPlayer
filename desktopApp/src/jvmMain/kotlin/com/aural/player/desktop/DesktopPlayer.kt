package com.aural.player.desktop

import com.aural.player.shared.AppTheme
import com.aural.player.shared.DesktopPrefs
import com.aural.player.shared.LrcLibClient
import com.aural.player.shared.PlayHistoryStore
import com.aural.player.shared.PlaybackState
import com.aural.player.shared.PrefsStore
import com.aural.player.shared.Song
import com.aural.player.shared.TopSong
import java.io.File
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

    // Datos locales de la PC: historial de escucha (Top 10) y
    // preferencias (tema, volumen, silencio). Misma carpeta.
    private val dataDir = File(System.getProperty("user.home"), ".auralplayer").absolutePath
    private val history = PlayHistoryStore(dataDir)
    private val prefs = PrefsStore(dataDir)

    // Segundos escuchados pendientes de registrar: se acumulan en
    // el ticker y se guardan cada 30 s o al cambiar de canción.
    private var pendingSong: Song? = null
    private var pendingSeconds = 0.0

    /** Escanea la carpeta Música en segundo plano e inicializa VLC. */
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
                // Después el escaneo (en tandas: la lista se va
                // llenando) y después VLC, para que un fallo del
                // motor nunca deje la pantalla en blanco.
                val found = mutableListOf<com.aural.player.shared.Song>()
                val songs = LibraryScanner.scan(LibraryScanner.defaultMusicDir()) { batch ->
                    found += batch
                    _state.update { it.copy(songs = found.toList(), scanning = true) }
                }
                val vlcOk = vlc.init()
                if (vlcOk) {
                    vlc.setVolume(_state.value.volume)
                    if (_state.value.muted) vlc.toggleMute()
                }
                _state.update {
                    it.copy(
                        songs = songs,
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

    /** Libera el motor y detiene el ticker. Se llama al cerrar la ventana. */
    fun dispose() {
        flushHistory()
        ticker?.cancel()
        ticker = null
        vlc.release()
    }

    fun select(index: Int) {
        playIndex(index)
    }

    fun toggle() {
        val current = _state.value
        if (current.currentIndex < 0) {
            if (current.songs.isNotEmpty()) playIndex(0)
            return
        }
        vlc.toggle()
        _state.update { it.copy(isPlaying = vlc.isPlaying) }
    }

    fun next(auto: Boolean = false) {
        val current = _state.value
        val nextIndex = current.currentIndex + 1
        if (nextIndex < current.songs.size) {
            playIndex(nextIndex)
        } else if (auto) {
            vlc.stop()
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    fun previous() {
        val current = _state.value
        if (current.positionMs > 3000L) {
            playIndex(current.currentIndex)
        } else if (current.currentIndex > 0) {
            playIndex(current.currentIndex - 1)
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

    private fun playIndex(index: Int) {
        val songs = _state.value.songs
        val song = songs.getOrNull(index) ?: return
        flushHistory()
        if (!vlc.play(song.filePath)) {
            _state.update { it.copy(error = "No se pudo reproducir ${song.title}") }
            return
        }
        _state.update {
            it.copy(
                currentIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = song.durationMs,
                error = null
            )
        }
        startTicker()
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
