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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
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
import com.aural.player.youtube.YouTubeStreamResolver
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
import java.io.File
import java.io.IOException
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
    /** Calidad de streaming en caché (true = 128 Kbps). */
    @Volatile
    private var streamingQualityCache = false

    /** Caché de disco del streaming (una sola instancia por proceso). */
    @Volatile
    private var streamCache: SimpleCache? = null
    private val streamCacheLock = Any()

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

    /**
     * Fábrica de fuentes con resolución de streaming: las pistas
     * online viajan como `aural-online://youtube/<videoId>` y la URL
     * real se resuelve aquí, en el hilo de carga, al reproducir.
     * Lo local pasa intacto por el DataSource por defecto.
     */
    /**
     * Buffer con tope de memoria por reproductor: el principal
     * carga la canción online de forma continua hasta tenerla
     * completa (ventana amplia de 900 s), acotado por bytes
     * (16 MB) para no disparar la RAM en teléfonos modestos;
     * el de fundido solo necesita el colchón de la siguiente
     * (máx 30 s, tope 4 MB).
     */
    private fun createLoadControl(fadePlayer: Boolean = false): DefaultLoadControl =
        if (fadePlayer) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    30_000,
                    4_000,
                    8_000
                )
                .setTargetBufferBytes(4 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    60_000,
                    900_000,
                    // Arranque casi instantáneo: con la URL ya
                    // pre-resuelta, 1.8 s de colchón bastan para
                    // sonar; la carga completa sigue detrás y el
                    // rebuffer conserva su colchón de 8 s.
                    1_800,
                    8_000
                )
                .setTargetBufferBytes(16 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        }

    /**
     * Caché de disco del streaming (256 MB, disco no RAM). Se
     * calienta en un hilo de fondo al arrancar el servicio
     * para que su creación (IO de disco) no corra en el hilo
     * principal; el doble chequeo hace seguro el primer uso
     * desde cualquier hilo.
     */
    private fun getStreamCache(): SimpleCache {
        streamCache?.let { return it }
        return synchronized(streamCacheLock) {
            streamCache ?: SimpleCache(
                File(cacheDir, "aural-stream-cache"),
                LeastRecentlyUsedCacheEvictor(256L * 1024L * 1024L),
                StandaloneDatabaseProvider(this)
            ).also { streamCache = it }
        }
    }

    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                if (uri.scheme != "aural-online") return dataSpec
                val videoId = uri.lastPathSegment
                    ?: throw IOException("Streaming sin video")
                val resolved = YouTubeStreamResolver.resolveStream(
                    videoId,
                    streamingQualityCache
                ) ?: throw IOException("No se pudo resolver el streaming")
                return dataSpec.buildUpon()
                    .setUri(android.net.Uri.parse(resolved.url))
                    .setKey(resolved.cacheKey)
                    .setHttpRequestHeaders(resolved.requestHeaders)
                    .build()
            }
        }
        // Misma petición HTTP que la descarga (headers de navegador)
        // y el mismo cliente OkHttp compartido del resolver, en
        // HTTP/1.1: cada rango paralelo de 512 KB usa una
        // conexión real y las velocidades se suman con VPN.
        val httpFactory = OkHttpDataSource.Factory(
            YouTubeStreamResolver.sharedHttpClient
        )
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        val networkFactory = ChunkedRangeDataSourceFactory(
            DefaultDataSource.Factory(this, httpFactory)
        ) { failedSpec ->
            // 403/410 o corte: URL fresca de StreamInfo, sin la que falló.
            val videoId = failedSpec.key
                ?.split(":")
                ?.takeIf { it.size >= 2 && it[0] == "yt" }
                ?.get(1)
                ?: return@ChunkedRangeDataSourceFactory null
            val refreshed = YouTubeStreamResolver.refreshStream(
                videoId,
                streamingQualityCache,
                failedSpec.uri.toString(),
                failedSpec.key
            ) ?: return@ChunkedRangeDataSourceFactory null
            failedSpec.buildUpon()
                .setUri(android.net.Uri.parse(refreshed.url))
                .setKey(refreshed.cacheKey)
                .setHttpRequestHeaders(refreshed.requestHeaders)
                .build()
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(getStreamCache())
            .setUpstreamDataSourceFactory(networkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = ResolvingDataSource.Factory(
            OnlineAwareDataSourceFactory(cacheFactory, DefaultDataSource.Factory(this)),
            resolver
        )
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    private fun onlineVideoId(item: MediaItem?): String? {
        val uri = item?.localConfiguration?.uri ?: return null
        return if (uri.scheme == "aural-online") uri.lastPathSegment else null
    }

    /** Mide la velocidad real de carga del stream online para
     *  el indicador de conexión del Now Playing: cada carga
     *  completada de googlevideo aporta bytes/tiempo al
     *  medidor del resolver (sin gastar datos extra). */
    private val connectionAnalyticsListener = object : AnalyticsListener {
        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            // La carga viaja con la URI interna (la resolución
            // ocurre dentro de open), así que el video sale de
            // la URI; la clave de caché es el respaldo.
            val uri = loadEventInfo.dataSpec.uri
            val videoId = if (uri.scheme == "aural-online") {
                uri.lastPathSegment
            } else {
                val parts = loadEventInfo.dataSpec.key?.split(":")
                if (parts != null && parts.size >= 2 && parts[0] == "yt") parts[1] else null
            } ?: return
            YouTubeStreamResolver.noteConnectionLoad(
                videoId, loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs
            )
            checkAbr(videoId)
        }
    }

    // Regla ABR estilo YouTube: si el throughput sostenido cae
    // por debajo de ~1,3× el bitrate vigente Y el colchón se
    // está encogiendo, tras 3 chequeos bajos seguidos se marca
    // la bajada a 48. Tendencia + salud del colchón, nunca un
    // solo rango malo.
    private var abrVideoId: String? = null
    private var abrLastBufferedAheadMs: Long = -1L
    private var abrLowChecks: Int = 0

    private fun checkAbr(videoId: String) {
        val player = mediaSession?.player ?: return
        if (onlineVideoId(player.currentMediaItem) != videoId) return
        val bufferedAhead = player.bufferedPosition - player.currentPosition
        if (abrVideoId != videoId) {
            abrVideoId = videoId
            abrLowChecks = 0
            abrLastBufferedAheadMs = bufferedAhead
            return
        }
        val bitrate = YouTubeStreamResolver.streamBitrateKbps(videoId)
        val kbps = YouTubeStreamResolver.connectionKbps(videoId)
        val low = bitrate != null && bitrate > 48 &&
            kbps != null && kbps < bitrate * 1.3 &&
            bufferedAhead < abrLastBufferedAheadMs &&
            bufferedAhead < 45_000L
        abrLastBufferedAheadMs = bufferedAhead
        if (low) {
            abrLowChecks++
            if (abrLowChecks >= 3) {
                YouTubeStreamResolver.noteRebuffer()
                abrLowChecks = 0
            }
        } else {
            abrLowChecks = 0
        }
    }

    private fun prefetchOnlineAround(player: Player) {
        // Actual + las tres siguientes de la cola (respetando
        // aleatorio y repetición): resolver solo la siguiente
        // dejaba la tercera en frío si el usuario saltaba.
        val seen = mutableSetOf<String>()
        fun prefetchItem(item: MediaItem?) {
            onlineVideoId(item)?.let { videoId ->
                if (seen.add(videoId)) {
                    YouTubeStreamResolver.prefetch(
                        videoId, streamingQualityCache
                    )
                }
            }
        }
        prefetchItem(player.currentMediaItem)
        val timeline = player.currentTimeline
        if (timeline.windowCount == 0) return
        var index = player.currentMediaItemIndex
        repeat(3) {
            index = timeline.getNextWindowIndex(
                index, player.repeatMode, player.shuffleModeEnabled
            )
            if (index == C.INDEX_UNSET ||
                index == player.currentMediaItemIndex ||
                index !in 0 until player.mediaItemCount
            ) {
                return
            }
            prefetchItem(player.getMediaItemAt(index))
        }
    }

    override fun onCreate() {
        super.onCreate()

        // La caché de streaming nace en fondo: su IO no debe
        // correr en el hilo principal al construir los players.
        serviceScope.launch(Dispatchers.IO) { getStreamCache() }

        // Configuración del reproductor ExoPlayer
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true // gestiona automáticamente el foco de audio
            )
            .setMediaSourceFactory(createMediaSourceFactory())
            .setLoadControl(createLoadControl())
            .setWakeMode(C.WAKE_MODE_NETWORK) // streaming 1.3.0: CPU + Wi-Fi despiertos con pantalla apagada
            // v1.0.9: al desconectar los audífonos (cable o Bluetooth) la música
            // se pausa sola (Android avisa con AUDIO_BECOMING_NOISY).
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addAnalyticsListener(connectionAnalyticsListener)

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
            .setMediaSourceFactory(createMediaSourceFactory())
            .setLoadControl(createLoadControl(fadePlayer = true))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(false)
            .build()
        fade.addAnalyticsListener(connectionAnalyticsListener)
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
        serviceScope.launch {
            app.settings.streamingQuality.collect { streamingQualityCache = it == "quality" }
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
            if (owner !== mediaSession?.player) return
            if (playbackState == Player.STATE_BUFFERING &&
                owner.playWhenReady &&
                owner.currentPosition > 3_000L &&
                onlineVideoId(owner.currentMediaItem) != null
            ) {
                YouTubeStreamResolver.noteRebuffer()
            }
            if (!crossfadeActive) return
            // La principal terminó en pleno fundido: cerrar ya
            // para que no avance sola y choque con la que entra.
            if (playbackState == Player.STATE_ENDED) {
                finishFade()
                return
            }
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
            prefetchOnlineAround(owner)
            if (crossfadeActive) return
            // La canción cambió sola sin fundido (o el usuario saltó
            // directo): se acabó el glide y la nueva suena a la
            // velocidad del usuario, no a la que dejó el glide a medias.
            stopTempoRecovery()
            resetSpeedToUser()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (owner !== mediaSession?.player) return
            prefetchOnlineAround(owner)
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
        // Con playWhenReady basta: si la principal entra en
        // BUFFERING justo al final, el fundido igual debe
        // poder arrancar con el colchón de la siguiente.
        if (!main.playWhenReady || crossfadeActive) return
        if (main.playbackState == Player.STATE_IDLE ||
            main.playbackState == Player.STATE_ENDED
        ) return
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
        // Preparar la siguiente con margen amplio: una canción
        // online tarda en resolver y cargar su colchón, y con
        // 5 s el fundido arrancaba en seco. Si la cola cambió,
        // el índice ya no vale.
        if (fadePreparedForIndex != nextIndex) {
            if (remaining <= fadeMs + 20_000) prepareFadePlayer(nextIndex)
            return
        }
        if (remaining <= fadeMs + 250) {
            // Solo fundir con colchón real ya cargado en la
            // siguiente; si no llegó, esperar al próximo tick
            // (y si la canción termina antes, la transición
            // normal la hace sonar sin fundido).
            val fade = fadePlayer ?: return
            val cushionNeeded = fadeMs + 2_000
            val fadeDuration = fade.duration
            val needed = if (fadeDuration > 0) minOf(cushionNeeded, fadeDuration)
            else cushionNeeded
            if (fade.bufferedPosition >= needed) startFade(nextIndex)
        }
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
            if (match != null) {
                setDjRate(fade, fadeTargetRate)
            } else {
                fade.setPlaybackParameters(main.playbackParameters)
            }
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
                } else if (main.playbackParameters.speed != fade.playbackParameters.speed ||
                    main.playbackParameters.pitch != fade.playbackParameters.pitch
                ) {
                    // Sin mezcla, la segunda voz sigue a la principal si
                    // el usuario cambia la velocidad en pleno fundido.
                    // Con mezcla no: su velocidad es el tempo igualado.
                    fade.setPlaybackParameters(main.playbackParameters)
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
        // velocidad del usuario en ~30 s (si no era ya esa).
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

    /**
     * Velocidad DJ en modo varispeed: el tono sigue al ajuste de tempo
     * (pitch = rate / userSpeed), como un pitch fader. Así el ajuste
     * DJ no pasa por el estiramiento de tiempo de Sonic, que era la
     * fuente de los artifacts del glide (issue #3). Al volver a
     * [userSpeed], el pitch queda en 1 (tono normal).
     */
    private fun setDjRate(player: Player, rate: Float) {
        val pitch = if (userSpeed > 0f) rate / userSpeed else 1f
        val target = androidx.media3.common.PlaybackParameters(rate, pitch)
        if (!samePlaybackParameters(player.playbackParameters, target)) {
            player.setPlaybackParameters(target)
        }
    }

    /** True si dos parámetros de reproducción son el mismo a
     *  efectos prácticos: reaplicar los mismos reinicia el
     *  procesado de audio y se oye un bajón de calidad de
     *  medio segundo (p. ej. tras buscar con el slider). */
    private fun samePlaybackParameters(
        a: androidx.media3.common.PlaybackParameters,
        b: androidx.media3.common.PlaybackParameters
    ): Boolean = abs(a.speed - b.speed) < 0.001f &&
        abs(a.pitch - b.pitch) < 0.001f

    /** Deja la velocidad en la que el usuario tiene puesta,
     *  solo si de verdad difiere: en el seek normal ya es la
     *  suya y reaplicarla degradaba el audio medio segundo. */
    private fun resetSpeedToUser() {
        val main = mediaSession?.player ?: return
        val target = androidx.media3.common.PlaybackParameters(userSpeed, 1f)
        if (!samePlaybackParameters(main.playbackParameters, target)) {
            main.setPlaybackParameters(target)
        }
    }

    /**
     * Vuelta al tempo: la velocidad vuelve sola de [fromRate] a [toRate]
     * en ~30 s. Se congela en pausa (solo avanza lo que suena) y se
     * cancela al saltar, cambiar de canción o tocar la velocidad a mano.
     */
    private fun startTempoRecovery(fromRate: Float, toRate: Float) {
        tempoRecoveryJob?.cancel()
        val totalMs = 30_000L
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
                    // Pasos de 200 ms en modo varispeed (setDjRate): el
                    // tono sigue al tempo y no hay estiramiento Sonic
                    // que reajustar, así que los pasos pueden ser finos
                    // sin producir los artifacts del glide anterior.
                    playedMs += 200
                    val t = (playedMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    // Curva ease-in-out (coseno) en vez de rampa lineal
                    // (issue #3): el cambio de velocidad es más lento al
                    // principio y al final del glide — donde el oído caza
                    // los pasos — y más rápido en el centro.
                    val eased = (1f - kotlin.math.cos(t * kotlin.math.PI.toFloat())) / 2f
                    val rate = fromRate + (toRate - fromRate) * eased
                    lastGlideSpeed = rate
                    setDjRate(main, rate)
                    delay(200)
                } else {
                    delay(200)
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
                    setDjRate(main, toRate)
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
        // Las online no se analizan: su URI es aural-online:// y el
        // BPM saldría de decodificar el stream (gasto de datos/CPU).
        if (currentUri != null && currentUri.scheme != "aural-online") {
            wanted += currentSongId to currentUri
        }
        var idx = currentIndex
        var steps = 0
        while (steps < 3) {
            val next = nextWindowIndexFrom(timeline, player, idx) ?: break
            if (next < 0 || next >= timeline.windowCount) break
            val item = timeline.getWindow(next, window).mediaItem
            val id = item.mediaId.toLongOrNull()
            val uri = item.localConfiguration?.uri
            if (id != null && uri != null && uri.scheme != "aural-online") wanted += id to uri
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
                // tirón al volver al tempo. El glide dura 30 s como
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
        // Durante el glide de vuelta al tempo no se toca la cola
        // (issue #3): los moveMediaItem compiten con el reproductor
        // justo cuando Sonic está estirando el tiempo. El reorden se
        // dispara otra vez con el próximo BPM o cambio de canción.
        if (tempoRecoveryJob?.isActive == true) return
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
        // Igual que el reorden: durante el glide no se toca la cola
        // (issue #3); el relleno se reintenta en la próxima transición.
        if (tempoRecoveryJob?.isActive == true) return
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
        runCatching { streamCache?.release() }
        streamCache = null
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

/**
 * Solo las pistas online resueltas pasan por la caché de streaming;
 * lo local sigue directo por el DataSource por defecto.
 */
private class OnlineAwareDataSourceFactory(
    private val cacheFactory: DataSource.Factory,
    private val defaultFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        OnlineAwareDataSource(cacheFactory, defaultFactory)
}

private class OnlineAwareDataSource(
    private val cacheFactory: DataSource.Factory,
    private val defaultFactory: DataSource.Factory
) : DataSource {
    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val useCache = dataSpec.key?.startsWith("yt:") == true ||
            dataSpec.uri.host?.contains("googlevideo.com") == true
        val created = if (useCache) cacheFactory.createDataSource()
        else defaultFactory.createDataSource()
        listeners.forEach { created.addTransferListener(it) }
        delegate = created
        return created.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): android.net.Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }
}

/**
 * Lee googlevideo en rangos acotados de 512 KB fijos: las
 * peticiones abiertas largas reciben 403 a mitad de canción.
 * El rango actual suena en streaming para el arranque de
 * 1.8 s y los siguientes se bajan en paralelo hacia la
 * caché por delante del playhead. Con VPN googlevideo puede
 * limitar cada conexión: varias conexiones HTTP/1.1 en
 * paralelo suman velocidad. La ventana base es de 3 rangos
 * y crece hasta 5 si el throughput por rango sale bajo.
 */
private class ChunkedRangeDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val urlRefresher: (DataSpec) -> DataSpec?
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ChunkedRangeDataSource(upstreamFactory, urlRefresher)
}

private class ChunkedRangeDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val urlRefresher: (DataSpec) -> DataSpec?
) : DataSource {

    private data class OpenChunk(val dataSource: DataSource, val length: Long)

    /** Rango bajado en paralelo: bytes completos del rango
     *  y cuánto tardó la descarga (para crecer la ventana
     *  si el throughput por rango sale bajo). */
    private data class ChunkFetch(val bytes: ByteArray, val durationMs: Long)

    private data class BufferedChunk(
        val start: Long,
        val future: java.util.concurrent.Future<ChunkFetch>
    )

    private val listeners = mutableListOf<TransferListener>()
    private val activeFetchSources = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<DataSource, Boolean>()
    )

    private var spec: DataSpec? = null
    private var chunked = false
    /** Respaldo progresivo: la URL solo acepta la lectura
     *  secuencial abierta tipo descarga; se lee en una sola
     *  conexión abierta y la caché se llena mientras suena. */
    private var progressive = false
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var endPosition = C.LENGTH_UNSET.toLong()
    private var chunkBytesRead = 0L
    private var chunkSize = CHUNK_BYTES
    private var current: DataSource? = null
    /** Rango servido desde memoria (ya bajado en paralelo). */
    private var servingBuffer: ByteArray? = null
    private var servingOffset = 0
    private var generation = 0
    private var specGeneration = 0
    private var refreshAttempts = 0

    /** Pool de rangos paralelos: se crea en open() y se
     *  apaga en close() (una instancia por carga). */
    private var parallelPool: java.util.concurrent.ExecutorService? = null
    /** Ventana paralela vigente: 3 rangos futuros en red
     *  sana; crece hasta 5 cuando el throughput por rango
     *  indica límite por conexión. Guardada con
     *  synchronized(this). */
    private var parallelWindow = BASE_PARALLEL_WINDOW
    /** Rangos futuros programados, en orden de posición.
     *  Guardada con synchronized(this). */
    private val window = java.util.ArrayDeque<BufferedChunk>()
    /** Próxima posición a programar en la ventana. */
    private var nextFetchPosition = C.LENGTH_UNSET.toLong()

    /** Medición de internet durante la lectura: bytes de red
     *  por ventana (~1 s o >=256 KB) para el indicador del
     *  Now Playing, sin esperar a que una carga «complete».
     *  Se anota también desde los hilos paralelos: candado
     *  propio. */
    private val connectionLock = Any()
    @Volatile
    private var connectionVideoId: String? = null
    private var connectionWindowBytes = 0L
    private var connectionWindowStartNs = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        current?.addTransferListener(transferListener)
        activeFetchSources.forEach { it.addTransferListener(transferListener) }
    }

    override fun open(dataSpec: DataSpec): Long {
        closeCurrent()
        generation++
        specGeneration++
        spec = dataSpec
        chunked = dataSpec.uri.host?.contains("googlevideo.com") == true
        progressive = chunked && dataSpec.key?.endsWith(":prog") == true
        position = dataSpec.position
        remaining = dataSpec.length
        endPosition = if (remaining != C.LENGTH_UNSET.toLong()) {
            position + remaining
        } else {
            C.LENGTH_UNSET.toLong()
        }
        chunkBytesRead = 0L
        refreshAttempts = 0
        // Ventana caliente: si el medidor ya sabe que la red
        // es lenta, la fase fría arranca con 5 rangos en vez
        // de esperar al primer rango lento para crecer.
        parallelWindow =
            if (YouTubeStreamResolver.networkSlowHint()) MAX_PARALLEL_WINDOW
            else BASE_PARALLEL_WINDOW
        nextFetchPosition = C.LENGTH_UNSET.toLong()
        connectionVideoId = videoIdFromKey(dataSpec.key)
        synchronized(connectionLock) {
            connectionWindowBytes = 0L
            connectionWindowStartNs = 0L
        }
        ensureParallelPool()
        val opened = openChunkWithRefresh(position, remaining)
        current = opened.dataSource
        if (remaining == C.LENGTH_UNSET.toLong() && opened.length != C.LENGTH_UNSET.toLong()) {
            remaining = opened.length
            endPosition = position + remaining
        }
        chunkSize = chunkSizeFor(remaining)
        resetNextFetchPosition()
        scheduleParallelRanges()
        return if (remaining != C.LENGTH_UNSET.toLong()) remaining else opened.length
    }

    private fun chunkSizeFor(remainingAtChunk: Long): Long =
        if (progressive) {
            Long.MAX_VALUE
        } else if (chunked && remainingAtChunk != C.LENGTH_UNSET.toLong()) {
            minOf(CHUNK_BYTES, remainingAtChunk)
        } else {
            CHUNK_BYTES
        }

    private fun videoIdFromKey(key: String?): String? {
        val parts = key?.split(":") ?: return null
        return if (parts.size >= 2 && parts[0] == "yt") parts[1] else null
    }

    /** Anota bytes leídos de red y reporta la ventana al
     *  medidor cuando llega a ~1 s o a 256 KB. */
    private fun noteNetworkBytes(read: Int) {
        if (!chunked || read <= 0) return
        val videoId = connectionVideoId ?: return
        var reportBytes = 0L
        var reportMs = 0L
        synchronized(connectionLock) {
            val now = System.nanoTime()
            if (connectionWindowStartNs == 0L) connectionWindowStartNs = now
            connectionWindowBytes += read
            val elapsedMs = (now - connectionWindowStartNs) / 1_000_000L
            if (connectionWindowBytes >= 256L * 1024L || elapsedMs >= 1_000L) {
                reportBytes = connectionWindowBytes
                reportMs = elapsedMs.coerceAtLeast(50L)
                connectionWindowBytes = 0L
                connectionWindowStartNs = now
            }
        }
        if (reportBytes > 0L) {
            YouTubeStreamResolver.noteConnectionWindow(videoId, reportBytes, reportMs)
        }
    }

    private fun flushConnectionWindow() {
        val videoId = connectionVideoId ?: return
        var reportBytes = 0L
        var reportMs = 0L
        synchronized(connectionLock) {
            if (connectionWindowBytes < 16L * 1024L || connectionWindowStartNs == 0L) return
            reportBytes = connectionWindowBytes
            reportMs = ((System.nanoTime() - connectionWindowStartNs) / 1_000_000L)
                .coerceAtLeast(50L)
            connectionWindowBytes = 0L
            connectionWindowStartNs = 0L
        }
        YouTubeStreamResolver.noteConnectionWindow(videoId, reportBytes, reportMs)
    }

    private fun openChunkAt(chunkPosition: Long, remainingAtChunk: Long): OpenChunk {
        val base = spec ?: throw IOException("DataSource sin abrir")
        // En progresivo se pide abierto (bytes=posición-), como
        // la descarga; en directo, rangos acotados de 512 KB
        // exactos (rangos mayores se sirven un rato y luego se
        // rechazan en clientes sin token).
        val chunkLength = if (chunked && !progressive && remainingAtChunk != C.LENGTH_UNSET.toLong()) {
            minOf(CHUNK_BYTES, remainingAtChunk)
        } else {
            remainingAtChunk
        }
        val chunkSpec = base.buildUpon()
            .setPosition(chunkPosition)
            .setLength(chunkLength)
            .build()
        val dataSource = upstreamFactory.createDataSource()
        listeners.forEach { dataSource.addTransferListener(it) }
        val length = dataSource.open(chunkSpec)
        return OpenChunk(dataSource, length)
    }

    private fun isRefreshable(e: IOException): Boolean =
        e is HttpDataSource.InvalidResponseCodeException &&
            (e.responseCode == 403 || e.responseCode == 410) ||
            e is HttpDataSource.HttpDataSourceException

    /** Sustituye la URL de googlevideo por una fresca (StreamInfo
     *  nuevo, sin la que falló). Máximo 2 refrescos por rango:
     *  el presupuesto se reinicia tras cada avance exitoso. */
    private fun refreshSpec(): Boolean {
        if (!chunked || refreshAttempts >= 2) return false
        val base = spec ?: return false
        val refreshed = try {
            urlRefresher(base)
        } catch (_: Exception) {
            null
        } ?: return false
        refreshAttempts++
        spec = refreshed
        specGeneration++
        connectionVideoId = videoIdFromKey(refreshed.key) ?: connectionVideoId
        progressive = refreshed.key?.endsWith(":prog") == true
        chunkSize = chunkSizeFor(remaining)
        // Los rangos paralelos usan la URL vieja: fuera.
        discardParallelWindow()
        return true
    }

    private fun openChunkWithRefresh(
        chunkPosition: Long,
        remainingAtChunk: Long
    ): OpenChunk {
        while (true) {
            try {
                return openChunkAt(chunkPosition, remainingAtChunk)
            } catch (e: IOException) {
                if (!isRefreshable(e) || !refreshSpec()) throw e
                // Espera corta entre reintentos: un corte de red
                // momentáneo no debe martillar el servidor.
                try {
                    Thread.sleep(if (refreshAttempts <= 1) 500L else 1_000L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }

    /** Watchdog de rangos: nace junto al pool paralelo y se
     *  apaga en closeCurrent. Cierra la fuente de un rango
     *  que pasa ~10 s sin recibir bytes para que su future
     *  falle y advanceChunk caiga a la vía secuencial. */
    private var watchdog: java.util.concurrent.ScheduledExecutorService? = null

    private fun ensureParallelPool() {
        if (!chunked || progressive || parallelPool != null) return
        parallelPool = java.util.concurrent.Executors.newFixedThreadPool(
            MAX_PARALLEL_WINDOW
        ) { runnable ->
            Thread(runnable, "aural-range-parallel").apply { isDaemon = true }
        }
        watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "aural-range-watchdog").apply { isDaemon = true }
        }
    }

    /** Tras abrir el rango actual, el siguiente rango a
     *  programar es el que sigue a este. */
    private fun resetNextFetchPosition() {
        nextFetchPosition = if (
            chunked && !progressive && endPosition != C.LENGTH_UNSET.toLong()
        ) {
            val next = position + chunkSize
            if (next < endPosition) next else C.LENGTH_UNSET.toLong()
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    /** Programa rangos futuros de 512 KB fijos hasta llenar
     *  la ventana vigente. */
    private fun scheduleParallelRanges() {
        if (!chunked || progressive) return
        val baseSpec = spec ?: return
        if (endPosition == C.LENGTH_UNSET.toLong()) return
        if (nextFetchPosition == C.LENGTH_UNSET.toLong()) return
        ensureParallelPool()
        val pool = parallelPool ?: return
        val gen = generation
        val specGen = specGeneration
        synchronized(this) {
            while (
                window.size < parallelWindow &&
                nextFetchPosition != C.LENGTH_UNSET.toLong() &&
                nextFetchPosition < endPosition
            ) {
                val start = nextFetchPosition
                val expected = minOf(CHUNK_BYTES, endPosition - start)
                if (expected <= 0L) {
                    nextFetchPosition = C.LENGTH_UNSET.toLong()
                    break
                }
                nextFetchPosition = if (start + expected < endPosition) {
                    start + expected
                } else {
                    C.LENGTH_UNSET.toLong()
                }
                val future = pool.submit(object : java.util.concurrent.Callable<ChunkFetch> {
                    override fun call(): ChunkFetch =
                        fetchChunk(start, expected, gen, specGen, baseSpec)
                })
                window.addLast(BufferedChunk(start, future))
            }
        }
    }

    /** Baja un rango completo de 512 KB a memoria. Un rango
     *  incompleto se trata como fallo: al avanzar, la vía
     *  secuencial con refresco abre esa posición. */
    private fun fetchChunk(
        start: Long,
        expectedLength: Long,
        gen: Int,
        specGen: Int,
        baseSpec: DataSpec
    ): ChunkFetch {
        val startedNs = System.nanoTime()
        val chunkSpec = baseSpec.buildUpon()
            .setPosition(start)
            .setLength(expectedLength)
            .build()
        val dataSource = upstreamFactory.createDataSource()
        listeners.forEach { dataSource.addTransferListener(it) }
        activeFetchSources.add(dataSource)
        // Watchdog: ~10 s sin bytes cierra la fuente; el read()
        // bloqueado lanza IOException y el future falla.
        val lastByteNs = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
        val watch = watchdog?.scheduleAtFixedRate(
            {
                if (System.nanoTime() - lastByteNs.get() > 10_000_000_000L) {
                    try {
                        dataSource.close()
                    } catch (_: Exception) {
                    }
                }
            },
            1L, 1L, java.util.concurrent.TimeUnit.SECONDS
        )
        try {
            dataSource.open(chunkSpec)
            lastByteNs.set(System.nanoTime())
            val output = java.io.ByteArrayOutputStream(expectedLength.toInt())
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (total < expectedLength) {
                val read = dataSource.read(
                    buffer,
                    0,
                    minOf(buffer.size, (expectedLength - total).toInt())
                )
                if (read == C.RESULT_END_OF_INPUT) break
                if (read > 0) {
                    output.write(buffer, 0, read)
                    total += read
                    lastByteNs.set(System.nanoTime())
                    noteNetworkBytes(read)
                }
            }
            if (total < expectedLength) {
                throw IOException("Rango de streaming incompleto")
            }
            val durationMs = ((System.nanoTime() - startedNs) / 1_000_000L)
                .coerceAtLeast(1L)
            val fetch = ChunkFetch(output.toByteArray(), durationMs)
            onRangeFetched(fetch, gen, specGen)
            return fetch
        } finally {
            watch?.cancel(false)
            activeFetchSources.remove(dataSource)
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Si el rango vino lento (límite por conexión), la
     *  ventana crece hasta 5 para sumar más conexiones. */
    private fun onRangeFetched(fetch: ChunkFetch, gen: Int, specGen: Int) {
        if (gen != generation || specGen != specGeneration) return
        val kbps = fetch.bytes.size.toLong() * 8L / fetch.durationMs
        synchronized(this) {
            parallelWindow = when {
                kbps < 150L -> MAX_PARALLEL_WINDOW
                kbps < 400L -> minOf(MAX_PARALLEL_WINDOW, parallelWindow + 1)
                else -> parallelWindow
            }
        }
        scheduleParallelRanges()
    }

    private fun discardParallelWindow() {
        synchronized(this) {
            window.forEach { it.future.cancel(true) }
            window.clear()
            nextFetchPosition = C.LENGTH_UNSET.toLong()
        }
        closeActiveFetchSources()
    }

    private fun closeActiveFetchSources() {
        activeFetchSources.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Avanza al siguiente rango. Devuelve false SOLO al fin
     *  real del archivo (o 416 pasado el último byte); un fallo
     *  de red/403 se lanza como error para que el reproductor
     *  reintente la misma canción en vez de darla por terminada
     *  y saltar a la siguiente. */
    private fun advanceChunk(): Boolean {
        if (remaining != C.LENGTH_UNSET.toLong() && remaining <= 0L) return false
        current?.close()
        current = null
        servingBuffer = null
        servingOffset = 0
        val buffered = synchronized(this) { window.peekFirst() }
        if (buffered != null && buffered.start == position) {
            val fetch = try {
                // Red de seguridad: ningún rango bloquea la
                // entrega sin fin; al expirar, el mismo fallback
                // secuencial con refresco de siempre.
                buffered.future.get(30L, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                buffered.future.cancel(true)
                null
            } catch (e: Exception) {
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                null
            }
            synchronized(this) {
                if (window.peekFirst() === buffered) window.pollFirst()
            }
            val expected = if (remaining != C.LENGTH_UNSET.toLong()) {
                minOf(CHUNK_BYTES, remaining)
            } else {
                CHUNK_BYTES
            }
            if (fetch != null && fetch.bytes.size.toLong() == expected) {
                servingBuffer = fetch.bytes
                servingOffset = 0
                chunkBytesRead = 0L
                chunkSize = fetch.bytes.size.toLong()
                // Rango nuevo: el presupuesto de refresco
                // vuelve a 2 para este rango.
                refreshAttempts = 0
                scheduleParallelRanges()
                return true
            }
            // El rango paralelo falló o vino incompleto: fuera
            // la ventana y se abre secuencial con el refresco
            // de siempre en la misma posición.
            discardParallelWindow()
        } else if (buffered != null) {
            // Ventana desalineada: no se usa ningún rango suyo.
            discardParallelWindow()
        }
        if (spec == null) throw IOException("DataSource cerrado")
        val opened = try {
            openChunkWithRefresh(position, remaining)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) return false
            throw e
        }
        current = opened.dataSource
        if (remaining == C.LENGTH_UNSET.toLong() && opened.length != C.LENGTH_UNSET.toLong()) {
            remaining = opened.length
            endPosition = position + remaining
        }
        chunkBytesRead = 0L
        chunkSize = chunkSizeFor(remaining)
        // Rango nuevo abierto: el presupuesto de refresco
        // vuelve a 2 para este rango.
        refreshAttempts = 0
        resetNextFetchPosition()
        scheduleParallelRanges()
        return true
    }

    /** Reabre en la misma posición con URL fresca tras un
     *  corte sin error HTTP (END_OF_INPUT prematuro). False
     *  solo ante el fin real (416); sin refresco disponible
     *  lanza error para que el reproductor reintente. */
    private fun reopenAfterRefresh(): Boolean {
        if (!refreshSpec()) {
            throw IOException("Corte de streaming sin refresco disponible")
        }
        current?.close()
        current = null
        servingBuffer = null
        servingOffset = 0
        val opened = try {
            openChunkWithRefresh(position, remaining)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) return false
            throw e
        }
        current = opened.dataSource
        if (remaining == C.LENGTH_UNSET.toLong() && opened.length != C.LENGTH_UNSET.toLong()) {
            remaining = opened.length
            endPosition = position + remaining
        }
        chunkBytesRead = 0L
        chunkSize = chunkSizeFor(remaining)
        resetNextFetchPosition()
        scheduleParallelRanges()
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (!chunked) {
            val active = current ?: return C.RESULT_END_OF_INPUT
            val read = active.read(buffer, offset, length)
            if (read > 0) {
                position += read
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
            }
            return read
        }
        if (progressive) {
            val active = current ?: return C.RESULT_END_OF_INPUT
            val read = try {
                active.read(buffer, offset, length)
            } catch (e: IOException) {
                if (isRefreshable(e) && refreshSpec() && advanceChunk()) {
                    return read(buffer, offset, length)
                }
                throw e
            }
            if (read == C.RESULT_END_OF_INPUT) {
                if (remaining != C.LENGTH_UNSET.toLong() && remaining <= 0L) {
                    return C.RESULT_END_OF_INPUT
                }
                // Corte de la conexión abierta: URL fresca y
                // reabrir en la misma posición (acotado por los
                // refrescos, para no girar en vacío).
                if (refreshSpec() && advanceChunk()) return read(buffer, offset, length)
                throw IOException("Corte de streaming sin refresco disponible")
            }
            if (read > 0) {
                position += read
                chunkBytesRead += read
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
                noteNetworkBytes(read)
            }
            return read
        }
        if (chunkBytesRead >= chunkSize) {
            if (!advanceChunk()) return C.RESULT_END_OF_INPUT
            return read(buffer, offset, length)
        }
        scheduleParallelRanges()
        val serving = servingBuffer
        if (serving != null) {
            val bufferLeft = serving.size - servingOffset
            if (bufferLeft <= 0) {
                if (!advanceChunk()) return C.RESULT_END_OF_INPUT
                return read(buffer, offset, length)
            }
            val toRead = minOf(
                length,
                bufferLeft,
                (chunkSize - chunkBytesRead).toInt()
            )
            System.arraycopy(serving, servingOffset, buffer, offset, toRead)
            servingOffset += toRead
            position += toRead
            chunkBytesRead += toRead
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= toRead
            scheduleParallelRanges()
            return toRead
        }
        val active = current ?: return C.RESULT_END_OF_INPUT
        val chunkLeft = (chunkSize - chunkBytesRead).toInt()
        val read = try {
            active.read(buffer, offset, minOf(length, chunkLeft))
        } catch (e: IOException) {
            // Corte a mitad de rango: URL fresca y seguir en la
            // misma posición; si no se puede, error (nunca fin
            // fingido: eso hacía saltar la canción a mitad).
            if (isRefreshable(e) && refreshSpec() && advanceChunk()) {
                return read(buffer, offset, length)
            }
            throw e
        }
        if (read == C.RESULT_END_OF_INPUT) {
            if (remaining != C.LENGTH_UNSET.toLong() && remaining <= 0L) {
                return C.RESULT_END_OF_INPUT
            }
            if (remaining == C.LENGTH_UNSET.toLong()) {
                return C.RESULT_END_OF_INPUT
            }
            // Corte sin error HTTP con audio por leer: URL
            // fresca y reabrir en la misma posición.
            return if (reopenAfterRefresh()) read(buffer, offset, length)
            else C.RESULT_END_OF_INPUT
        }
        if (read > 0) {
            position += read
            chunkBytesRead += read
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
            noteNetworkBytes(read)
            scheduleParallelRanges()
        }
        return read
    }

    override fun getUri(): android.net.Uri? = current?.uri ?: spec?.uri

    override fun close() {
        flushConnectionWindow()
        generation++
        closeCurrent()
        spec = null
    }

    private fun closeCurrent() {
        current?.close()
        current = null
        servingBuffer = null
        servingOffset = 0
        discardParallelWindow()
        parallelPool?.shutdownNow()
        parallelPool = null
        watchdog?.shutdownNow()
        watchdog = null
    }

    private companion object {
        const val CHUNK_BYTES = 512L * 1024L
        const val BASE_PARALLEL_WINDOW = 3
        const val MAX_PARALLEL_WINDOW = 5
    }
}
