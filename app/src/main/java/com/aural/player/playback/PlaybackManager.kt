// Enlace entre la interfaz (ViewModel) y el servicio Media3 mediante MediaController
package com.aural.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.aural.player.data.PlayHistoryStore
import com.aural.player.data.Song
import com.aural.player.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Modos de repetición del reproductor. */
enum class RepeatMode { OFF, ONE, ALL }

/** Parada programada: al terminar la canción actual o la lista entera. */
enum class StopMode { SONG, QUEUE }

/** Estado observable de la reproducción que consume la interfaz. */
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isReady: Boolean = false,
    /**
     * Índices de la cola vecinos de [currentIndex] para el carrusel de
     * portada del Now Playing. Con aleatorio activado siguen el orden
     * aleatorio del timeline; con aleatorio desactivado son
     * currentIndex±1. [C.INDEX_UNSET] = no hay vecino (extremo).
     */
    val prevNeighborIndex: Int = C.INDEX_UNSET,
    val nextNeighborIndex: Int = C.INDEX_UNSET
)

/**
 * Controlador de reproducción conectado al [MusicService].
 * Expone un StateFlow con todo lo que la UI necesita y métodos de acción.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackManager(private val context: Context) {

    private var controller: MediaController? = null
    private val settings = SettingsStore(context)

    /** Parada programada activa (null = ninguna). La consume el diálogo del temporizador. */
    private val _stopMode = MutableStateFlow<StopMode?>(null)
    val stopMode: StateFlow<StopMode?> = _stopMode.asStateFlow()

    /** Aviso corto para la UI (p. ej. un streaming que no resolvió). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    /** Diagnóstico completo de la escalera agotada (cliente,
     *  status y reason por cliente). Se muestra en un modal con
     *  botón de copiar y queda hasta que el usuario lo cierra;
     *  no se pierde al saltar a la siguiente canción. */
    private val _diagnosticReport = MutableStateFlow<String?>(null)
    val diagnosticReport: StateFlow<String?> = _diagnosticReport.asStateFlow()

    fun clearDiagnosticReport() {
        _diagnosticReport.value = null
    }

    /** Índices online que ya fallaron en esta cola (anti-bucle al saltar). */
    private val failedOnlineIndices = mutableSetOf<Int>()

    /** Peldaños de la escalera sin VPN ya intentados por video:
     *  1 NewPipe fresco, 2 InnerTube, 3 progresivo. Se limpia
     *  cuando la canción suena (READY). */
    private val onlineLadderAttempts = mutableMapOf<String, Int>()

    private val _endlessDjActive = MutableStateFlow(false)
    /** Si la Sesión DJ infinita está activa (la lleva el servicio). */
    val endlessDjActive: StateFlow<Boolean> = _endlessDjActive.asStateFlow()

    /** Marca la Sesión DJ infinita como activa o no (lo llama el servicio). */
    fun setEndlessDjActive(active: Boolean) {
        _endlessDjActive.value = active
    }

    /** Registro de escucha para "Tu Top 10". */
    private val playHistory = PlayHistoryStore(context)

    /**
     * Segundos acumulados por canción en esta sesión (id -> título, artista,
     * milisegundos). Se vuelca al registro al cambiar de canción, al
     * pausar y cada minuto; nunca interfiere con la reproducción.
     */
    private val trackedMillis = mutableMapOf<Long, Triple<String, String, Long>>()
    private var lastTrackedId: Long? = null
    private var tickerTickCount = 0

    /** Caché de metadatos personalizados: songId -> (título, artista, álbum). */
    private var customMetadataCache: Map<Long, Triple<String, String, String>> = emptyMap()
    /** Caché de portadas personalizadas: songId -> URL. */
    private var customCoversCache: Map<Long, String> = emptyMap()

    // Declarado ANTES del init: los collectors lanzados en init leen
    // _state y si la corrutina corre antes de que el constructor termine
    // de asignarlo, el acceso da NullPointerException (crash 2026-11-15).
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Scopes reutilizados: crear un CoroutineScope por llamada no
    // cancela nada y reserva objetos de más (los dos collectors del
    // init corren toda la vida del proceso). Se declaran ANTES del
    // init: el init los usa y las propiedades se inicializan en
    // orden textual.
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /** PNG de la portada predeterminada (signo de música) para canciones
     *  sin arte. Declarada ANTES del init por la misma razón que _state:
     *  el init lanza su pre-render en IO y si esa corrutina corre antes
     *  de que el constructor asigne el delegado lazy, el acceso da
     *  NullPointerException (crash 2026-11-25, primera apertura tras
     *  actualizar; era el único lazy declarado tras el init). */
    private val defaultCoverBytes: ByteArray by lazy { renderDefaultCover() }

    /** Cola cacheada para publishState: se reconstruye solo cuando
     *  cambia el timeline (firma = ids de los MediaItems en orden). */
    private var cachedQueue: List<Song> = emptyList()
    private var cachedQueueIds: List<String> = emptyList()
    private var cachedQueueIdsLong: List<Long> = emptyList()

    /** Ajustes del fundido en caché (los saltos los leen sin suspender). */
    private var crossfadeEnabledCache = false

    init {
        ioScope.launch { settings.crossfadeEnabled.collect { crossfadeEnabledCache = it } }
        // Observar cambios en personalizaciones y refrescar notificación si afecta la canción actual
        ioScope.launch {
            settings.customMetadata.collect { newMeta ->
                val oldMeta = customMetadataCache
                customMetadataCache = newMeta
                // La cola cacheada puede tener Songs con la metadata
                // vieja: invalidarla para que el próximo publishState
                // la reconstruya (igual que antes del caché).
                cachedQueueIds = emptyList()
                refreshCurrentIfAffected(oldMeta, newMeta)
            }
        }
        ioScope.launch {
            settings.customCovers.collect { newCovers ->
                val oldCovers = customCoversCache
                customCoversCache = newCovers
                cachedQueueIds = emptyList()
                refreshCurrentIfCoverAffected(oldCovers, newCovers)
            }
        }
        // La portada por defecto se renderiza aquí (hilo IO), no en
        // el primer uso en el hilo principal (toMediaItem).
        ioScope.launch { defaultCoverBytes }
    }

    /** Si la canción actual fue editada, actualiza su metadata en el reproductor. */
    private fun refreshCurrentIfAffected(
        old: Map<Long, Triple<String, String, String>>,
        new: Map<Long, Triple<String, String, String>>
    ) {
        val currentId = _state.value.currentSong?.id ?: return
        if (old[currentId] != new[currentId]) {
            refreshCurrentMediaItem()
        }
    }

    private fun refreshCurrentIfCoverAffected(
        old: Map<Long, String>,
        new: Map<Long, String>
    ) {
        val currentId = _state.value.currentSong?.id ?: return
        if (old[currentId] != new[currentId]) {
            refreshCurrentMediaItem()
        }
    }

    /** Reconstruye el MediaItem actual con los metadatos actualizados. */
    private fun refreshCurrentMediaItem() {
        // MediaController debe usarse en el hilo principal
        scope.launch {
            try {
                val c = controller ?: return@launch
                val index = c.currentMediaItemIndex
                if (index < 0) return@launch
                val song = _state.value.currentSong ?: return@launch
                c.replaceMediaItem(index, song.toMediaItem())
            } catch (e: Exception) {
                // Ignorar si falla
            }
        }
    }

    /** Canciones conocidas por id, para reconstruir la cola desde MediaItems. */
    private var libraryById: Map<Long, Song> = emptyMap()

    /** Última reproducción guardada (canción + cola), para no escribir de más. */
    private var lastSavedPlayback: Pair<Long, List<Long>>? = null

    /** Restauración pendiente si el controlador aún no estaba listo. */
    private var pendingRestore: Pair<List<Song>, Int>? = null

    private var tickerJob: kotlinx.coroutines.Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
            if (isPlaying) startTicker() else {
                stopTicker()
                flushTracking()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // La lista terminó sola (sin repetir): la parada programada
            // ya no tiene nada que hacer.
            if (playbackState == Player.STATE_ENDED && _stopMode.value != null) {
                _stopMode.value = null
            }
            // La canción suena: la escalera de ese video se reinicia.
            if (playbackState == Player.STATE_READY) {
                _state.value.currentSong?.onlineVideoId?.let { videoId ->
                    onlineLadderAttempts.remove(videoId)
                    com.aural.player.youtube.YouTubeStreamResolver.clearEscalation(videoId)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Escalera de respaldo de la MISMA canción (Etapa 2):
            // ante un error online —también si ni empieza— se
            // escala conservando la posición: 1) NewPipe fresco
            // excluyendo la URL fallida, 2) InnerTube directo,
            // 3) progresivo. Solo con la escalera agotada se avisa
            // y se salta a la siguiente.
            val c = controller ?: return
            val current = _state.value.currentSong ?: return
            if (!current.isOnline) return
            val videoId = current.onlineVideoId
            if (videoId != null) {
                val attempts = (onlineLadderAttempts[videoId] ?: 0) + 1
                if (attempts <= 3) {
                    onlineLadderAttempts[videoId] = attempts
                    com.aural.player.youtube.YouTubeStreamResolver
                        .prepareEscalation(videoId, attempts)
                    val curIdx = c.currentMediaItemIndex
                    val pos = c.currentPosition
                    if (curIdx >= 0) {
                        c.seekTo(curIdx, pos)
                        c.prepare()
                        c.playWhenReady = true
                        publishState()
                        return
                    }
                }
                onlineLadderAttempts.remove(videoId)
                com.aural.player.youtube.YouTubeStreamResolver.invalidate(videoId)
            }
            val curIdx = c.currentMediaItemIndex
            failedOnlineIndices += curIdx
            val nextIdx = if (curIdx >= 0 && c.currentTimeline.windowCount > 0) {
                c.currentTimeline.getNextWindowIndex(
                    curIdx, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled
                )
            } else C.INDEX_UNSET
            // Motivo técnico del player (playabilityStatus o
            // formatos solo cifrados): va completo al modal de
            // diagnóstico, que queda hasta cerrarlo. El aviso
            // corto no lleva el detalle: el Toast lo truncaba.
            val resolver = com.aural.player.youtube.YouTubeStreamResolver
            resolver.playabilityDiagnosticReport()?.let { report ->
                _diagnosticReport.value = report
            }
            resolver.clearPlayabilityDiagnostics()
            if (nextIdx == C.INDEX_UNSET || nextIdx in failedOnlineIndices) {
                _message.value = "No se pudo reproducir en línea"
                publishState()
                return
            }
            _message.value = "No se pudo reproducir en línea. Saltando…"
            c.seekTo(nextIdx, 0L)
            c.prepare()
            c.playWhenReady = true
            publishState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Solo importan los saltos automáticos (una canción que
            // termina y pasa a la siguiente, o que vuelve a empezar).
            if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
            val c = controller ?: return
            when (_stopMode.value) {
                StopMode.SONG -> {
                    // Queda en pausa al inicio de la siguiente canción.
                    c.pause()
                    _stopMode.value = null
                    publishState()
                }
                StopMode.QUEUE -> {
                    // Con repetir-todo, la lista "termina" al volver al
                    // inicio: se vuelve a la última canción y se pausa.
                    // Con una sola canción en la cola (o repetir-una) no
                    // hay vuelta al inicio: la lista termina al reiniciar
                    // la canción y también hay que parar.
                    // Con aleatorio el orden no es lineal y la vuelta no
                    // aplica, salvo que la cola tenga una sola canción.
                    val wrapped = newPosition.windowIndex < oldPosition.windowIndex
                    val singleItem = c.mediaItemCount <= 1
                    val repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
                    if ((wrapped && !c.shuffleModeEnabled) || singleItem || repeatOne) {
                        c.seekTo(oldPosition.windowIndex, 0L)
                        c.pause()
                        _stopMode.value = null
                        publishState()
                    }
                }
                null -> {}
            }
        }
    }

    /** Activa o cancela la parada programada. */
    fun setStopMode(mode: StopMode?) {
        _stopMode.value = mode
    }

    /** Vuelca al registro los segundos acumulados en esta sesión. */
    private fun flushTracking() {
        if (trackedMillis.isEmpty()) return
        val snapshot = trackedMillis.toMap()
        trackedMillis.clear()
        ioScope.launch {
            try {
                snapshot.forEach { (id, data) ->
                    val seconds = data.third / 1000
                    if (seconds > 0) {
                        playHistory.record(id, data.first, data.second, seconds)
                    }
                }
            } catch (e: Exception) {
                // El registro nunca debe romper la reproducción
            }
        }
    }

    /** Debe llamarse una vez al iniciar la app: conecta con el servicio. */
    fun connect() {
        scope.launch {
            val token = SessionToken(
                context,
                ComponentName(context, MusicService::class.java)
            )
            // await() suspendido hasta que el controlador esté disponible
            val result = MediaController.Builder(context, token).buildAsync().await()
            controller = result
            result.addListener(playerListener)
            _state.value = _state.value.copy(isReady = true)
            publishState()
            // Si la restauración al abrir llegó antes que el controlador,
            // se aplica ahora (sin pisar nada que ya esté sonando).
            // La cola DJ pendiente va primero: es la intención más nueva.
            pendingDjQueue?.let { songs ->
                pendingDjQueue = null
                applyDjQueue(songs)
            }
            pendingRestore?.let { (songs, index) ->
                pendingRestore = null
                applyRestore(songs, index)
            }
        }
    }

    /**
     * Registra la biblioteca para poder mapear MediaItems a canciones.
     * Conserva las online ya conocidas (id negativo): la cola mixta
     * debe seguir resolviéndose aunque la biblioteca local recargue.
     */
    fun setLibrary(songs: List<Song>) {
        val onlineKnown = libraryById.filterKeys { it < 0L }
        libraryById = songs.associateBy { it.id } + onlineKnown
        publishState()
    }

    /** Registra canciones (locales u online) que entran a la cola. */
    fun registerKnownSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        libraryById = libraryById + songs.associateBy { it.id }
        cachedQueueIds = emptyList()
    }

    /**
     * Inserta o actualiza una canción en la biblioteca en memoria y
     * republica el estado al instante, sin esperar al re-escaneo de
     * MediaStore. Se usa tras editar los tags del archivo: el re-escaneo
     * puede devolver la fila vieja si MediaStore aún está re-indexando
     * el archivo reemplazado (caso M4A), y sin esto la pantalla se queda
     * con la información vieja hasta cambiar de canción.
     */
    fun refreshSongMetadata(song: Song) {
        registerKnownSongs(listOf(song))
        publishState()
    }

    // ----------------------------- Acciones -----------------------------

    /** Reemplaza la cola con las canciones dadas e inicia en [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        // Cola nueva = intención nueva: se apaga la sesión DJ infinita
        // y se cancela la parada programada.
        setEndlessDj(false)
        _stopMode.value = null
        failedOnlineIndices.clear()
        registerKnownSongs(songs)
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.playWhenReady = true
        publishState()
    }

    /**
     * Restaura una cola en pausa (reanudar al abrir): deja la canción
     * indicada cargada y lista, sin sonar. Si el controlador aún no está
     * listo, queda pendiente y se aplica al conectar. Nunca pisa una
     * cola que ya tenga algo en reproducción.
     */
    fun restoreQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        if (controller == null) {
            pendingRestore = songs to startIndex
            return
        }
        applyRestore(songs, startIndex)
    }

    private fun applyRestore(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (c.mediaItemCount > 0) return
        registerKnownSongs(songs)
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.playWhenReady = false
        publishState()
    }

    /** Añade una canción al final de la cola y la reproduce si nada suena. */
    fun enqueue(song: Song) {
        val c = controller ?: return
        registerKnownSongs(listOf(song))
        c.addMediaItem(song.toMediaItem())
        if (c.mediaItemCount == 1) {
            c.prepare()
            c.playWhenReady = true
        }
        publishState()
    }

    /** Añade varias canciones al final de la cola (selección múltiple). v1.0.7. */
    /** Inserta una canción justo después de la actual ("reproducir siguiente"). */
    fun enqueueNext(song: Song) {
        val c = controller ?: return
        registerKnownSongs(listOf(song))
        if (c.mediaItemCount == 0) {
            c.addMediaItem(song.toMediaItem())
            c.prepare()
            c.playWhenReady = true
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
        publishState()
    }

    fun enqueueAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller ?: return
        registerKnownSongs(songs)
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(songs.map { it.toMediaItem() })
        if (wasEmpty) {
            c.prepare()
            c.playWhenReady = true
        }
        publishState()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        publishState()
    }

    fun seekToNext() {
        val c = controller ?: return
        // Con fundido, el salto lo hace el servicio (segundo reproductor
        // y cruce de volúmenes); si no se pudo, salto normal.
        if (crossfadeEnabledCache) {
            sendCrossfadeCommand(MusicService.CMD_CROSSFADE_NEXT) {
                c.seekToNext()
                publishState()
            }
            return
        }
        c.seekToNext()
        publishState()
    }

    fun seekToPrevious() {
        val c = controller ?: return
        // Convención habitual: si lleva más de 3 s, volver al inicio
        if (c.currentPosition > 3_000) {
            c.seekTo(0L)
            publishState()
            return
        }
        if (crossfadeEnabledCache) {
            sendCrossfadeCommand(MusicService.CMD_CROSSFADE_PREV) {
                c.seekToPrevious()
                publishState()
            }
            return
        }
        c.seekToPrevious()
        publishState()
    }

    /** Anterior directo, sin la regla de los 3 segundos (gesto de la isla).
     *  OJO: no se puede usar controller.seekToPrevious() porque ExoPlayer
     *  trae la regla de los 3 s implementada DENTRO y reiniciaría igual:
     *  se salta directo al índice anterior. */
    fun seekToPreviousSong() {
        val c = controller ?: return
        if (crossfadeEnabledCache) {
            sendCrossfadeCommand(MusicService.CMD_CROSSFADE_PREV) {
                val prevIndex = c.currentMediaItemIndex - 1
                if (prevIndex >= 0) c.seekTo(prevIndex, 0L) else c.seekTo(0L)
                publishState()
            }
            return
        }
        val prevIndex = c.currentMediaItemIndex - 1
        if (prevIndex >= 0) c.seekTo(prevIndex, 0L) else c.seekTo(0L)
        publishState()
    }

    /**
     * Enciende o apaga la Sesión DJ infinita. Ya NO manda ningún comando
     * al servicio: el flag es el único canal — el servicio lo colecciona
     * y arranca/para el motor, y la tarjeta lo colecciona para mostrarse.
     * La cola la pone el cliente con playDjQueue, así el arranque no
     * depende de que un comando llegue al servicio.
     */
    fun setEndlessDj(enabled: Boolean) {
        setEndlessDjActive(enabled)
    }

    /** Cola de Sesión DJ que llegó antes que el controlador (igual que pendingRestore). */
    private var pendingDjQueue: List<Song>? = null

    /**
     * Pone la cola de la Sesión DJ y la reproduce. Si el controlador aún
     * no está conectado (recién instalada), la cola queda pendiente y se
     * aplica al conectar. La parada programada se cancela y el repeat se
     * apaga: la sesión es infinita por relleno, no por repetir la cola.
     * El flag de sesión NO se toca aquí: lo pone quien arranca la sesión.
     */
    fun playDjQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        if (controller == null) {
            pendingDjQueue = songs
            return
        }
        applyDjQueue(songs)
    }

    private fun applyDjQueue(songs: List<Song>) {
        val c = controller ?: return
        _stopMode.value = null
        failedOnlineIndices.clear()
        registerKnownSongs(songs)
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, 0, 0L)
        c.shuffleModeEnabled = false
        c.repeatMode = Player.REPEAT_MODE_OFF
        c.prepare()
        c.playWhenReady = true
        publishState()
    }

    /**
     * Pide al servicio un fundido hacia la siguiente/anterior canción.
     * Si el servicio dice que no (sin fundido, sin siguiente, parada
     * programada...), corre el [onFallback] con el salto normal.
     */
    private fun sendCrossfadeCommand(action: String, onFallback: () -> Unit) {
        val c = controller ?: return
        scope.launch {
            val ok = try {
                val result = c.sendCustomCommand(
                    SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY
                ).await()
                result.resultCode == SessionResult.RESULT_SUCCESS
            } catch (_: Exception) {
                false
            }
            if (!ok) onFallback()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    /**
     * Posición en vivo del reproductor, sin esperar al ticker de 500 ms.
     * La usa la pantalla de letras para que la línea activa no vaya por
     * detrás del audio. Sin controlador, cae a la última posición
     * conocida del estado.
     */
    fun currentPositionMs(): Long =
        controller?.currentPosition?.coerceAtLeast(0) ?: _state.value.positionMs

    /** Salta directamente a un elemento de la cola. */
    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.playWhenReady = true
        }
        publishState()
    }

    /**
     * Resuelve el [Song] de un índice del timeline de Media3 (o null si ese
     * item no está en la biblioteca). Misma resolución que [publishState]:
     * UN SOLO espacio de índices en toda la app (el del timeline), sin
     * mezclar con listas filtradas.
     */
    fun songAtTimelineIndex(index: Int): Song? {
        val c = controller ?: return null
        if (index !in 0 until c.mediaItemCount) return null
        return c.getMediaItemAt(index).mediaId.toLongOrNull()?.let { libraryById[it] }
    }

    /** Elimina un elemento de la cola. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
        }
        publishState()
    }

    /**
     * Quita de la cola todas las canciones indicadas (por id). Si la que
     * suena está entre ellas, ExoPlayer avanza sola a la siguiente.
     * v1.0.9 (eliminar canciones).
     */
    fun removeSongsFromQueue(songIds: Set<Long>) {
        val c = controller ?: return
        if (songIds.isEmpty() || c.mediaItemCount == 0) return
        // Índices descendentes para que no se muevan al borrar.
        val indices = (c.mediaItemCount - 1 downTo 0).filter { i ->
            c.getMediaItemAt(i).mediaId.toLongOrNull() in songIds
        }
        indices.forEach { c.removeMediaItem(it) }
        publishState()
    }

    /** Mueve un elemento dentro de la cola (para reordenar por arrastre). */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val c = controller ?: return
        if (fromIndex in 0 until c.mediaItemCount &&
            toIndex in 0 until c.mediaItemCount && fromIndex != toIndex
        ) {
            c.moveMediaItem(fromIndex, toIndex)
        }
        publishState()
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        publishState()
    }

    /** Cicla: sin repetir → repetir todo → repetir uno. */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishState()
    }

    /**
     * Ciclo único del botón de modo de Now Playing:
     * sin repetir → repetir todo → repetir uno → aleatorio → sin repetir.
     * El aleatorio activa la mezcla CON repetir todo (bucle), para que la
     * música no se detenga al terminar la cola.
     */
    fun cyclePlayMode() {
        val c = controller ?: return
        when {
            c.shuffleModeEnabled -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_OFF
            }
            c.repeatMode == Player.REPEAT_MODE_OFF ->
                c.repeatMode = Player.REPEAT_MODE_ALL
            c.repeatMode == Player.REPEAT_MODE_ALL ->
                c.repeatMode = Player.REPEAT_MODE_ONE
            else -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
        }
        publishState()
    }

    fun stop() {
        _stopMode.value = null
        // Dejar de reproducir dentro de la lista apaga la sesión infinita.
        setEndlessDj(false)
        flushTracking()
        controller?.stop()
        publishState()
    }

    fun release() {
        flushTracking()
        stopTicker()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    // ----------------------------- Internos -----------------------------

    /** Publica el estado derivado del reproductor en el StateFlow. */
    private fun publishState() {
        val c = controller ?: return
        // La cola solo se reconstruye cuando cambia el timeline: en
        // cada evento (play/pausa, seek, cambio de pista) los ids son
        // los mismos y se reutiliza la lista anterior — misma instancia,
        // así los coleccionistas que solo leen la cola no recomponen.
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val items = if (ids == cachedQueueIds) {
            cachedQueue
        } else {
            val rebuilt = ids.mapNotNull { id ->
                id.toLongOrNull()?.let { libraryById[it] }
            }
            cachedQueueIds = ids
            cachedQueue = rebuilt
            cachedQueueIdsLong = rebuilt.map { it.id }
            rebuilt
        }
        val current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { libraryById[it] }
        val curIdx = c.currentMediaItemIndex
        // Vecinos del carrusel de portada: con aleatorio activado siguen el
        // orden aleatorio del timeline; con aleatorio desactivado son
        // currentIndex±1. REPEAT_MODE_OFF a propósito: sin wrap en los
        // extremos (el wrap es lógica de repeat, no del carrusel).
        val timeline = c.currentTimeline
        val shuffle = c.shuffleModeEnabled
        val prevNeighbor = if (curIdx >= 0 && timeline.windowCount > 0) {
            timeline.getPreviousWindowIndex(curIdx, Player.REPEAT_MODE_OFF, shuffle)
        } else C.INDEX_UNSET
        val nextNeighbor = if (curIdx >= 0 && timeline.windowCount > 0) {
            timeline.getNextWindowIndex(curIdx, Player.REPEAT_MODE_OFF, shuffle)
        } else C.INDEX_UNSET
        _state.value = _state.value.copy(
            currentSong = current,
            isPlaying = c.isPlaying,
            durationMs = if (current != null) current.durationMs.coerceAtLeast(c.duration.coerceAtLeast(0)) else 0L,
            queue = items,
            currentIndex = curIdx,
            shuffleEnabled = shuffle,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            prevNeighborIndex = prevNeighbor,
            nextNeighborIndex = nextNeighbor
        )
        // Guardar última reproducción (solo cuando cambia la canción o la cola,
        // para no escribir en cada evento del reproductor).
        if (current != null && items.isNotEmpty()) {
            val snapshot = current.id to cachedQueueIdsLong
            if (snapshot != lastSavedPlayback) {
                lastSavedPlayback = snapshot
                ioScope.launch {
                    try {
                        settings.setLastPlayback(current.id, cachedQueueIdsLong)
                    } catch (e: Exception) {
                        // No bloquear la reproducción si falla el guardado
                    }
                }
            }
        }
    }

    /** Actualiza la posición cada 500 ms mientras suena. */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                controller?.let {
                    _state.value = _state.value.copy(
                        positionMs = it.currentPosition.coerceAtLeast(0),
                        durationMs = if (_state.value.durationMs <= 0) it.duration.coerceAtLeast(0)
                        else _state.value.durationMs
                    )
                }
                // Registro de escucha: 500 ms más para la canción actual.
                try {
                    val current = _state.value.currentSong
                    if (current != null) {
                        if (current.id != lastTrackedId) {
                            flushTracking()
                            lastTrackedId = current.id
                        }
                        val prev = trackedMillis[current.id]
                        val meta = customMetadataCache[current.id]
                        trackedMillis[current.id] = Triple(
                            meta?.first?.ifBlank { current.title } ?: current.title,
                            meta?.second?.ifBlank { current.artist } ?: current.artist,
                            (prev?.third ?: 0L) + 500L
                        )
                        tickerTickCount++
                        if (tickerTickCount >= 120) {
                            tickerTickCount = 0
                            flushTracking()
                        }
                    }
                } catch (e: Exception) {
                    // El registro nunca debe romper el ticker
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Dibuja el signo de música sobre fondo neutro y lo devuelve como PNG. */
    private fun renderDefaultCover(): ByteArray {
        val size = 512
        val bitmap = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#333333"))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        // Cabeza de la nota (elipse inclinada)
        canvas.save()
        canvas.rotate(-20f, 190f, 380f)
        canvas.drawOval(android.graphics.RectF(120f, 330f, 260f, 430f), paint)
        canvas.restore()
        // Tallo
        canvas.drawRect(android.graphics.RectF(240f, 110f, 275f, 380f), paint)
        // Bandera
        val flag = android.graphics.Path().apply {
            moveTo(275f, 110f)
            cubicTo(360f, 120f, 400f, 160f, 385f, 230f)
            cubicTo(360f, 190f, 320f, 175f, 275f, 190f)
            close()
        }
        canvas.drawPath(flag, paint)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /** Convierte una canción en MediaItem con metadatos para la notificación. */
    private fun Song.toMediaItem(): MediaItem {
        val meta = customMetadataCache[id]
        val displayTitle = meta?.first?.ifBlank { title } ?: title
        val displayArtist = meta?.second?.ifBlank { artist } ?: artist
        val displayAlbum = meta?.third?.ifBlank { album } ?: album
        val artworkUri = customCoversCache[id] ?: albumArtUri

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
        if (!artworkUri.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(android.net.Uri.parse(artworkUri))
        } else {
            // Sin portada: el signo de música como arte directo
            metadataBuilder.setArtworkData(defaultCoverBytes)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
