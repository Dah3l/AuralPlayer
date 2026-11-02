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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aural.player.AuralApp
import com.aural.player.MainActivity
import com.aural.player.R
import com.aural.player.ui.island.DynamicIslandCollapsed
import com.aural.player.ui.island.IslandOverlayContent
import com.aural.player.ui.island.DynamicIslandExpanded
import com.aural.player.ui.island.QuickAction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    companion object {
        const val CMD_TOGGLE_FAVORITE = "aural_toggle_favorite"
        const val CMD_CYCLE_REPEAT = "aural_cycle_repeat"
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
            .setWakeMode(C.WAKE_MODE_NETWORK) // permite seguir reproduciendo con pantalla apagada
            .build()

        // Ecualizador y normalización atados a la sesión de audio
        EqualizerManager.init(this, player.audioSessionId)
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
                playerState to favorites
            }.collect { (playerState, favorites) ->
                val song = playerState.currentSong ?: return@collect
                val isFav = favorites.contains(song.id)
                val repeatIcon = when (playerState.repeatMode) {
                    RepeatMode.OFF -> R.drawable.ic_notif_repeat_off
                    RepeatMode.ONE -> R.drawable.ic_notif_repeat_one
                    RepeatMode.ALL -> R.drawable.ic_notif_repeat
                }
                val repeatName = when (playerState.repeatMode) {
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
    // ISLA DINÁMICA (v1.0.7): overlay con WindowManager.
    // Visible solo si: activada en ajustes + hay canción cargada + app en
    // segundo plano. Requiere permiso SYSTEM_ALERT_WINDOW.
    // =======================================================================

    private var islandView: ComposeView? = null
    private var islandParams: WindowManager.LayoutParams? = null
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
                val show = enabled &&
                        playerState.currentSong != null &&
                        !foreground &&
                        Settings.canDrawOverlays(this@MusicService)
                if (show) showIslandOverlay() else hideIslandOverlay()
            }
        }
    }

    private fun showIslandOverlay() {
        if (islandView != null) return
        val app = application as? AuralApp ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24 // debajo de la barra de estado
        }
        val composeView = ComposeView(this).apply {
            // El servicio no es un LifecycleOwner; se usa un ciclo de vida
            // manual mínimo para que Compose funcione en el overlay.
            val islandLifecycle = IslandLifecycleOwner()
            setViewTreeLifecycleOwner(islandLifecycle)
            setViewTreeViewModelStoreOwner(IslandViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(IslandSavedStateOwner(islandLifecycle.lifecycle))
            setContent {
                IslandOverlayContent(
                    app = app,
                    onMove = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (_: Exception) { }
                    },
                    onClose = {
                        // Cerrar la isla = desactivarla en ajustes.
                        serviceScope.launch {
                            app.settings.setIslandEnabled(false)
                        }
                    }
                )
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

    private fun hideIslandOverlay() {
        islandView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) { }
        }
        islandView = null
        islandParams = null
    }

    /** Ciclo de vida mínimo para el ComposeView del overlay. */
    private class IslandLifecycleOwner : androidx.lifecycle.LifecycleOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this).apply {
            handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
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
        serviceScope.cancel()
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
