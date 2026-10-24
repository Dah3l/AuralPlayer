// Servicio de reproducción en segundo plano basado en Media3
package com.aural.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.aural.player.R
import com.aural.player.data.SettingsStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MediaSessionService que mantiene la reproducción activa cuando la app
 * pasa a segundo plano, publica una notificación multimedia y expone los
 * controles a auriculares y dispositivos Bluetooth.
 */
@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings by lazy { SettingsStore(this) }
    private var favoriteIds: Set<Long> = emptySet()

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "aural_toggle_favorite"
        const val ACTION_CYCLE_PLAY_MODE = "aural_cycle_play_mode"
    }

    override fun onCreate() {
        super.onCreate()

        // Configuración del reproductor ExoPlayer
        val player = ExoPlayer.Builder(this)
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
            .setCallback(SessionCallback())
        mediaSession = sessionBuilder.build()

        // Notificación multimedia de Media3 (usa el icono de la app del manifest)
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

        // Refrescar los botones propios cuando cambia la canción o el modo
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshNotificationButtons()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                refreshNotificationButtons()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                refreshNotificationButtons()
            }
        })
        serviceScope.launch {
            settings.favorites.collect { ids ->
                favoriteIds = ids
                refreshNotificationButtons()
            }
        }
        refreshNotificationButtons()
    }

    /** El sistema llama a este método para entregar la sesión a los clientes. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Botones propios de la notificación: modo (repetir/aleatorio) y favorito.
     * Se refrescan con cada cambio de canción, modo o favoritos.
     */
    private fun refreshNotificationButtons() {
        val session = mediaSession ?: return
        val player = session.player
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val isFav = songId != null && favoriteIds.contains(songId)
        val cycleIcon = when {
            player.shuffleModeEnabled -> R.drawable.ic_notif_shuffle
            player.repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
            player.repeatMode == Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat
            else -> R.drawable.ic_notif_repeat_off
        }
        session.setCustomLayout(
            listOf(
                CommandButton.Builder(cycleIcon)
                    .setDisplayName("Modo de reproducción")
                    .setCustomAction(ACTION_CYCLE_PLAY_MODE)
                    .build(),
                CommandButton.Builder(
                    if (isFav) R.drawable.ic_notif_favorite
                    else R.drawable.ic_notif_favorite_border
                )
                    .setDisplayName("Favorito")
                    .setCustomAction(ACTION_TOGGLE_FAVORITE)
                    .build()
            )
        )
    }

    /** Acciones de los botones propios de la notificación. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customAction: String,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    val songId = session.player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (songId != null) {
                        serviceScope.launch { settings.toggleFavorite(songId) }
                    }
                }
                ACTION_CYCLE_PLAY_MODE -> {
                    // Mismo ciclo de 4 estados que el botón de Now Playing
                    val player = session.player
                    when {
                        player.shuffleModeEnabled -> {
                            player.shuffleModeEnabled = false
                            player.repeatMode = Player.REPEAT_MODE_OFF
                        }
                        player.repeatMode == Player.REPEAT_MODE_OFF ->
                            player.repeatMode = Player.REPEAT_MODE_ALL
                        player.repeatMode == Player.REPEAT_MODE_ALL ->
                            player.repeatMode = Player.REPEAT_MODE_ONE
                        else -> {
                            player.repeatMode = Player.REPEAT_MODE_ALL
                            player.shuffleModeEnabled = true
                        }
                    }
                    refreshNotificationButtons()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
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
