// Aplicación: instancia única de los componentes compartidos
package com.aural.player

import android.app.Application
import com.aural.player.data.MusicRepository
import com.aural.player.data.SettingsStore
import com.aural.player.playback.PlaybackManager

/**
 * Contenedor manual de dependencias (sin librerías de DI externas):
 * repositorio, almacén de ajustes y gestor de reproducción.
 */
class AuralApp : Application() {

    lateinit var repository: MusicRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var playback: PlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        settings = SettingsStore(this)
        playback = PlaybackManager(this)
        // Conectar el MediaController con el servicio en segundo plano
        playback.connect()
    }
}
