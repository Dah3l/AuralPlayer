// Tile de ajustes rápidos: abre AuralPlayer directo en la búsqueda por audio.
// En Android 14+ no se puede usar startActivityAndCollapse(Intent); se usa PendingIntent.
package com.aural.player

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

class AudioSearchTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_AUDIO_SEARCH
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }
}
