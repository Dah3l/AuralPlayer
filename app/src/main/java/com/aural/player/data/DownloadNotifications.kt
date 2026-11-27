// Notificaciones del sistema para descargas (canciones online
// y episodios de podcast): progreso mientras baja y aviso al
// terminar o fallar.
package com.aural.player.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object DownloadNotifications {

    private const val CHANNEL_ID = "aural_downloads"

    /** Id estable de notificación para una descarga. */
    fun idFor(key: String): Int =
        (key.hashCode() and 0x7fffffff).takeIf { it != 0 } ?: 1

    fun progress(context: Context, id: Int, title: String, percent: Int?) {
        progress(context, id, title, percent, null, null, null)
    }

    /** Progreso desde los detalles reales de la descarga:
     *  nombre en su línea y debajo, en orden, tamaño total,
     *  descargado, porcentaje y velocidad. */
    fun progress(
        context: Context,
        id: Int,
        title: String,
        detail: ActiveDownload,
        cancelKind: String?,
        cancelKey: String?
    ) {
        progress(
            context, id, title, detail.percent,
            orderedDetailText(detail), cancelKind, cancelKey
        )
    }

    /** Progreso con particularidades y acción de cancelar.
     *  Tocar la notificación abre la pestaña Descargas. */
    fun progress(
        context: Context,
        id: Int,
        title: String,
        percent: Int?,
        detailText: String?,
        cancelKind: String?,
        cancelKey: String?
    ) {
        val detailLines = detailText
            ?.takeIf { it.isNotBlank() }
            ?: percent?.let { "Progreso: $it%" }
        val expandedText = listOfNotNull(title, detailLines)
            .joinToString("\n")
        val builder = baseBuilder(context)
            .setContentTitle(title)
            .setSubText("Descargando")
            .setContentText(detailLines ?: title)
            .setStyle(
                Notification.BigTextStyle().bigText(expandedText)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openDownloadsIntent(context, id))
        if (cancelKind != null && cancelKey != null) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "Cancelar",
                    cancelIntent(context, id, cancelKind, cancelKey)
                ).build()
            )
        }
        if (percent != null) {
            builder.setProgress(100, percent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(context, id, builder.build())
    }

    private fun openDownloadsIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, com.aural.player.MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(
                com.aural.player.MainActivity.EXTRA_OPEN_YOUTUBE_TAB, true
            )
        return PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIntent(
        context: Context,
        id: Int,
        kind: String,
        key: String
    ): PendingIntent {
        val intent = Intent(context, com.aural.player.MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(
                com.aural.player.MainActivity.EXTRA_CANCEL_DOWNLOAD_KIND, kind
            )
            .putExtra(
                com.aural.player.MainActivity.EXTRA_CANCEL_DOWNLOAD_KEY, key
            )
        return PendingIntent.getActivity(
            context, id + 500_000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun complete(context: Context, id: Int, title: String) {
        complete(context, id, title, null)
    }

    /** Cierre de la descarga: sustituye la notificación de
     *  progreso (mismo id) por una completada, sin barra ni
     *  estado «descargando» aunque el último progreso fuera
     *  100%. */
    fun complete(
        context: Context,
        id: Int,
        title: String,
        detail: ActiveDownload?
    ) {
        val detailLines = detail?.let { orderedDetailText(it) }
        val expandedText = listOfNotNull(title, detailLines)
            .joinToString("\n")
        val notification = baseBuilder(context)
            .setContentTitle("Descarga completada")
            .setContentText(title)
            .setStyle(
                Notification.BigTextStyle().bigText(expandedText)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent(context, id))
            .build()
        notify(context, id, notification)
    }

    /** Particularidades en orden fijo, una por línea. */
    private fun orderedDetailText(detail: ActiveDownload): String {
        val lines = mutableListOf<String>()
        lines += if (detail.totalBytes > 0L) {
            "Tamaño total: ${formatBytes(detail.totalBytes)}"
        } else {
            "Tamaño total: calculando…"
        }
        lines += "Descargado: ${formatBytes(detail.downloadedBytes)}"
        lines += "Progreso: ${detail.percent}%"
        lines += if (detail.speedBytesPerSec > 0L) {
            "Velocidad: ${formatBytes(detail.speedBytesPerSec)}/s"
        } else {
            "Velocidad: calculando…"
        }
        return lines.joinToString("\n")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L ->
            String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L ->
            String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun error(context: Context, id: Int, title: String) {
        val notification = baseBuilder(context)
            .setContentTitle("No se pudo descargar")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent(context, id))
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
