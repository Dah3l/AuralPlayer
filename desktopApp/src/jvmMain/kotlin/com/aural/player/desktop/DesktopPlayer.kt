package com.aural.player.desktop

import com.aural.player.shared.PlaybackState
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

    /** Escanea la carpeta Música en segundo plano e inicializa VLC. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                // Primero el escaneo (en tandas: la lista se va
                // llenando) y después VLC, para que un fallo del
                // motor nunca deje la pantalla en blanco.
                val found = mutableListOf<com.aural.player.shared.Song>()
                val songs = LibraryScanner.scan(LibraryScanner.defaultMusicDir()) { batch ->
                    found += batch
                    _state.update { it.copy(songs = found.toList(), scanning = true) }
                }
                val vlcOk = vlc.init()
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
        ticker?.cancel()
        ticker = null
        vlc.dispose()
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

    private fun playIndex(index: Int) {
        val songs = _state.value.songs
        val song = songs.getOrNull(index) ?: return
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
            }
        }
    }
}
