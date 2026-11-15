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
        onError = { _state.update { it.copy(error = "No se pudo reproducir la canción.") } }
    )
    private var ticker: Job? = null

    /** Inicializa VLC y escanea la carpeta Música en segundo plano. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            val vlcOk = vlc.init()
            val songs = LibraryScanner.scan(LibraryScanner.defaultMusicDir())
            _state.update {
                it.copy(
                    songs = songs,
                    scanning = false,
                    error = if (!vlcOk) "No se encontraron las librerías de VLC." else null
                )
            }
        }
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
