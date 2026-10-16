// Tile de ajustes rápidos: "Identificar canción" (estilo Shazam/Google)
package com.aural.player

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Acceso directo en el panel de ajustes rápidos para identificar
 * la canción que suena, sin abrir la app manualmente.
 * El usuario lo agrega una vez desde "Editar" en los ajustes rápidos.
 */
class AudioSearchTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra(EXTRA_OPEN_AUDIO_SEARCH, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Cierra el panel y abre la app directo en la búsqueda por audio
        startActivityAndCollapse(intent)
    }

    companion object {
        const val EXTRA_OPEN_AUDIO_SEARCH = "open_audio_search"
    }
}
