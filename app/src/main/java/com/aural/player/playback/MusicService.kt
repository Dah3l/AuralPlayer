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
import androidx.media3.exoplayer.ExoPlayer
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
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

    // ------------------------- Mezcla DJ -------------------------
    /** Mezcla DJ activada (implica fundido). */
    private var djMixEnabledCache = false
    /** BPM guardados (songId -> BPM real, sin plegar). */
    private var bpmCache: Map<Long, Float> = emptyMap()
    /** Velocidad de la voz del fundido en este fundido (1f = sin igualar). */
    private var fadeTargetRate = 1f
    /** Si este fundido lleva cambio de bajos (tempo igualado o compatible). */
    private var fadeBassSwap = false
    /** Velocidad que el usuario tiene puesta (la del selector del Now
     *  Playing): a ella vuelve la canción tras la mezcla. */
    private var userSpeed = 1f
    /** Última velocidad puesta por el glide (para distinguir sus cambios
     *  de los del usuario en onPlaybackParametersChanged). */
    private var lastGlideSpeed = 1f
    /** Glide de vuelta al tempo tras una mezcla (null = no hay). */
    @Volatile
    private var tempoRecoveryJob: Job? = null
    // Sesión DJ infinita: la cola se reordena sola por tempo y se
    // rellena sola cuando se acaba. El pool es toda la biblioteca.
    private var endlessDjSession = false
    private var endlessPool: List<MediaItem> = emptyList()
    private val endlessRecentlyPlayed = ArrayDeque<Long>()
    private var lastBpmMapSize = 0

    /** Análisis de BPM en curso (ids) + trabajo de análisis. */
    private var analysisJob: Job? = null
    private val analyzingIds = mutableSetOf<Long>()
    private val analyzedIds = mutableSetOf<Long>()
    private var lastAnalyzedSongId: Long? = null

    companion object {
        const val CMD_TOGGLE_FAVORITE = "aural_toggle_favorite"
        const val CMD_CYCLE_REPEAT = "aural_cycle_repeat"
        /** Fundido al saltar a la siguiente/anterior (lo pide PlaybackManager). */
        const val CMD_CROSSFADE_NEXT = "aural_crossfade_next"
        const val CMD_CROSSFADE_PREV = "aural_crossfade_prev"
        /** Rellenar la cola infinita cuando quedan pocas canciones. */
        private const val ENDLESS_REFILL_THRESHOLD = 5
        /** Canciones recientes que el relleno no repite. */
        private const val ENDLESS_RECENT_CAP = 50
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
        // Análisis de BPM en segundo plano (para la Mezcla DJ).
        startBpmAnalysis()
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
        // Ajustes y BPM de la Mezcla DJ en caché.
        serviceScope.launch {
            app.settings.djMixEnabled.collect {
                djMixEnabledCache = it
                // Apagar la Mezcla DJ apaga la sesión infinita.
                if (!it && endlessDjSession) {
                    endlessDjSession = false
                    app.playback.setEndlessDjActive(false)
                }
            }
        }
        // Sesión DJ infinita: el cliente enciende el flag y el servicio
        // arranca/para el motor. Sin comandos: el flag es el único canal
        // y no se puede perder en silencio.
        serviceScope.launch {
            app.playback.endlessDjActive.collect { active ->
                if (active) startEndlessEngine() else endlessDjSession = false
            }
        }
        serviceScope.launch {
            app.settings.bpmMap.collect {
                bpmCache = it
                // Sesión infinita: cada BPM nuevo reordena la cola
                // que viene por tempo.
                if (endlessDjSession && it.size > lastBpmMapSize) {
                    reorderDjTail()
                }
                lastBpmMapSize = it.size
            }
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
            if (owner !== mediaSession?.player) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // El usuario buscó con el slider: se cancela el fundido
                // (si lo había) y el glide de vuelta al tempo, y la
                // canción sigue a la velocidad del usuario.
                if (crossfadeActive) abortFade()
                stopTempoRecovery()
                resetSpeedToUser()
                return
            }
            if (!crossfadeActive) return
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                // La canción terminó antes de que la rampa acabara
                // (retraso del bucle): cerrar el fundido ya, que la
                // segunda voz está casi a todo volumen.
                finishFade()
            }
        }

        override fun onPlaybackParametersChanged(
            playbackParameters: androidx.media3.common.PlaybackParameters
        ) {
            if (owner !== mediaSession?.player) return
            // Los sets del glide se mueven de a milésimas: si el cambio
            // es así de pequeño, es el glide, no el usuario (llega por
            // el looper con retraso, así que no vale un flag).
            if (abs(playbackParameters.speed - lastGlideSpeed) < 0.005f) return
            // Cambio de velocidad del usuario (selector del Now Playing):
            // anota la elegida y cancela el glide (el selector ya puso
            // la velocidad; aquí solo se para el glide).
            userSpeed = playbackParameters.speed
            stopTempoRecovery()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (owner !== mediaSession?.player) return
            if (crossfadeActive) return
            // La canción cambió sola sin fundido (o el usuario saltó
            // directo): se acabó el glide y la nueva suena a la
            // velocidad del usuario, no a la que dejó el glide a medias.
            stopTempoRecovery()
            resetSpeedToUser()
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
    private fun nextWindowIndex(player: Player): Int? =
        nextWindowIndexFrom(player, player.currentMediaItemIndex)

    /** Siguiente canción desde [fromIndex] (para encadenar varias). */
    private fun nextWindowIndexFrom(player: Player, fromIndex: Int): Int? {
        val timeline = player.currentTimeline
        if (timeline.windowCount == 0) return null
        val idx = timeline.getNextWindowIndex(
            fromIndex, player.repeatMode, player.shuffleModeEnabled
        )
        if (idx == C.INDEX_UNSET || idx == fromIndex) return null
        return idx
    }

    /** Como el de arriba, pero sobre una foto del timeline ya tomada
     *  (el análisis de BPM no puede releer el timeline vivo: al abrir,
     *  la cola se está aplicando y cambia entre dos lecturas). */
    private fun nextWindowIndexFrom(timeline: Timeline, player: Player, fromIndex: Int): Int? {
        if (timeline.windowCount == 0) return null
        val idx = timeline.getNextWindowIndex(
            fromIndex, player.repeatMode, player.shuffleModeEnabled
        )
        if (idx == C.INDEX_UNSET || idx == fromIndex) return null
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
            // Mezcla DJ: la que entra toma el tempo de la que sale (o
            // suena a su tempo si son doble/mitad); sin mezcla, la
            // segunda voz copia la velocidad del usuario, como antes.
            val match = if (djMixEnabledCache) {
                val outId = main.currentMediaItem?.mediaId?.toLongOrNull()
                val inId = items[targetIndex].mediaId.toLongOrNull()
                BpmAnalysis.matchTempo(
                    bpmOutRaw = outId?.let { bpmCache[it] },
                    bpmInRaw = inId?.let { bpmCache[it] },
                    speedOut = main.playbackParameters.speed,
                    baseSpeed = userSpeed
                )
            } else null
            fadeTargetRate = match?.rate ?: main.playbackParameters.speed
            fadeBassSwap = match?.bassSwap ?: false
            fade.setPlaybackSpeed(fadeTargetRate)
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
        // Un glide anterior no sigue: la nueva mezcla manda.
        stopTempoRecovery()
        fade.volume = 0f
        fade.playWhenReady = true
        // Cambio de bajos: la que sale pierde los graves y la que entra
        // los gana, durante el fundido (misma t que el volumen).
        val bassSwap = fadeBassSwap
        val savedBassDb = if (bassSwap) EqualizerManager.getSavedBandLevelDb(0) else 0
        val minBassDb = if (bassSwap) EqualizerManager.levelRangeDb().first else 0
        if (bassSwap) EqualizerManager.bassSwapActive = true
        rampJob?.cancel()
        rampJob = serviceScope.launch {
            while (crossfadeActive) {
                // t avanza con la posición real de la segunda voz: si se
                // pausa en pleno fundido, la rampa se congela y sigue al
                // reanudar, sin saltos de volumen.
                val t = (fade.currentPosition.toFloat() / fadeEffectiveMs).coerceIn(0f, 1f)
                fade.volume = sin(t * PI / 2).toFloat()
                main.volume = cos(t * PI / 2).toFloat()
                if (bassSwap) {
                    val outBass = (savedBassDb + (minBassDb - savedBassDb) * t).roundToInt()
                    val inBass = (minBassDb + (savedBassDb - minBassDb) * t).roundToInt()
                    EqualizerManager.setBandLevelTransient(false, 0, outBass)
                    EqualizerManager.setBandLevelTransient(true, 0, inBass)
                } else if (main.playbackParameters.speed != fade.playbackParameters.speed) {
                    // Sin mezcla, la segunda voz sigue a la principal si
                    // el usuario cambia la velocidad en pleno fundido.
                    // Con mezcla no: su velocidad es el tempo igualado.
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
        // Los papeles se intercambian también en el ecualizador: cada
        // juego de efectos sigue a su sesión (re-crear el juego
        // principal con init() dejaba al nuevo principal con DOS EQs
        // y a la reserva sin ninguno).
        EqualizerManager.swapRoles()
        // Los bajos vuelven a su nivel: la nueva principal ya está en
        // el suyo (la rampa terminó ahí) y la reserva se restaura.
        if (fadeBassSwap) {
            EqualizerManager.setBandLevelTransient(
                true, 0, EqualizerManager.getSavedBandLevelDb(0)
            )
            EqualizerManager.bassSwapActive = false
        }
        fadeBassSwap = false
        // La que entra venía al tempo de la que sale: vuelve sola a la
        // velocidad del usuario en ~20 s (si no era ya esa).
        if (fadeTargetRate != userSpeed) {
            startTempoRecovery(fromRate = fadeTargetRate, toRate = userSpeed)
        }
        fadeTargetRate = 1f
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
        // Los bajos vuelven a su nivel en los dos juegos.
        if (fadeBassSwap) {
            val saved = EqualizerManager.getSavedBandLevelDb(0)
            EqualizerManager.setBandLevelTransient(false, 0, saved)
            EqualizerManager.setBandLevelTransient(true, 0, saved)
            EqualizerManager.bassSwapActive = false
        }
        fadeBassSwap = false
        fadeTargetRate = 1f
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
    // MEZCLA DJ: vuelta al tempo + análisis de BPM
    // =======================================================================

    /** Para el glide de vuelta al tempo (sin tocar la velocidad). */
    private fun stopTempoRecovery() {
        tempoRecoveryJob?.cancel()
        tempoRecoveryJob = null
    }

    /** Deja la velocidad en la que el usuario tiene puesta. */
    private fun resetSpeedToUser() {
        val main = mediaSession?.player ?: return
        if (main.playbackParameters.speed != userSpeed) {
            main.setPlaybackSpeed(userSpeed)
        }
    }

    /**
     * Vuelta al tempo: la velocidad vuelve sola de [fromRate] a [toRate]
     * en ~20 s. Se congela en pausa (solo avanza lo que suena) y se
     * cancela al saltar, cambiar de canción o tocar la velocidad a mano.
     */
    private fun startTempoRecovery(fromRate: Float, toRate: Float) {
        tempoRecoveryJob?.cancel()
        val totalMs = 20_000L
        tempoRecoveryJob = serviceScope.launch {
            val songId = mediaSession?.player?.currentMediaItem?.mediaId
                ?: return@launch
            var playedMs = 0L
            while (playedMs < totalMs) {
                val main = mediaSession?.player ?: break
                if (main.currentMediaItem?.mediaId != songId) break
                if (main.playbackState == Player.STATE_IDLE ||
                    main.playbackState == Player.STATE_ENDED
                ) break
                if (main.isPlaying) {
                    // Pasos de 500 ms, NO de 100 ms: cada setPlaybackSpeed
                    // hace que Sonic reajuste el estiramiento de tiempo
                    // en pleno stream y eso se oye como tirones. Con 40
                    // pasos en 20 s cada salto es ~0,2% de tono
                    // (inaudible como salto) y los tirones desaparecen.
                    playedMs += 500
                    val t = (playedMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val rate = fromRate + (toRate - fromRate) * t
                    lastGlideSpeed = rate
                    main.setPlaybackSpeed(rate)
                    delay(500)
                } else {
                    delay(500)
                }
            }
            // Cierre: velocidad exacta del usuario (sin decimales a
            // medias). Solo si el glide terminó y sigue la misma canción.
            if (playedMs >= totalMs) {
                val main = mediaSession?.player
                if (main != null && main.currentMediaItem?.mediaId == songId &&
                    main.playbackParameters.speed != toRate
                ) {
                    lastGlideSpeed = toRate
                    main.setPlaybackSpeed(toRate)
                }
            }
            tempoRecoveryJob = null
        }
    }

    /**
     * Análisis de BPM en segundo plano: al cambiar de canción se
     * analiza la actual y las 3 siguientes de la cola (las que van a
     * necesitar el BPM), una por una y en orden. Las ya analizadas no
     * se repiten. No depende de que la Mezcla DJ esté activada: así la
     * biblioteca se cubre sola mientras se escucha normal.
     */
    private fun startBpmAnalysis() {
        val app = application as? AuralApp ?: return
        serviceScope.launch {
            app.playback.state.collect { playerState ->
                val songId = playerState.currentSong?.id ?: return@collect
                if (songId == lastAnalyzedSongId) return@collect
                lastAnalyzedSongId = songId
                triggerBpmAnalysis(songId)
                if (endlessDjSession) {
                    endlessRecentlyPlayed.addLast(songId)
                    while (endlessRecentlyPlayed.size > ENDLESS_RECENT_CAP) {
                        endlessRecentlyPlayed.removeFirst()
                    }
                    refillEndlessQueueIfNeeded()
                    reorderDjTail()
                }
            }
        }
    }

    private fun triggerBpmAnalysis(currentSongId: Long) {
        val player = mediaSession?.player ?: return
        val app = application as? AuralApp ?: return
        // Al abrir, la cola restaurada se está aplicando (setMediaItems)
        // mientras el estado ya se publicó: currentMediaItemIndex puede
        // ser >= 0 con el timeline todavía vacío, y getMediaItemAt
        // revienta con IndexOutOfBoundsException (crash al abrir).
        // Se lee el timeline UNA vez y se camina solo sobre esa foto,
        // con los índices acotados a ella; si la foto no cuadra con la
        // canción que disparó el análisis, se sale y el próximo cambio
        // lo reintenta. El análisis nunca debe tumbar la app.
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= timeline.windowCount) return
        val window = Timeline.Window()
        val currentItem = timeline.getWindow(currentIndex, window).mediaItem
        if (currentItem.mediaId.toLongOrNull() != currentSongId) return
        val wanted = ArrayList<Pair<Long, android.net.Uri>>()
        val currentUri = currentItem.localConfiguration?.uri
        if (currentUri != null) wanted += currentSongId to currentUri
        var idx = currentIndex
        var steps = 0
        while (steps < 3) {
            val next = nextWindowIndexFrom(timeline, player, idx) ?: break
            if (next < 0 || next >= timeline.windowCount) break
            val item = timeline.getWindow(next, window).mediaItem
            val id = item.mediaId.toLongOrNull()
            val uri = item.localConfiguration?.uri
            if (id != null && uri != null) wanted += id to uri
            idx = next
            steps++
        }
        if (wanted.isEmpty()) return
        analysisJob?.cancel()
        analysisJob = serviceScope.launch(Dispatchers.IO) {
            for ((id, uri) in wanted) {
                if (id in analyzedIds || id in analyzingIds) continue
                if (bpmCache.containsKey(id)) {
                    analyzedIds += id
                    continue
                }
                // El análisis NO corre mientras el glide está activo:
                // decodificar 75 s + analizar (autocorrelación) a la vez
                // que Sonic estira el tiempo satura la CPU y se oye el
                // tirón al volver al tempo. El glide dura 20 s como
                // mucho y el BPM no corre prisa (la siguiente transición
                // está a minutos). La espera va ANTES de marcar el id
                // como analizando: si cancelan el trabajo esperando,
                // la canción se reintenta en el próximo cambio.
                while (tempoRecoveryJob?.isActive == true) delay(500)
                analyzingIds += id
                try {
                    val bpm = BpmAnalysis.analyzeBpm(this@MusicService, uri)
                    if (bpm != null) {
                        app.settings.setBpm(id, bpm)
                    }
                    analyzedIds += id
                } catch (_: Exception) {
                    // El análisis nunca debe romper la reproducción
                } finally {
                    analyzingIds -= id
                }
            }
        }
    }

    // =======================================================================
    // SESIÓN DJ INFINITA: la cola no se acaba nunca. Arranca con toda la
    // biblioteca mezclada; cada BPM nuevo reordena la cola que viene por
    // tempo (la más cercana primero, doble/mitad como cercanos) y cuando
    // quedan pocas canciones se rellena sola sin repetir las recientes.
    // Nunca se mueve la que suena ni la siguiente (el fundido la tiene
    // preparada) y no se reordena en mitad de un fundido ni con el
    // aleatorio o la repetición activados (entonces manda el usuario).
    // =======================================================================

    /**
     * Arranca el MOTOR de la sesión infinita: construye el pool con toda
     * la biblioteca (para rellenar) y activa el reorden por tempo. La
     * COLA ya la puso el cliente (PlaybackManager.playDjQueue): el
     * servicio nunca toca los media items al arrancar, solo reordena y
     * rellena lo que suena. Si el flag se apagó mientras se escaneaba,
     * no se arranca nada.
     */
    private fun startEndlessEngine() {
        serviceScope.launch {
            val app = application as? AuralApp ?: return@launch
            val songs = withContext(Dispatchers.IO) {
                MusicRepository(this@MusicService).scanSongs()
            }
            if (songs.isEmpty()) return@launch
            if (!app.playback.endlessDjActive.value) return@launch
            endlessPool = songs.map { song ->
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
            endlessRecentlyPlayed.clear()
            endlessDjSession = true
        }
    }

    /** Reordena la cola que viene (desde la tercera en adelante) por tempo. */
    private fun reorderDjTail() {
        if (!endlessDjSession || !djMixEnabledCache) return
        if (crossfadeActive) return
        val player = mediaSession?.player ?: return
        if (player.shuffleModeEnabled || player.repeatMode != Player.REPEAT_MODE_OFF) return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= timeline.windowCount) return
        // Ancla: el BPM de la siguiente (la que va a mezclar). Si aún no
        // tiene BPM, el de la que suena. Sin ancla no se puede ordenar.
        val window = Timeline.Window()
        val nextIndex = nextWindowIndexFrom(timeline, player, currentIndex) ?: return
        val nextId = timeline.getWindow(nextIndex, window).mediaItem.mediaId.toLongOrNull()
        val currentId = timeline.getWindow(currentIndex, window).mediaItem.mediaId.toLongOrNull()
        val anchor = (nextId?.let { bpmCache[it] } ?: currentId?.let { bpmCache[it] })
            ?.let { BpmAnalysis.foldBpm(it) } ?: return
        // Cola que viene: desde la tercera en adelante. La siguiente no
        // se toca: el fundido ya la tiene preparada.
        val tailStart = currentIndex + 2
        if (tailStart >= timeline.windowCount) return
        val tailIds = ArrayList<Long>(timeline.windowCount - tailStart)
        for (i in tailStart until timeline.windowCount) {
            timeline.getWindow(i, window).mediaItem.mediaId.toLongOrNull()?.let { tailIds += it }
        }
        if (tailIds.size < 2) return
        val ordered = orderByTempo(tailIds, anchor)
        if (ordered == tailIds) return
        // Aplicar el orden con moveMediaItem, espejando cada movimiento
        // en la lista local para no perder la cuenta de los índices.
        val mirror = tailIds.toMutableList()
        for (pos in ordered.indices) {
            val id = ordered[pos]
            val from = mirror.indexOf(id)
            if (from < 0 || from == pos) continue
            player.moveMediaItem(tailStart + from, tailStart + pos)
            mirror.removeAt(from)
            mirror.add(pos, id)
        }
    }

    /** Cadena por tempo: cada id sigue al más cercano en BPM (doble/mitad
     *  como cercanos). Los sin BPM van al final, en su orden actual: ya
     *  se ordenarán cuando se analicen. */
    /** Cadena de tempo: la lógica pura vive en BpmAnalysis (la usa también el ViewModel). */
    private fun orderByTempo(ids: List<Long>, anchorBpm: Float): List<Long> =
        BpmAnalysis.orderByTempo(ids, bpmCache, anchorBpm)

    /** Si quedan pocas canciones en la cola, la rellena con canciones del
     *  pool que no estén en la cola ni hayan sonado hace poco. Si no queda
     *  ninguna elegible, se olvida el historial reciente (salvo la que
     *  suena y la siguiente) y se reintenta: la sesión no se acaba nunca. */
    private fun refillEndlessQueueIfNeeded() {
        if (!endlessDjSession) return
        val player = mediaSession?.player ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= timeline.windowCount) return
        if (timeline.windowCount - currentIndex - 1 > ENDLESS_REFILL_THRESHOLD) return
        val window = Timeline.Window()
        val inQueue = HashSet<Long>()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window).mediaItem.mediaId.toLongOrNull()?.let { inQueue += it }
        }
        val keepRecent = HashSet<Long>()
        timeline.getWindow(currentIndex, window).mediaItem.mediaId.toLongOrNull()?.let { keepRecent += it }
        nextWindowIndexFrom(timeline, player, currentIndex)?.let { ni ->
            timeline.getWindow(ni, window).mediaItem.mediaId.toLongOrNull()?.let { keepRecent += it }
        }
        var candidates = endlessPool
            .mapNotNull { it.mediaId.toLongOrNull() }
            .filter { it !in inQueue && it !in endlessRecentlyPlayed }
        if (candidates.isEmpty()) {
            endlessRecentlyPlayed.removeAll { it !in keepRecent }
            candidates = endlessPool
                .mapNotNull { it.mediaId.toLongOrNull() }
                .filter { it !in inQueue && it !in endlessRecentlyPlayed }
            if (candidates.isEmpty()) return
        }
        val wanted = candidates.toSet()
        player.addMediaItems(endlessPool.filter { it.mediaId.toLongOrNull() in wanted })
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
        tempoRecoveryJob?.cancel()
        analysisJob?.cancel()
        fadeWatchJob = null
        rampJob = null
        tempoRecoveryJob = null
        analysisJob = null
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
