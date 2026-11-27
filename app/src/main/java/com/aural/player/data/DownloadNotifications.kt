// Notificaciones del sistema para descargas (canciones online
// y episodios de podcast): progreso mientras baja y aviso al
// terminar o fallar.
package com.aural.player.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object DownloadNotifications {

    private const val CHANNEL_ID = "aural_downloads"

    /** Id estable de notificación para una descarga. */
    fun idFor(key: String): Int =
        (key.hashCode() and 0x7fffffff).takeIf { it != 0 } ?: 1

    fun progress(context: Context, id: Int, title: String, percent: Int?) {
        val builder = baseBuilder(context)
            .setContentTitle("Descargando")
            .setContentText(
                if (percent != null) "$title · $percent%" else title
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (percent != null) {
            builder.setProgress(100, percent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(context, id, builder.build())
    }

    fun complete(context: Context, id: Int, title: String) {
        val notification = baseBuilder(context)
            .setContentTitle("Descarga completada")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        notify(context, id, notification)
    }

    fun error(context: Context, id: Int, title: String) {
        val notification = baseBuilder(context)
            .setContentTitle("No se pudo descargar")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notify(context, id, notification)
    }

    fun cancel(context: Context, id: Int) {
        manager(context)?.cancel(id)
    }

    private fun baseBuilder(context: Context): Notification.Builder {
        ensureChannel(context)
        return Notification.Builder(context, CHANNEL_ID)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager(context)?.notify(id, notification)
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager

    private fun ensureChannel(context: Context) {
        val manager = manager(context) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
