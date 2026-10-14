// Servicio de reproducción en segundo plano basado en Media3
package com.aural.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.google.common.collect.ImmutableList
import com.aural.player.R

/**
 * MediaSessionService que mantiene la reproducción activa cuando la app
 * pasa a segundo plano, publica una notificación multimedia y expone los
 * controles a auriculares y dispositivos Bluetooth.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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
        mediaSession = sessionBuilder.build()
        setMediaNotificationProvider(AuralNotificationProvider(this))
    }

    /** El sistema llama a este método para entregar la sesión a los clientes. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Liberar recursos cuando el servicio se destruye. */
    override fun onDestroy() {
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

/**
 * Proveedor de notificación personalizado: usa el icono de la app
 * y muestra carátula, título, artista y controles de reproducción.
 */
@UnstableApi
private class AuralNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aural_playback",
                "Reproducción",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata

        val playPauseAction = if (player.playWhenReady)
            actionFactory.createMediaAction(
                android.R.drawable.ic_media_pause, "Pausar", PlayerNotificationManager.ACTION_PAUSE
            )
        else
            actionFactory.createMediaAction(
                android.R.drawable.ic_media_play, "Reproducir", PlayerNotificationManager.ACTION_PLAY
            )

        val notification = NotificationCompat.Builder(context, "aural_playback")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(metadata.title)
            .setContentText(metadata.artist)
            .setLargeIcon(metadata.artworkData?.let {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            })
            .setContentIntent(mediaSession.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(player.playWhenReady)
            .addAction(
                actionFactory.createMediaAction(
                    android.R.drawable.ic_media_previous, "Anterior",
                    PlayerNotificationManager.ACTION_PREVIOUS
                ).let {
                    NotificationCompat.Action(
                        it.iconCompat, it.title, it.pendingIntent
                    )
                }
            )
            .addAction(
                NotificationCompat.Action(
                    playPauseAction.iconCompat, playPauseAction.title, playPauseAction.pendingIntent
                )
            )
            .addAction(
                actionFactory.createMediaAction(
                    android.R.drawable.ic_media_next, "Siguiente",
                    PlayerNotificationManager.ACTION_NEXT
                ).let {
                    NotificationCompat.Action(
                        it.iconCompat, it.title, it.pendingIntent
                    )
                }
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        return MediaNotification(1, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false
}
