// Enlace entre la interfaz (ViewModel) y el servicio Media3 mediante MediaController
package com.aural.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

/** Modos de repetición del reproductor. */
enum class RepeatMode { OFF, ONE, ALL }

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
    val isReady: Boolean = false
)

/**
 * Controlador de reproducción conectado al [MusicService].
 * Expone un StateFlow con todo lo que la UI necesita y métodos de acción.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackManager(private val context: Context) {

    private var controller: MediaController? = null
    private val settings = SettingsStore(context)

    /** Caché de metadatos personalizados: songId -> (título, artista, álbum). */
    private var customMetadataCache: Map<Long, Triple<String, String, String>> = emptyMap()
    /** Caché de portadas personalizadas: songId -> URL. */
    private var customCoversCache: Map<Long, String> = emptyMap()

    init {
        // Observar cambios en personalizaciones y refrescar notificación si afecta la canción actual
        CoroutineScope(Dispatchers.IO).launch {
            settings.customMetadata.collect { newMeta ->
                val oldMeta = customMetadataCache
                customMetadataCache = newMeta
                refreshCurrentIfAffected(oldMeta, newMeta)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            settings.customCovers.collect { newCovers ->
                val oldCovers = customCoversCache
                customCoversCache = newCovers
                refreshCurrentIfCoverAffected(oldCovers, newCovers)
            }
        }
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
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        if (index < 0) return
        val song = _state.value.currentSong ?: return
        try {
            c.replaceMediaItem(index, song.toMediaItem())
        } catch (e: Exception) {
            // Ignorar si falla
        }
    }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Canciones conocidas por id, para reconstruir la cola desde MediaItems. */
    private var libraryById: Map<Long, Song> = emptyMap()

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var tickerJob: kotlinx.coroutines.Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
            if (isPlaying) startTicker() else stopTicker()
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
        }
    }

    /** Registra la biblioteca para poder mapear MediaItems a canciones. */
    fun setLibrary(songs: List<Song>) {
        libraryById = songs.associateBy { it.id }
        publishState()
    }

    // ----------------------------- Acciones -----------------------------

    /** Reemplaza la cola con las canciones dadas e inicia en [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.playWhenReady = true
        publishState()
    }

    /** Añade una canción al final de la cola y la reproduce si nada suena. */
    fun enqueue(song: Song) {
        val c = controller ?: return
        c.addMediaItem(song.toMediaItem())
        if (c.mediaItemCount == 1) {
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
        controller?.seekToNext()
        publishState()
    }

    fun seekToPrevious() {
        val c = controller ?: return
        // Convención habitual: si lleva más de 3 s, volver al inicio
        if (c.currentPosition > 3_000) c.seekTo(0L) else c.seekToPrevious()
        publishState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    /** Salta directamente a un elemento de la cola. */
    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.playWhenReady = true
        }
        publishState()
    }

    /** Elimina un elemento de la cola. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
        }
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

    fun stop() {
        controller?.stop()
        publishState()
    }

    fun release() {
        stopTicker()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    // ----------------------------- Internos -----------------------------

    /** Publica el estado derivado del reproductor en el StateFlow. */
    private fun publishState() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).mapNotNull { i ->
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { libraryById[it] }
        }
        val current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { libraryById[it] }
        _state.value = _state.value.copy(
            currentSong = current,
            isPlaying = c.isPlaying,
            durationMs = if (current != null) current.durationMs.coerceAtLeast(c.duration.coerceAtLeast(0)) else 0L,
            queue = items,
            currentIndex = c.currentMediaItemIndex,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
        )
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
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Convierte una canción en MediaItem con metadatos para la notificación. */
    private fun Song.toMediaItem(): MediaItem {
        val meta = customMetadataCache[id]
        val displayTitle = meta?.first?.ifBlank { title } ?: title
        val displayArtist = meta?.second?.ifBlank { artist } ?: artist
        val displayAlbum = meta?.third?.ifBlank { album } ?: album
        val artworkUri = customCoversCache[id] ?: albumArtUri

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setArtist(displayArtist)
                    .setAlbumTitle(displayAlbum)
                    .setArtworkUri(
                        artworkUri?.let { android.net.Uri.parse(it) }
                    )
                    .build()
            )
            .build()
    }
}
