// Servicio de reproducción en segundo plano basado en Media3
package com.aural.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aural.player.AuralApp
import com.aural.player.MainActivity
import com.aural.player.R
import com.aural.player.data.MusicRepository
import com.aural.player.ui.island.IslandOverlayContent
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.theme.AuralTheme
import com.aural.player.ui.island.QuickAction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MediaSessionService que mantiene la reproducción activa cuando la app
 * pasa a segundo plano, publica una notificación multimedia propia y
 * expone los controles a auriculares y dispositivos Bluetooth.
 *
 * v1.0.7: notificación propia con botones personalizados — anterior,
 * play/pausa, siguiente (estándar de Media3) + favorita y rotación del
 * modo de repetición (custom layout). La barra de progreso arrastrable
 * NO es posible en notificaciones (RemoteViews); el seek con gestos vive
 * en la isla dinámica.
 */
@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Listener que (re)inicializa el ecualizador cuando ExoPlayer crea su sesión de audio. */
    private var eqSessionListener: androidx.media3.common.Player.Listener? = null
    private var lastEqSessionId: Int = 0

    // ------------------------- Fundido entre canciones -------------------------
    // Segundo reproductor para el crossfade: mientras el principal apaga la
    // canción que termina, este enciende la siguiente y se cruzan los
    // volúmenes. Al terminar el fundido intercambian los papeles
    // (MediaSession.setPlayer) y el que era principal queda de reserva.
    private var fadePlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var fadeEqListener: androidx.media3.common.Player.Listener? = null
    private var lastFadeEqSessionId: Int = 0
    private var fadeWatchJob: Job? = null
    private var rampJob: Job? = null
    private var crossfadeActive = false
    /** Índice del timeline para el que está preparado el segundo
     *  reproductor (-1 = sin preparar). */
    private var fadePreparedForIndex = -1
    private var fadeEffectiveMs = 0L
    private var crossfadeEnabledCache = false
    private var crossfadeSecondsCache = 5

    companion object {
        const val CMD_TOGGLE_FAVORITE = "aural_toggle_favorite"
        const val CMD_CYCLE_REPEAT = "aural_cycle_repeat"
        /** Fundido al saltar a la siguiente/anterior (lo pide PlaybackManager). */
        const val CMD_CROSSFADE_NEXT = "aural_crossfade_next"
        const val CMD_CROSSFADE_PREV = "aural_crossfade_prev"
        /** Acción del despertador: sonar la canción o playlist elegida. */
        const val ACTION_ALARM_PLAY = "com.aural.player.ALARM_PLAY"
        const val EXTRA_ALARM_SONG_IDS = "alarm_song_ids"
    }

    override fun onCreate() {
        super.onCreate()

        // Configuración del reproductor ExoPlayer
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true // gestiona automáticamente el foco de audio
            )
            .setWakeMode(C.WAKE_MODE_LOCAL) // parcial: la CPU sigue despierta con pantalla apagada; el lock de Wi-Fi no hace falta (todo es local hasta que llegue el streaming Pro)
            // v1.0.9: al desconectar los audífonos (cable o Bluetooth) la música
            // se pausa sola (Android avisa con AUDIO_BECOMING_NOISY).
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Ecualizador y normalización atados a la sesión de audio
        EqualizerManager.init(this, player.audioSessionId)
        // v1.2.0: player.audioSessionId es 0 hasta que suena el primer audio,
        // así que el init de arriba no crea nada en ese caso. Re-inicializar
        // cuando la sesión se crea de verdad (o cambia).
        val eqListener = object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != lastEqSessionId) {
                    lastEqSessionId = audioSessionId
                    EqualizerManager.init(this@MusicService, audioSessionId)
                }
            }
        }
        eqSessionListener = eqListener
        player.addListener(eqListener)
        // Nota: ExoPlayer realiza transiciones gapless (sin silencio entre pistas)
        // de forma automática cuando las pistas consecutivas son compatibles.

        // Sesión de medios: conecta el reproductor con la notificación y controles externos
        val sessionBuilder = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCallback(AuralSessionCallback())
        mediaSession = sessionBuilder.build()

        // Notificación multimedia propia de Media3 (usa el icono de la app del manifest)
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

        // v1.0.7: botones personalizados en la notificación (favorita + repetición).
        // Se reconstruyen cuando cambian la canción, los favoritos o el modo.
        startCustomLayoutUpdates()
        // v1.0.7: isla dinámica (overlay) cuando está activada y hay música
        // cargada fuera de la app.
        startIslandOverlay()
        // Fundido entre canciones: segundo reproductor + vigilancia.
        startCrossfadeEngine(player)
    }

    /** Crea el segundo reproductor del fundido y arranca su vigilancia. */
    private fun startCrossfadeEngine(mainPlayer: androidx.media3.exoplayer.ExoPlayer) {
        val app = application as? AuralApp ?: return
        // El segundo reproductor NO gestiona el foco de audio (eso lo hace
        // el principal: si lo pidiera también, al arrancar el fundido le
        // quitaría el foco al principal y lo pausaría) ni el ruido al
        // desconectar audífonos (el espejo de play/pausa lo cubre).
        val fade = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(false)
            .build()
        fade.volume = 0f
        fadePlayer = fade
        // Ecualizador del segundo reproductor (su propio juego de efectos).
        val fadeEq = object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != lastFadeEqSessionId) {
                    lastFadeEqSessionId = audioSessionId
                    EqualizerManager.initSecondSession(this@MusicService, audioSessionId)
                }
            }
        }
        fadeEqListener = fadeEq
        fade.addListener(fadeEq)
        // Espejo de play/pausa y cancelación del fundido, en ambos.
        fade.addListener(makeFadeControlListener(fade))
        mainPlayer.addListener(makeFadeControlListener(mainPlayer))
        // Ajustes del fundido en caché (el motor los lee sin suspender).
        serviceScope.launch {
            app.settings.crossfadeEnabled.collect { crossfadeEnabledCache = it }
        }
        serviceScope.launch {
            app.settings.crossfadeSeconds.collect { crossfadeSecondsCache = it }
        }
        // Vigilancia: cada 250 ms mira si falta poco para el final.
        fadeWatchJob = serviceScope.launch {
            while (true) {
                delay(250)
                try {
                    fadeWatchTick()
                } catch (_: Exception) {
                    // La vigilancia nunca debe romper la reproducción
                }
            }
        }
    }

    /**
     * Listener de control del fundido, uno por reproductor: solo actúa
     * cuando su reproductor es el de la sesión (el otro es la voz del
     * fundido y no manda). Como el filtro es dinámico, no hay que mover
     * listeners al intercambiar los papeles.
     */
    private fun makeFadeControlListener(owner: Player) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (owner !== mediaSession?.player || !crossfadeActive) return
            val fade = fadePlayer ?: return
            if (fade === owner) return
            // Pausa/reanudación durante el fundido: la segunda voz sigue
            // a la principal (si no, seguiría sonando con todo en pausa).
            if (isPlaying) fade.play() else fade.pause()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (owner !== mediaSession?.player || !crossfadeActive) return
            // Parada total (botón parar) en pleno fundido: cancelar.
            if (playbackState == Player.STATE_IDLE) abortFade()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (owner !== mediaSession?.player || !crossfadeActive) return
            when (reason) {
                // El usuario buscó con el slider en pleno fundido: se
                // cancela y la canción actual sigue normal.
                Player.DISCONTINUITY_REASON_SEEK -> abortFade()
                // La canción terminó antes de que la rampa acabara
                // (retraso del bucle): cerrar el fundido ya, que la
                // segunda voz está casi a todo volumen.
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> finishFade()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (owner !== mediaSession?.player) return
            // Cola nueva o reordenada: lo preparado ya no vale.
            fadePreparedForIndex = -1
            if (crossfadeActive) abortFade()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ALARM_PLAY) {
            handleAlarmPlay(intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * La alarma: pone la canción o playlist elegida en el orden
     * configurado. Si ya está sonando música (el usuario está despierto
     * y escuchando), no roba la cola.
     */
    private fun handleAlarmPlay(intent: Intent) {
        serviceScope.launch {
            try {
                val player = mediaSession?.player ?: return@launch
                if (player.isPlaying) return@launch
                val idArray = intent.getLongArrayExtra(EXTRA_ALARM_SONG_IDS)
                    ?: return@launch
                if (idArray.isEmpty()) return@launch
                val order = idArray.withIndex().associate { it.value to it.index }
                val songs = withContext(Dispatchers.IO) {
                    MusicRepository(this@MusicService).scanSongs()
                }.filter { it.id in order }.sortedBy { order[it.id] }
                if (songs.isEmpty()) return@launch
                val items = songs.map { song ->
                    MediaItem.Builder()
                        .setMediaId(song.id.toString())
                        .setUri(song.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .build()
                        )
                        .build()
                }
                player.setMediaItems(items)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                // La alarma nunca debe crashear el servicio
            }
        }
    }

    /** Callback de sesión: comandos personalizados de la notificación. */
    private inner class AuralSessionCallback : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val app = application as? AuralApp ?: return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
            )
            when (customCommand.customAction) {
                CMD_TOGGLE_FAVORITE -> {
                    val songId = app.playback.state.value.currentSong?.id
                    if (songId != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            app.settings.toggleFavorite(songId)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_CYCLE_REPEAT -> {
                    app.playback.cyclePlayMode()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_CROSSFADE_NEXT -> {
                    val ok = crossfadeToNext()
                    return Futures.immediateFuture(
                        SessionResult(
                            if (ok) SessionResult.RESULT_SUCCESS
                            else SessionResult.RESULT_ERROR_UNKNOWN
                        )
                    )
                }
                CMD_CROSSFADE_PREV -> {
                    val ok = crossfadeToPrev()
                    return Futures.immediateFuture(
                        SessionResult(
                            if (ok) SessionResult.RESULT_SUCCESS
                            else SessionResult.RESULT_ERROR_UNKNOWN
                        )
                    )
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** Reconstruye el custom layout con el estado actual. */
    private fun startCustomLayoutUpdates() {
        val app = application as? AuralApp ?: return
        val session = mediaSession ?: return
        serviceScope.launch {
            combine(
                app.playback.state,
                app.settings.favorites
            ) { playerState, favorites ->
                // El layout solo depende de la canción, si es favorita y
                // el modo de repetición: la posición (que cambia cada
                // 500 ms) no lo mueve, así que se mapea a esos tres
                // valores y se deduplica.
                Triple(
                    playerState.currentSong?.id,
                    playerState.currentSong?.id?.let { favorites.contains(it) } ?: false,
                    playerState.repeatMode
                )
            }
                .distinctUntilChanged()
                .collect { (songId, isFav, repeatMode) ->
                    if (songId == null) return@collect
                    val repeatIcon = when (repeatMode) {
                    RepeatMode.OFF -> R.drawable.ic_notif_repeat_off
                    RepeatMode.ONE -> R.drawable.ic_notif_repeat_one
                    RepeatMode.ALL -> R.drawable.ic_notif_repeat
                }
                val repeatName = when (repeatMode) {
                    RepeatMode.OFF -> "Repetir: desactivado"
                    RepeatMode.ONE -> "Repetir: una"
                    RepeatMode.ALL -> "Repetir: todas"
                }
                session.setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName(if (isFav) "Quitar de favoritas" else "Marcar favorita")
                            .setIconResId(
                                if (isFav) R.drawable.ic_notif_favorite
                                else R.drawable.ic_notif_favorite_border
                            )
                            .setSessionCommand(
                                SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY)
                            )
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName(repeatName)
                            .setIconResId(repeatIcon)
                            .setSessionCommand(
                                SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY)
                            )
                            .build()
                    )
                )
            }
        }
    }

    /** El sistema llama a este método para entregar la sesión a los clientes. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // =======================================================================
    // FUNDIDO ENTRE CANCIONES (crossfade)
    // El principal suena; el segundo entra con la siguiente canción unos
    // segundos antes del final y se cruzan los volúmenes con curva de
    // potencia igual (el oído percibe el volumen constante, como Spotify).
    // Al acabar, la sesión cambia al segundo reproductor y los papeles se
    // intercambian: la cola, el aleatorio y la repetición viajan con los
    // MediaItems, así que nada de eso se toca.
    // =======================================================================

    /** Siguiente canción del timeline (null si no hay: fin de cola sin repetir). */
    private fun nextWindowIndex(player: Player): Int? {
        val timeline = player.currentTimeline
        if (timeline.windowCount == 0) return null
        val idx = timeline.getNextWindowIndex(
            player.currentMediaItemIndex, player.repeatMode, player.shuffleModeEnabled
        )
        if (idx == C.INDEX_UNSET || idx == player.currentMediaItemIndex) return null
        return idx
    }

    /** Canción anterior del timeline (null si no hay). */
    private fun prevWindowIndex(player: Player): Int? {
        val timeline = player.currentTimeline
        if (timeline.windowCount == 0) return null
        val idx = timeline.getPreviousWindowIndex(
            player.currentMediaItemIndex, player.repeatMode, player.shuffleModeEnabled
        )
        if (idx == C.INDEX_UNSET || idx == player.currentMediaItemIndex) return null
        return idx
    }

    /** Vigilancia del final de la canción: prepara y dispara el fundido. */
    private fun fadeWatchTick() {
        val main = mediaSession?.player ?: return
        if (!main.isPlaying || crossfadeActive) return
        if (!crossfadeEnabledCache) return
        // Con parada programada no hay fundido: la canción termina y la
        // pausa la gestiona PlaybackManager como siempre.
        if ((application as? AuralApp)?.playback?.stopMode?.value != null) return
        // Repetir una: la "siguiente" es la misma canción, no se funde.
        if (main.repeatMode == Player.REPEAT_MODE_ONE) return
        val duration = main.duration
        if (duration <= 0) return
        val nextIndex = nextWindowIndex(main) ?: return
        val fadeMs = crossfadeSecondsCache * 1000L
        val remaining = duration - main.currentPosition
        // Preparar la siguiente con margen (la cola + prepare no deben
        // comerse el fundido). Si la cola cambió, el índice ya no vale.
        if (fadePreparedForIndex != nextIndex) {
            if (remaining <= fadeMs + 5000) prepareFadePlayer(nextIndex)
            return
        }
        if (remaining <= fadeMs + 250) startFade(nextIndex)
    }

    /**
     * Carga en el segundo reproductor la cola completa (en el orden del
     * timeline del principal) empezando en [targetIndex], lista para
     * sonar en silencio. El aleatorio se conserva trasplantando el orden
     * exacto: setMediaItems vuelve a barajar (cloneAndSet), así que se
     * fija un orden identidad sobre la lista ya barajada — el interruptor
     * de aleatorio sigue leyendo true y la cola no cambia de orden.
     */
    private fun prepareFadePlayer(targetIndex: Int): Boolean {
        val main = mediaSession?.player ?: return false
        val fade = fadePlayer ?: return false
        if (main.mediaItemCount == 0 || targetIndex !in 0 until main.mediaItemCount) return false
        return try {
            val items = (0 until main.mediaItemCount).map { main.getMediaItemAt(it) }
            fade.volume = 0f
            fade.setMediaItems(items, targetIndex, 0L)
            fade.shuffleModeEnabled = main.shuffleModeEnabled
            fade.repeatMode = main.repeatMode
            fade.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(items.size))
            fade.setPlaybackSpeed(main.playbackParameters.speed)
            fade.prepare()
            fade.playWhenReady = false
            fadePreparedForIndex = targetIndex
            true
        } catch (_: Exception) {
            fadePreparedForIndex = -1
            false
        }
    }

    /** Arranca el fundido hacia [targetIndex]. Devuelve false si no se pudo. */
    private fun startFade(targetIndex: Int): Boolean {
        val main = mediaSession?.player ?: return false
        val fade = fadePlayer ?: return false
        if (crossfadeActive || !crossfadeEnabledCache) return false
        if ((application as? AuralApp)?.playback?.stopMode?.value != null) return false
        if (targetIndex == main.currentMediaItemIndex ||
            targetIndex !in 0 until main.mediaItemCount
        ) return false
        if (fadePreparedForIndex != targetIndex && !prepareFadePlayer(targetIndex)) return false
        // Fundido efectivo: el ajuste, limitado a la mitad de cada canción
        // para que las cortas también fundan en vez de saltarse el efecto.
        val durMain = main.duration.takeIf { it > 0 } ?: return false
        val durTarget = fade.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val effective = minOf(crossfadeSecondsCache * 1000L, durMain / 2, durTarget / 2)
        if (effective < 1000) return false
        fadeEffectiveMs = effective
        crossfadeActive = true
        fade.volume = 0f
        fade.playWhenReady = true
        rampJob?.cancel()
        rampJob = serviceScope.launch {
            while (crossfadeActive) {
                // t avanza con la posición real de la segunda voz: si se
                // pausa en pleno fundido, la rampa se congela y sigue al
                // reanudar, sin saltos de volumen.
                val t = (fade.currentPosition.toFloat() / fadeEffectiveMs).coerceIn(0f, 1f)
                fade.volume = sin(t * PI / 2).toFloat()
                main.volume = cos(t * PI / 2).toFloat()
                if (main.playbackParameters.speed != fade.playbackParameters.speed) {
                    fade.setPlaybackSpeed(main.playbackParameters.speed)
                }
                if (t >= 1f) {
                    finishFade()
                    break
                }
                delay(50)
            }
        }
        return true
    }

    /** Cierra el fundido: la segunda voz queda de principal y se intercambian los papeles. */
    private fun finishFade() {
        if (!crossfadeActive) return
        crossfadeActive = false
        rampJob?.cancel()
        rampJob = null
        val oldMain = mediaSession?.player as? ExoPlayer ?: return
        val fade = fadePlayer ?: return
        fade.volume = 1f
        oldMain.volume = 0f
        // Pausar antes del cambio: si no, al terminar su canción avanzaría
        // solo a la siguiente y sonaría dos veces.
        oldMain.pause()
        mediaSession?.setPlayer(fade)
        fadePlayer = oldMain
        fadePreparedForIndex = -1
        // El ecualizador principal pasa a la sesión nueva (el juego de
        // efectos de la segunda voz ya estaba en esta sesión).
        if (fade.audioSessionId != 0) {
            lastEqSessionId = fade.audioSessionId
            EqualizerManager.init(this, fade.audioSessionId)
        }
    }

    /** Cancela el fundido y deja todo como estaba (sin cambiar de canción). */
    private fun abortFade() {
        if (!crossfadeActive) return
        crossfadeActive = false
        rampJob?.cancel()
        rampJob = null
        fadePlayer?.pause()
        fadePlayer?.volume = 0f
        mediaSession?.player?.volume = 1f
        fadePreparedForIndex = -1
    }

    /** Fundido al saltar a la siguiente (lo pide PlaybackManager). */
    private fun crossfadeToNext(): Boolean {
        val main = mediaSession?.player ?: return false
        val target = nextWindowIndex(main) ?: return false
        return startFade(target)
    }

    /** Fundido al saltar a la anterior (lo pide PlaybackManager). */
    private fun crossfadeToPrev(): Boolean {
        val main = mediaSession?.player ?: return false
        val target = prevWindowIndex(main) ?: return false
        return startFade(target)
    }

    // =======================================================================
    // ISLA DINÁMICA (v1.0.7): overlay con WindowManager.
    // Visible solo si: activada en ajustes + hay canción cargada + app en
    // segundo plano. Requiere permiso SYSTEM_ALERT_WINDOW.
    // =======================================================================

    private var islandView: ComposeView? = null
    private var islandParams: WindowManager.LayoutParams? = null
    private var islandLifecycle: IslandLifecycleOwner? = null
    // La X oculta la isla "por ahora" (NO la apaga en ajustes): reaparece con
    // canción nueva, cambio de play/pausa o al salir y volver a entrar a Aural.
    private var islandDismissed = false
    private var lastIslandSongId: Long? = null
    private var lastIslandPlaying: Boolean? = null
    private var lastIslandForeground: Boolean? = null
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private fun startIslandOverlay() {
        val app = application as? AuralApp ?: return
        serviceScope.launch {
            combine(
                app.settings.islandEnabled,
                app.playback.state,
                app.isForeground
            ) { enabled, playerState, foreground ->
                Triple(enabled, playerState, foreground)
            }.collect { (enabled, playerState, foreground) ->
                val songId = playerState.currentSong?.id
                val playing = playerState.isPlaying
                if (songId != lastIslandSongId || playing != lastIslandPlaying ||
                    (lastIslandForeground == true && !foreground)
                ) {
                    islandDismissed = false
                }
                lastIslandSongId = songId
                lastIslandPlaying = playing
                lastIslandForeground = foreground
                val show = enabled &&
                        songId != null &&
                        !foreground &&
                        !islandDismissed &&
                        Settings.canDrawOverlays(this@MusicService)
                if (show) showIslandOverlay() else hideIslandOverlay()
            }
        }
    }

    private fun showIslandOverlay() {
        if (islandView != null) return
        val app = application as? AuralApp ?: return
        // Posición FIJA: centrada, justo debajo de la barra de estado
        // (donde cae el orificio de la cámara). Sin arrastre.
        val density = resources.displayMetrics.density
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId)
        else (24 * density).toInt()
        val params = WindowManager.LayoutParams(
            // v1.0.8: tamaño exacto de la píldora (ver collapsedWindowPx())
            // en vez de WRAP_CONTENT, para no bloquear toques fuera de ella.
            collapsedWindowPx().first,
            collapsedWindowPx().second,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // -16dp: compensa el padding anti-recorte de la sombra para
            // que la píldora quede a statusBar + 10dp como antes.
            y = statusBarPx + (10 * density).toInt() - (16 * density).toInt()
        }
        val composeView = ComposeView(this).apply {
            // El servicio no es un LifecycleOwner; se usa un ciclo de vida
            // manual mínimo para que Compose funcione en el overlay.
            // OJO: el SavedStateRegistry debe hacer attach con el lifecycle
            // en INITIALIZED (si no, SavedStateRegistryController lanza
            // "Restarter must be created only during owner's initialization stage").
            val owner = IslandLifecycleOwner()
            val savedStateOwner = IslandSavedStateOwner(owner.lifecycle)
            owner.doResume()
            islandLifecycle = owner
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(IslandViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                // La isla vive en un overlay del servicio: se envuelve en el
                // tema guardado para que siga al tema de la app.
                val themeName by app.settings.appTheme
                    .collectAsState(initial = "BOSQUE")
                val theme = try {
                    AppTheme.valueOf(themeName)
                } catch (_: Exception) {
                    AppTheme.BOSQUE
                }
                AuralTheme(theme = theme) {
                    IslandOverlayContent(
                        app = app,
                        onClose = {
                            // La X la oculta por ahora; NO toca el ajuste.
                            islandDismissed = true
                            hideIslandOverlay()
                        },
                        onWindowSync = { w, h -> syncIslandWindow(w, h) }
                    )
                }
            }
        }
        try {
            windowManager.addView(composeView, params)
            islandView = composeView
            islandParams = params
        } catch (_: Exception) {
            // Sin permiso de overlay o fallo del WindowManager: no se muestra.
            islandView = null
            islandParams = null
        }
    }

    // La ventana sigue el morph de la isla cuadro por cuadro (vía
    // onWindowSync): +32dp de aire para que la sombra (14dp) respire sin
    // recortarse. Sin saltos instantáneos de tamaño, que en algunos equipos
    // descentraban la píldora unos milisegundos (se veía como un panel que
    // entraba desde la derecha al abrir, o una pill desde la izquierda al
    // cerrar). La gravedad la mantiene siempre centrada.
    private fun syncIslandWindow(wDp: Float, hDp: Float) {
        val view = islandView ?: return
        val params = islandParams ?: return
        val metrics = resources.displayMetrics
        val d = metrics.density
        params.width = ((wDp + 32f) * d).toInt()
            .coerceAtMost(metrics.widthPixels)
        params.height = ((hDp + 32f) * d).toInt()
            .coerceAtMost(metrics.heightPixels)
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { }
    }

    /**
     * v1.0.8: tamaño exacto de la ventana en estado colapsado.
     * La píldora mide 135×44dp + 16dp de aire anti-recorte por lado.
     * Antes era WRAP_CONTENT, pero la caja exterior (fillMaxSize, del fix
     * de la sombra) medía la pantalla completa y la ventana invisible
     * bloqueaba los toques de la app de abajo en toda la pantalla.
     */
    private fun collapsedWindowPx(): Pair<Int, Int> {
        val d = resources.displayMetrics.density
        return Pair(((135f + 32f) * d).toInt(), ((44f + 32f) * d).toInt())
    }

    private fun hideIslandOverlay() {
        islandView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) { }
        }
        try {
            islandLifecycle?.doDestroy()
        } catch (_: Exception) { }
        islandView = null
        islandParams = null
        islandLifecycle = null
    }

    /** Ciclo de vida mínimo para el ComposeView del overlay.
     *  Se crea en INITIALIZED y se avanza manualmente: el
     *  SavedStateRegistryController exige INITIALIZED en el attach. */
    private class IslandLifecycleOwner : androidx.lifecycle.LifecycleOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this)

        fun doResume() {
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }

        fun doDestroy() {
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        }

        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private class IslandViewModelStoreOwner :
        androidx.lifecycle.ViewModelStoreOwner {
        override val viewModelStore = androidx.lifecycle.ViewModelStore()
    }

    private class IslandSavedStateOwner(
        private val parentLifecycle: androidx.lifecycle.Lifecycle
    ) : androidx.savedstate.SavedStateRegistryOwner {
        private val controller = androidx.savedstate.SavedStateRegistryController.create(this)
        init {
            controller.performAttach()
            controller.performRestore(null)
        }
        override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
            get() = controller.savedStateRegistry
        override val lifecycle: androidx.lifecycle.Lifecycle get() = parentLifecycle
    }

    /** Liberar recursos cuando el servicio se destruye. */
    override fun onDestroy() {
        fadeWatchJob?.cancel()
        rampJob?.cancel()
        fadeWatchJob = null
        rampJob = null
        fadeEqListener?.let { fadePlayer?.removeListener(it) }
        fadeEqListener = null
        runCatching { fadePlayer?.release() }
        fadePlayer = null
        serviceScope.cancel()
        eqSessionListener?.let { mediaSession?.player?.removeListener(it) }
        eqSessionListener = null
        EqualizerManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // La notificación en primer plano la genera Media3 automáticamente
    // a partir de los metadatos actuales del reproductor.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Si no estaba sonando, detener el servicio al cerrar la app
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
}
