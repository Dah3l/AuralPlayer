package com.aural.player.desktop

import com.aural.player.shared.AppTheme
import com.aural.player.shared.CoverArtClient
import com.aural.player.shared.CoverStore
import com.aural.player.shared.DesktopPrefs
import com.aural.player.shared.DownloadStatus
import com.aural.player.shared.EditResult
import com.aural.player.shared.LibraryData
import com.aural.player.shared.LibraryStore
import com.aural.player.shared.LocalFiles
import com.aural.player.shared.LrcLibClient
import com.aural.player.shared.LyricsStore
import com.aural.player.shared.PlayHistoryStore
import com.aural.player.shared.PlaybackState
import com.aural.player.shared.PlayerActions
import com.aural.player.shared.Playlist
import com.aural.player.shared.PrefsStore
import com.aural.player.shared.RepeatMode
import com.aural.player.shared.Song
import com.aural.player.shared.SongOverride
import com.aural.player.shared.StopMode
import com.aural.player.shared.TopSong
import com.aural.player.shared.YoutubeDownload
import com.aural.player.shared.YoutubeResult
import java.awt.Desktop
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Estado y motor de la app de escritorio: biblioteca escaneada, cola,
 * transporte, modos de reproducción, temporizador, ecualizador,
 * favoritas, portadas, letras y edición de tags. El motor es
 * [VlcPlayer]; la UI solo observa el estado y manda intenciones
 * por [PlayerActions].
 */
class DesktopPlayer(private val scope: CoroutineScope) : PlayerActions {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val vlc = VlcPlayer(
        // El callback `finished` llega en el hilo de eventos de VLC:
        // NUNCA se llama a VLC desde ahí (avanzar a la siguiente
        // canción dentro del callback bloquea el hilo y la app se
        // queda pegada al terminar una canción). Solo se encola el
        // trabajo en la corrutina del reproductor y se vuelve al momento.
        onFinished = { scope.launch { onTrackFinished() } },
        onError = { path -> _state.update { it.copy(error = "No se pudo reproducir: $path. Registro VLC: ${VlcPlayer.LOG_PATH}") } }
    )
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    // Datos locales de la PC: historial de escucha (Top 10),
    // preferencias, biblioteca (carpetas + playlists + favoritas +
    // metadatos superpuestos), portadas y letras. Misma carpeta.
    private val dataDir = File(System.getProperty("user.home"), ".auralplayer").absolutePath
    private val history = PlayHistoryStore(dataDir)
    private val prefs = PrefsStore(dataDir)
    private val library = LibraryStore(dataDir)
    private val covers = CoverStore(dataDir)
    private val lyrics = LyricsStore(dataDir)

    // Motor de YouTube (NewPipeExtractor): se inicializa perezoso,
    // en el primer uso, para no tocar la red al abrir la app.
    private val youtube by lazy { YoutubeEngine() }

    // Descargas canceladas por el usuario: el bucle de descarga lo
    // consulta en cada bloque y aborta (borra el parcial).
    private val cancelledDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Metadatos superpuestos (ediciones que no se pudieron escribir
    // en el archivo): viven en memoria y en library.json.
    private var overrides: Map<String, SongOverride> = emptyMap()

    // Historial de aleatorio: para que "anterior" vuelva a la
    // canción que sonó antes y no a otra al azar.
    private val shuffleHistory = mutableListOf<Int>()

    // Último volumen distinto de cero: al quitar el silencio se
    // restaura este nivel en vez de quedarse en 0.
    private var lastVolume: Int = 100

    // Vigilancia de atasco: si una pista suena pero su posición no
    // avanza 2 s seguidos, se vuelve a lanzar la reproducción una
    // vez (a veces VLC deja la entrada nueva en "abriendo" al
    // cambiar de canción sola y no suena hasta que se busca con el
    // slider; el reintento hace lo mismo sin tocar nada).
    private var stallTicks = 0
    private var lastWatchPositionMs = -1L
    private var stallRecoveredFor: String? = null

    // Segundos escuchados pendientes de registrar: se acumulan en
    // el ticker y se guardan cada 30 s o al cambiar de canción.
    private var pendingSong: Song? = null
    private var pendingSeconds = 0.0

    /** Escanea las carpetas de música en segundo plano e inicializa VLC. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                // Primero las preferencias guardadas, para que la
                // ventana abra como quedó (tema, volumen, modo, EQ).
                try {
                    prefs.load()?.let { saved ->
                        val theme = AppTheme.entries.firstOrNull { it.name == saved.themeName }
                            ?: AppTheme.BOSQUE
                        val repeat = runCatching {
                            RepeatMode.valueOf(saved.repeatModeName)
                        }.getOrDefault(RepeatMode.OFF)
                        // Volumen guardado en 0 = estado trampa (el botón
                        // de mute no lo sacaba de ahí): se abre en 100 y
                        // sin silencio; quien quiera silencio usa el mute.
                        val healedVolume = if (saved.volume <= 0) 100
                        else saved.volume.coerceIn(0, 100)
                        val healedMuted = if (saved.volume <= 0) false else saved.muted
                        lastVolume = healedVolume
                        _state.update {
                            it.copy(
                                theme = theme,
                                volume = healedVolume,
                                muted = healedMuted,
                                repeatMode = repeat,
                                shuffleEnabled = saved.shuffleEnabled,
                                playbackSpeed = saved.playbackSpeed,
                                eqEnabled = saved.eqEnabled,
                                eqPreamp = saved.eqPreamp,
                                eqAmps = saved.eqAmps,
                                islandEnabled = saved.islandEnabled,
                                cometEnabled = saved.cometEnabled,
                                islandAppearance = saved.islandAppearance,
                                islandButtonColorHex = saved.islandButtonColorHex,
                                islandCometColorHex = saved.islandCometColorHex
                            )
                        }
                        // Si se curó un volumen en 0, se guarda la corrección.
                        if (saved.volume <= 0) persistPrefs()
                    }
                } catch (t: Throwable) {
                    // Sin preferencias guardadas: se abre con los valores base
                }
                // Carpetas + playlists + favoritas + superpuestos
                // guardados (o la carpeta Música por defecto la
                // primera vez).
                val savedLibrary = try { library.load() } catch (t: Throwable) { null }
                val folders = savedLibrary?.folders
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(LibraryScanner.defaultMusicDir().absolutePath)
                overrides = savedLibrary?.overrides ?: emptyMap()
                _state.update {
                    it.copy(
                        folders = folders,
                        playlists = savedLibrary?.playlists ?: emptyList(),
                        favorites = savedLibrary?.favorites?.toSet() ?: emptySet()
                    )
                }
                // Después el escaneo (en tandas: la lista se va
                // llenando) y después VLC, para que un fallo del
                // motor nunca deje la pantalla en blanco.
                val songs = scanFolders(folders)
                val vlcOk = vlc.init()
                if (vlcOk) {
                    vlc.setVolume(_state.value.volume)
                    vlc.setMute(_state.value.muted)
                    vlc.setRate(_state.value.playbackSpeed)
                    vlc.setEqualizer(
                        _state.value.eqEnabled,
                        _state.value.eqPreamp,
                        _state.value.eqAmps
                    )
                    // El icono de la bocina muestra el estado REAL de VLC
                    // al abrir: si el motor quedó distinto de lo guardado,
                    // manda VLC y se guarda la corrección.
                    val realVolume = vlc.volume()
                    val realMuted = vlc.isMute()
                    if (realVolume != _state.value.volume || realMuted != _state.value.muted) {
                        _state.update { it.copy(volume = realVolume, muted = realMuted) }
                        if (realVolume > 0) lastVolume = realVolume
                        persistPrefs()
                    }
                }
                _state.update {
                    it.copy(
                        songs = songs,
                        queue = songs,
                        scanning = false,
                        eqBandFrequencies = if (vlcOk) vlc.equalizerBands() else emptyList(),
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
        val artworkDir = covers.embeddedDir()
        for (folder in folders) {
            val dir = File(folder)
            if (!dir.isDirectory) continue
            LibraryScanner.scan(dir, artworkDir) { batch ->
                var changed = false
                for (song in batch) {
                    if (found.put(song.filePath, withOverride(song)) == null) changed = true
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

    /** Aplica el metadato superpuesto si la canción tiene uno. */
    private fun withOverride(song: Song): Song {
        val override = overrides[song.filePath] ?: return song
        return song.copy(title = override.title, artist = override.artist, album = override.album)
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
        sleepJob?.cancel()
        sleepJob = null
        // Parar y liberar VLC va en un hilo aparte (daemon): dentro de
        // libvlc puede quedarse esperando, y el cierre de la ventana
        // no puede depender de eso (antes, dispose() corría en el hilo
        // de la ventana y la app no cerraba).
        Thread({
            try {
                vlc.stop()
            } catch (_: Throwable) {
            }
            try {
                vlc.release()
            } catch (_: Throwable) {
            }
        }, "aural-vlc-release").apply { isDaemon = true; start() }
    }

    // ---------------- Transporte ----------------

    /** Reproduce la canción [index] de la lista [list]: esa lista queda como cola. */
    override fun playFrom(list: List<Song>, index: Int) {
        val song = list.getOrNull(index) ?: return
        // Una lista distinta a la cola actual reinicia el historial
        // de aleatorio: lo anterior ya no aplica.
        if (list !== _state.value.queue) shuffleHistory.clear()
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

    override fun playShuffled(list: List<Song>) {
        if (list.isEmpty()) return
        playFrom(list.shuffled(), 0)
    }

    override fun togglePlay() {
        val current = _state.value
        if (current.currentIndex < 0) {
            if (current.queue.isNotEmpty()) playFrom(current.queue, 0)
            else if (current.songs.isNotEmpty()) playFrom(current.songs, 0)
            return
        }
        vlc.toggle()
        _state.update { it.copy(isPlaying = vlc.isPlaying) }
    }

    override fun next() {
        val current = _state.value
        if (current.queue.isEmpty() || current.currentIndex < 0) return
        if (current.shuffleEnabled) {
            if (current.queue.size <= 1) {
                playFrom(current.queue, 0)
                return
            }
            shuffleHistory.add(current.currentIndex)
            var nextIndex: Int
            do {
                nextIndex = Random.nextInt(current.queue.size)
            } while (nextIndex == current.currentIndex)
            playFrom(current.queue, nextIndex)
            return
        }
        val nextIndex = current.currentIndex + 1
        if (nextIndex < current.queue.size) {
            playFrom(current.queue, nextIndex)
        } else if (current.repeatMode == RepeatMode.ALL) {
            playFrom(current.queue, 0)
        }
    }

    override fun previous() {
        val current = _state.value
        if (current.currentIndex < 0) return
        // Más de 3 s: vuelve al inicio de la misma canción.
        if (current.positionMs > 3000L) {
            playFrom(current.queue, current.currentIndex)
            return
        }
        // En aleatorio, "anterior" vuelve a la que sonó antes.
        if (current.shuffleEnabled && shuffleHistory.isNotEmpty()) {
            val prevIndex = shuffleHistory.removeAt(shuffleHistory.lastIndex)
            if (prevIndex in current.queue.indices) {
                playFrom(current.queue, prevIndex)
            }
            return
        }
        if (current.currentIndex > 0) {
            playFrom(current.queue, current.currentIndex - 1)
        }
    }

    /** Fin de pista: parada programada, repetir uno, aleatorio, repetir todo o parar. */
    private fun onTrackFinished() {
        val current = _state.value
        if (current.currentIndex < 0) return
        // Parada al terminar la canción: se detiene y se apaga el modo.
        if (current.stopMode == StopMode.SONG) {
            vlc.stop()
            _state.update { it.copy(isPlaying = false, positionMs = 0L, stopMode = null) }
            return
        }
        if (current.repeatMode == RepeatMode.ONE) {
            playFrom(current.queue, current.currentIndex)
            return
        }
        if (current.shuffleEnabled) {
            if (current.queue.size <= 1) {
                vlc.stop()
                _state.update { it.copy(isPlaying = false, positionMs = 0L) }
                return
            }
            shuffleHistory.add(current.currentIndex)
            var nextIndex: Int
            do {
                nextIndex = Random.nextInt(current.queue.size)
            } while (nextIndex == current.currentIndex)
            playFrom(current.queue, nextIndex)
            return
        }
        val nextIndex = current.currentIndex + 1
        if (nextIndex < current.queue.size) {
            playFrom(current.queue, nextIndex)
        } else if (current.stopMode == StopMode.QUEUE) {
            vlc.stop()
            _state.update { it.copy(isPlaying = false, positionMs = 0L, stopMode = null) }
        } else if (current.repeatMode == RepeatMode.ALL) {
            playFrom(current.queue, 0)
        } else {
            vlc.stop()
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    override fun seek(fraction: Float) {
        vlc.seek(fraction)
        _state.update {
            it.copy(positionMs = (fraction * it.durationMs).toLong().coerceAtLeast(0L))
        }
    }

    override fun setVolume(value: Int) {
        val clamped = value.coerceIn(0, 100)
        vlc.setVolume(clamped)
        if (clamped > 0) {
            // Subir el volumen quita el silencio: el deslizador y el
            // botón de mute ya no pelean entre ellos.
            lastVolume = clamped
            vlc.setMute(false)
            _state.update { it.copy(volume = clamped, muted = false) }
        } else {
            // Volumen a 0 = silencio: el icono de mute queda coherente.
            vlc.setMute(true)
            _state.update { it.copy(volume = 0, muted = true) }
        }
        persistPrefs()
    }

    override fun toggleMute() {
        val current = _state.value
        val newMuted = !current.muted
        if (newMuted) {
            vlc.setMute(true)
            _state.update { it.copy(muted = true) }
        } else {
            // Al reactivar, si el volumen estaba en 0 se restaura el
            // último nivel: antes el botón no hacía nada audible.
            val restored = if (current.volume <= 0) lastVolume.coerceAtLeast(1)
            else current.volume
            vlc.setVolume(restored)
            vlc.setMute(false)
            _state.update { it.copy(muted = false, volume = restored) }
        }
        persistPrefs()
    }

    override fun setTheme(theme: AppTheme) {
        _state.update { it.copy(theme = theme) }
        persistPrefs()
    }

    // ---------------- Isla flotante ----------------

    override fun setIslandEnabled(enabled: Boolean) {
        _state.update { it.copy(islandEnabled = enabled) }
        persistPrefs()
    }

    override fun setCometEnabled(enabled: Boolean) {
        _state.update { it.copy(cometEnabled = enabled) }
        persistPrefs()
    }

    override fun setIslandAppearance(appearance: String) {
        _state.update { it.copy(islandAppearance = appearance) }
        persistPrefs()
    }

    override fun setIslandButtonColor(hex: String?) {
        _state.update { it.copy(islandButtonColorHex = hex) }
        persistPrefs()
    }

    override fun setIslandCometColor(hex: String?) {
        _state.update { it.copy(islandCometColorHex = hex) }
        persistPrefs()
    }

    // ---------------- Cola ----------------

    override fun enqueue(song: Song) {
        _state.update { st ->
            if (st.queue.isEmpty()) st.copy(queue = listOf(song))
            else st.copy(queue = st.queue + song)
        }
    }

    override fun enqueueAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        _state.update { st -> st.copy(queue = st.queue + songs) }
    }

    override fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        val queue = current.queue.toMutableList()
        queue.removeAt(index)
        when {
            queue.isEmpty() -> {
                flushHistory()
                vlc.stop()
                _state.update {
                    it.copy(queue = emptyList(), currentIndex = -1, isPlaying = false, positionMs = 0L)
                }
            }
            index == current.currentIndex -> {
                // Suena la que cayó en su lugar, o la anterior si era la última.
                val newIndex = if (index < queue.size) index else queue.size - 1
                playFrom(queue, newIndex)
            }
            index < current.currentIndex -> {
                _state.update { it.copy(queue = queue, currentIndex = it.currentIndex - 1) }
            }
            else -> {
                _state.update { it.copy(queue = queue) }
            }
        }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val current = _state.value
        if (from == to || from !in current.queue.indices || to !in current.queue.indices) return
        val queue = current.queue.toMutableList()
        val item = queue.removeAt(from)
        queue.add(to, item)
        var index = current.currentIndex
        if (index == from) {
            index = to
        } else if (from < index && to >= index) {
            index -= 1
        } else if (from > index && to <= index) {
            index += 1
        }
        _state.update { it.copy(queue = queue, currentIndex = index) }
    }

    // ---------------- Modo, velocidad, temporizador, EQ ----------------

    /** El botón de modo cicla: sin repetir → todo → uno → aleatorio → sin repetir. */
    override fun cyclePlaybackMode() {
        val current = _state.value
        val (repeat, shuffle) = when {
            current.shuffleEnabled -> RepeatMode.OFF to false
            current.repeatMode == RepeatMode.OFF -> RepeatMode.ALL to false
            current.repeatMode == RepeatMode.ALL -> RepeatMode.ONE to false
            else -> RepeatMode.OFF to true
        }
        _state.update { it.copy(repeatMode = repeat, shuffleEnabled = shuffle) }
        persistPrefs()
    }

    override fun setPlaybackSpeed(speed: Float) {
        vlc.setRate(speed)
        _state.update { it.copy(playbackSpeed = speed) }
        persistPrefs()
    }

    override fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            vlc.pause()
            _state.update { it.copy(isPlaying = false, sleepMinutes = null) }
        }
        _state.update { it.copy(sleepMinutes = minutes, stopMode = null) }
    }

    override fun setStopMode(mode: StopMode) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(stopMode = mode, sleepMinutes = null) }
    }

    override fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepMinutes = null, stopMode = null) }
    }

    override fun applyEqualizer(enabled: Boolean, preamp: Float, amps: List<Float>) {
        _state.update { it.copy(eqEnabled = enabled, eqPreamp = preamp, eqAmps = amps) }
        vlc.setEqualizer(enabled, preamp, amps)
        persistPrefs()
    }

    // ---------------- Carpetas ----------------

    /** Abre el explorador para añadir una carpeta de música y re-escanea. */
    override fun addFolder() {
        val picked = pickFolder() ?: return
        val normalized = picked.trimEnd('/', '\\')
        val current = _state.value.folders
        if (current.any { it.trimEnd('/', '\\').equals(normalized, ignoreCase = true) }) return
        val updated = current + picked
        _state.update { it.copy(folders = updated) }
        saveLibrary()
        rescan(updated)
    }

    /** Quita una carpeta del escaneo (las canciones dejan de listarse) y re-escanea. */
    override fun removeFolder(path: String) {
        val updated = _state.value.folders - path
        if (updated.size == _state.value.folders.size) return
        _state.update { it.copy(folders = updated) }
        saveLibrary()
        rescan(updated)
    }

    // ---------------- Playlists ----------------

    override fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val playlist = Playlist(id = newPlaylistId(), name = trimmed)
        val updated = _state.value.playlists + playlist
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    /** Crea la playlist y añade la canción en el mismo gesto. */
    override fun createPlaylistAndAdd(name: String, song: Song) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val playlist = Playlist(
            id = newPlaylistId(),
            name = trimmed,
            songPaths = listOf(song.filePath)
        )
        val updated = _state.value.playlists + playlist
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun createPlaylistAndAddAll(name: String, songs: List<Song>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || songs.isEmpty()) return
        val playlist = Playlist(
            id = newPlaylistId(),
            name = trimmed,
            songPaths = songs.map { it.filePath }.distinct()
        )
        val updated = _state.value.playlists + playlist
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun renamePlaylist(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val updated = _state.value.playlists.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun deletePlaylist(id: String) {
        val updated = _state.value.playlists.filter { it.id != id }
        if (updated.size == _state.value.playlists.size) return
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun addToPlaylist(id: String, song: Song) {
        val updated = _state.value.playlists.map {
            if (it.id == id && song.filePath !in it.songPaths) {
                it.copy(songPaths = it.songPaths + song.filePath)
            } else {
                it
            }
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun addAllToPlaylist(id: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val paths = songs.map { it.filePath }.toSet()
        val updated = _state.value.playlists.map {
            if (it.id == id) {
                it.copy(songPaths = it.songPaths + paths.filter { path -> path !in it.songPaths })
            } else {
                it
            }
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    override fun removeFromPlaylist(id: String, song: Song) {
        val updated = _state.value.playlists.map {
            if (it.id == id) it.copy(songPaths = it.songPaths - song.filePath) else it
        }
        _state.update { it.copy(playlists = updated) }
        saveLibrary()
    }

    private fun newPlaylistId(): String =
        System.currentTimeMillis().toString() + "-" + Random.nextInt(1000)

    // ---------------- Favoritas y borrado ----------------

    override fun toggleFavorite(song: Song) {
        _state.update { st ->
            val favorites = if (song.filePath in st.favorites) {
                st.favorites - song.filePath
            } else {
                st.favorites + song.filePath
            }
            st.copy(favorites = favorites)
        }
        saveLibrary()
    }

    override fun addFavorites(songs: List<Song>) {
        if (songs.isEmpty()) return
        _state.update { st ->
            st.copy(favorites = st.favorites + songs.map { it.filePath })
        }
        saveLibrary()
    }

    /**
     * Borra canciones DE VERDAD (los archivos se eliminan del disco),
     * las saca de la biblioteca, la cola, las playlists, las
     * favoritas y los superpuestos. Si sonaba una de ellas, se para.
     */
    override fun deleteSongs(songs: List<Song>) {
        val paths = songs.map { it.filePath }.toSet()
        if (paths.isEmpty()) return
        val current = _state.value
        val currentDeleted = current.currentSong?.filePath in paths
        if (currentDeleted) {
            flushHistory()
            vlc.stop()
        }
        scope.launch(Dispatchers.IO) {
            for (path in paths) {
                try {
                    File(path).delete()
                } catch (_: Throwable) {
                }
            }
            overrides = overrides - paths
            val playingPath = if (currentDeleted) null else current.currentSong?.filePath
            _state.update { st ->
                val newQueue = st.queue.filter { it.filePath !in paths }
                val newIndex = playingPath?.let { p ->
                    newQueue.indexOfFirst { it.filePath == p }
                } ?: -1
                st.copy(
                    songs = st.songs.filter { it.filePath !in paths },
                    queue = newQueue,
                    currentIndex = if (currentDeleted) -1 else newIndex,
                    isPlaying = if (currentDeleted) false else st.isPlaying,
                    positionMs = if (currentDeleted) 0L else st.positionMs,
                    playlists = st.playlists.map {
                        it.copy(songPaths = it.songPaths.filter { path -> path !in paths })
                    },
                    favorites = st.favorites - paths
                )
            }
            saveLibrary()
        }
    }

    // ---------------- Portadas, información y letras ----------------

    override fun loadCoverBytes(song: Song): ByteArray? {
        return try {
            covers.load(song.id)
        } catch (_: Throwable) {
            null
        }
    }

    override fun hasCustomCover(song: Song): Boolean {
        return try {
            covers.hasCustom(song.id)
        } catch (_: Throwable) {
            false
        }
    }

    /** Elige una imagen, la baja a 600px como mucho y la guarda como portada. */
    override fun pickCustomCover(song: Song): Boolean {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Elige una portada"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter(
                    "Imágenes",
                    "jpg", "jpeg", "png", "gif", "bmp"
                )
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return false
            val file = chooser.selectedFile ?: return false
            val img = ImageIO.read(file) ?: return false
            val longest = if (img.width >= img.height) img.width else img.height
            val scale = if (longest > 600) 600.0 / longest else 1.0
            val width = (img.width * scale).roundToInt().coerceAtLeast(1)
            val height = (img.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = scaled.createGraphics()
            graphics.drawImage(img.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
            graphics.dispose()
            val out = ByteArrayOutputStream()
            if (!ImageIO.write(scaled, "jpg", out)) return false
            covers.saveCustom(song.id, out.toByteArray())
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun removeCustomCover(song: Song) {
        try {
            covers.removeCustom(song.id)
        } catch (_: Throwable) {
        }
    }

    /** Descarga la portada de una URL (iTunes, 600 px) y la guarda. */
    override suspend fun saveCoverFromUrl(song: Song, url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = CoverArtClient.download(url) ?: return@withContext false
                covers.saveCustom(song.id, bytes)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Edita título/artista/álbum: primero en el ARCHIVO (y se
     * reincrustra la portada personalizada si la hay); si el
     * archivo no se deja escribir, los cambios quedan guardados
     * solo en AuralPlayer (superpuesto), sin perder nada.
     */
    override fun editMetadata(
        song: Song,
        title: String,
        artist: String,
        album: String
    ): EditResult {
        val file = File(song.filePath)
        if (!file.isFile) return EditResult.FAILED
        val customBytes = try {
            if (covers.hasCustom(song.id)) covers.load(song.id) else null
        } catch (_: Throwable) {
            null
        }
        val written = DesktopTagEditor.writeMetadata(file, title, artist, album, customBytes)
        return if (written) {
            // El archivo manda: se suelta el superpuesto y se
            // relee el archivo con los tags ya nuevos.
            overrides = overrides - song.filePath
            val fresh = withOverride(LibraryScanner.readSingle(file))
            if (pendingSong?.filePath == song.filePath) pendingSong = fresh
            _state.update { st ->
                st.copy(
                    songs = st.songs.map { if (it.filePath == song.filePath) fresh else it },
                    queue = st.queue.map { if (it.filePath == song.filePath) fresh else it }
                )
            }
            saveLibrary()
            EditResult.SAVED_FILE
        } else {
            overrides = overrides + (song.filePath to SongOverride(title, artist, album))
            val overlaid = withOverride(song)
            if (pendingSong?.filePath == song.filePath) pendingSong = overlaid
            _state.update { st ->
                st.copy(
                    songs = st.songs.map { if (it.filePath == song.filePath) overlaid else it },
                    queue = st.queue.map { if (it.filePath == song.filePath) overlaid else it }
                )
            }
            saveLibrary()
            EditResult.SAVED_OVERLAY
        }
    }

    /** Letra guardada, o buscada en línea y guardada para la próxima. */
    override suspend fun loadLyrics(song: Song): String? {
        return withContext(Dispatchers.IO) {
            try {
                lyrics.load(song.id)
            } catch (_: Throwable) {
                null
            } ?: try {
                val found = LrcLibClient.searchLyrics(
                    song.artist,
                    song.title,
                    (song.durationMs / 1000).toInt()
                )
                if (!found.isNullOrBlank()) {
                    try {
                        lyrics.save(song.id, found)
                    } catch (_: Throwable) {
                    }
                }
                found
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Busca la letra con el nombre que escribió el usuario y la guarda. */
    override suspend fun searchAndSaveLyrics(song: Song, track: String, artist: String): Boolean {
        return try {
            val found = LrcLibClient.searchLyrics(
                artist,
                track,
                (song.durationMs / 1000).toInt()
            )
            if (found.isNullOrBlank()) return false
            withContext(Dispatchers.IO) {
                try {
                    lyrics.save(song.id, found)
                } catch (_: Throwable) {
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Elige un archivo .lrc del disco y lo guarda como letra de la canción. */
    override fun pickLrcFile(song: Song): Boolean {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Elige un archivo de letra (.lrc)"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("Archivos de letra", "lrc")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return false
            val file = chooser.selectedFile ?: return false
            val content = file.readText()
            if (content.isBlank()) return false
            lyrics.save(song.id, content)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------- Historial ----------------

    /** Las canciones más escuchadas según el historial local. */
    override suspend fun loadTopSongs(limit: Int): List<TopSong> {
        return try {
            history.getTopSongs(limit)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // ---------------- Descargas de YouTube ----------------

    override suspend fun searchYouTube(query: String): List<YoutubeResult> {
        if (query.isBlank()) return emptyList()
        _state.update { it.copy(youTubeSearching = true, youTubeError = null) }
        return try {
            val results = youtube.search(query)
            _state.update { it.copy(youTubeResults = results, youTubeSearching = false) }
            results
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    youTubeSearching = false,
                    youTubeError = "No se pudo buscar en YouTube: ${t.message?.take(100)}"
                )
            }
            emptyList()
        }
    }

    override fun downloadYouTube(result: YoutubeResult) {
        val current = _state.value.downloads
        if (current.any {
                it.videoId == result.videoId &&
                    (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING)
            }
        ) return
        _state.update {
            it.copy(
                downloads = it.downloads.filter { d -> d.videoId != result.videoId } +
                    YoutubeDownload(result.videoId, result.title, DownloadStatus.QUEUED, 0f),
                youTubeError = null
            )
        }
        scope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    downloads = it.downloads.map { d ->
                        if (d.videoId == result.videoId) d.copy(status = DownloadStatus.DOWNLOADING) else d
                    }
                )
            }
            // Primera carpeta de la biblioteca (por defecto, Música).
            val folders = _state.value.folders
            val destDir = File(
                folders.firstOrNull() ?: LibraryScanner.defaultMusicDir().absolutePath
            )
            val file = youtube.download(
                videoId = result.videoId,
                title = result.title,
                uploader = result.uploader,
                destDir = destDir,
                onProgress = { pct ->
                    _state.update {
                        it.copy(
                            downloads = it.downloads.map { d ->
                                if (d.videoId == result.videoId) d.copy(progress = pct) else d
                            }
                        )
                    }
                },
                isCancelled = { result.videoId in cancelledDownloads }
            )
            val wasCancelled = result.videoId in cancelledDownloads
            cancelledDownloads.remove(result.videoId)
            if (file != null) {
                _state.update {
                    it.copy(
                        downloads = it.downloads.map { d ->
                            if (d.videoId == result.videoId) {
                                d.copy(status = DownloadStatus.DONE, progress = 1f)
                            } else d
                        }
                    )
                }
                // La canción nueva aparece en la biblioteca sin perder
                // la reproducción en curso.
                rescan(_state.value.folders)
            } else if (wasCancelled) {
                // Cancelada por el usuario: vuelve el botón de descarga.
                _state.update {
                    it.copy(downloads = it.downloads.filter { d -> d.videoId != result.videoId })
                }
            } else {
                val reason = youtube.lastError ?: "No se pudo descargar el audio"
                _state.update {
                    it.copy(
                        downloads = it.downloads.map { d ->
                            if (d.videoId == result.videoId) {
                                d.copy(status = DownloadStatus.FAILED, error = reason)
                            } else d
                        },
                        youTubeError = "No se pudo descargar \"${result.title}\": $reason."
                    )
                }
            }
        }
    }

    override fun cancelDownload(videoId: String) {
        cancelledDownloads.add(videoId)
    }

    override fun clearYouTubeError() {
        _state.update { it.copy(youTubeError = null) }
    }

    // ---------------- Sistema ----------------

    override fun copyText(text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text), null)
        } catch (_: Throwable) {
        }
    }

    /** Abre la carpeta del archivo en el Explorador de Windows. */
    override fun revealInExplorer(path: String) {
        try {
            val desktop = Desktop.getDesktop()
            val parent = File(path).parentFile
            if (parent != null && parent.isDirectory) {
                desktop.open(parent)
            }
        } catch (_: Throwable) {
        }
    }

    override fun openUrl(url: String) {
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (_: Throwable) {
        }
    }

    // ---------------- Respaldo ----------------

    /**
     * Exporta preferencias, biblioteca (carpetas, playlists,
     * favoritas) e historial Top 10 a un solo JSON. El usuario
     * elige dónde guardarlo con el diálogo de Windows.
     */
    override fun exportBackupToFile(): Boolean {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Exportar respaldo de AuralPlayer"
                fileFilter = FileNameExtensionFilter("Respaldo de AuralPlayer (*.json)", "json")
                selectedFile = File("aural-respaldo.json")
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return false
            var target = chooser.selectedFile ?: return false
            if (target.extension.lowercase() != "json") {
                target = File(target.absolutePath + ".json")
            }
            val bundle = buildJsonObject {
                put("format", "aural-backup")
                put("version", 1)
                put("prefs", LocalFiles.readText("$dataDir/prefs.json") ?: "")
                put("library", LocalFiles.readText("$dataDir/library.json") ?: "")
                put("history", LocalFiles.readText("$dataDir/play_history.json") ?: "")
            }
            target.writeText(Json { }.encodeToString(JsonObject.serializer(), bundle))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Importa un respaldo exportado antes: restaura los tres JSON y
     * vuelve a cargar todo sin reiniciar la app.
     */
    override fun importBackupFromFile(): Boolean {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Importar respaldo de AuralPlayer"
                fileFilter = FileNameExtensionFilter("Respaldo de AuralPlayer (*.json)", "json")
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return false
            val file = chooser.selectedFile ?: return false
            val obj = Json { }.parseToJsonElement(file.readText()) as? JsonObject
                ?: return false
            if ((obj["format"] as? JsonPrimitive)?.contentOrNull != "aural-backup") return false
            val prefsText = (obj["prefs"] as? JsonPrimitive)?.contentOrNull ?: return false
            val libraryText = (obj["library"] as? JsonPrimitive)?.contentOrNull ?: return false
            val historyText = (obj["history"] as? JsonPrimitive)?.contentOrNull ?: return false
            LocalFiles.writeText("$dataDir/prefs.json", prefsText)
            LocalFiles.writeText("$dataDir/library.json", libraryText)
            LocalFiles.writeText("$dataDir/play_history.json", historyText)
            reloadData()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Borra todos los datos guardados de la app (preferencias,
     * biblioteca, historial, portadas y letras). La UI avisa que
     * hay que cerrar y volver a abrir.
     */
    override fun clearAllData(): Boolean {
        return try {
            File(dataDir).deleteRecursively()
            File(dataDir).mkdirs()
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------- Persistencia ----------------

    private fun saveLibrary() {
        scope.launch(Dispatchers.IO) {
            try {
                val st = _state.value
                library.save(
                    LibraryData(
                        folders = st.folders,
                        playlists = st.playlists,
                        favorites = st.favorites.toList(),
                        overrides = overrides
                    )
                )
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
            prefs.save(
                DesktopPrefs(
                    themeName = s.theme.name,
                    volume = s.volume,
                    muted = s.muted,
                    repeatModeName = s.repeatMode.name,
                    shuffleEnabled = s.shuffleEnabled,
                    playbackSpeed = s.playbackSpeed,
                    eqEnabled = s.eqEnabled,
                    eqPreamp = s.eqPreamp,
                    eqAmps = s.eqAmps,
                    islandEnabled = s.islandEnabled,
                    cometEnabled = s.cometEnabled,
                    islandAppearance = s.islandAppearance,
                    islandButtonColorHex = s.islandButtonColorHex,
                    islandCometColorHex = s.islandCometColorHex
                )
            )
        }
    }

    /**
     * Vuelve a leer preferencias + biblioteca del disco y re-escanea
     * (tras importar un respaldo). No re-inicia VLC: solo el estado.
     */
    private fun reloadData() {
        scope.launch(Dispatchers.IO) {
            try {
                prefs.load()?.let { saved ->
                    val healedVolume = if (saved.volume <= 0) 100
                    else saved.volume.coerceIn(0, 100)
                    val healedMuted = if (saved.volume <= 0) false else saved.muted
                    lastVolume = healedVolume
                    val theme = AppTheme.entries.firstOrNull { it.name == saved.themeName }
                        ?: AppTheme.BOSQUE
                    val repeat = runCatching {
                        RepeatMode.valueOf(saved.repeatModeName)
                    }.getOrDefault(RepeatMode.OFF)
                    _state.update {
                        it.copy(
                            theme = theme,
                            volume = healedVolume,
                            muted = healedMuted,
                            repeatMode = repeat,
                            shuffleEnabled = saved.shuffleEnabled,
                            playbackSpeed = saved.playbackSpeed,
                            eqEnabled = saved.eqEnabled,
                            eqPreamp = saved.eqPreamp,
                            eqAmps = saved.eqAmps,
                            islandEnabled = saved.islandEnabled,
                            cometEnabled = saved.cometEnabled,
                            islandAppearance = saved.islandAppearance,
                            islandButtonColorHex = saved.islandButtonColorHex,
                            islandCometColorHex = saved.islandCometColorHex
                        )
                    }
                    vlc.setVolume(healedVolume)
                    vlc.setMute(healedMuted)
                    vlc.setRate(_state.value.playbackSpeed)
                    vlc.setEqualizer(
                        _state.value.eqEnabled,
                        _state.value.eqPreamp,
                        _state.value.eqAmps
                    )
                }
            } catch (_: Throwable) {
            }
            try {
                val savedLibrary = library.load()
                overrides = savedLibrary?.overrides ?: emptyMap()
                val folders = savedLibrary?.folders
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(LibraryScanner.defaultMusicDir().absolutePath)
                _state.update {
                    it.copy(
                        folders = folders,
                        playlists = savedLibrary?.playlists ?: emptyList(),
                        favorites = savedLibrary?.favorites?.toSet() ?: emptySet()
                    )
                }
                rescan(folders)
            } catch (_: Throwable) {
            }
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
                // Vigilancia de atasco: sonando pero sin avanzar.
                // No vale el final de la pista (los últimos 2 s la
                // posición apenas se mueve antes del evento `finished`).
                if (song != null && st.durationMs > 0 &&
                    vlc.positionMs < st.durationMs - 2000
                ) {
                    val pos = vlc.positionMs
                    if (pos == lastWatchPositionMs) {
                        stallTicks++
                        if (stallTicks >= 4 && stallRecoveredFor != song.filePath) {
                            stallRecoveredFor = song.filePath
                            stallTicks = 0
                            vlc.play(song.filePath)
                        }
                    } else {
                        stallTicks = 0
                        lastWatchPositionMs = pos
                    }
                } else {
                    stallTicks = 0
                    lastWatchPositionMs = -1L
                }
            }
        }
    }
}
