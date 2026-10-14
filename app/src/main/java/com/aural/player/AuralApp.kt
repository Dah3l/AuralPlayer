// Aplicación: instancia única de los componentes compartidos
package com.aural.player

import android.app.Application
import android.util.Log
import com.aural.player.data.MusicRepository
import com.aural.player.data.SettingsStore
import com.aural.player.playback.PlaybackManager
import java.io.File

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
        // Guardar reporte de crashes para diagnóstico (temporal)
        setupCrashReporter()
        repository = MusicRepository(this)
        settings = SettingsStore(this)
        playback = PlaybackManager(this)
        // Conectar el MediaController con el servicio en segundo plano
        playback.connect()
    }

    /** Guarda el stack trace de cualquier crash no capturado en un archivo. */
    private fun setupCrashReporter() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildString {
                    appendLine("=== CRASH ${java.util.Date()} ===")
                    appendLine(Log.getStackTraceString(throwable))
                }
                File(filesDir, "crash_report.txt").writeText(report)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** Lee el último reporte de crash, o null si no hay. */
        fun readCrashReport(app: Application): String? {
            val file = File(app.filesDir, "crash_report.txt")
            return if (file.exists()) file.readText() else null
        }

        /** Borra el reporte de crash. */
        fun clearCrashReport(app: Application) {
            File(app.filesDir, "crash_report.txt").delete()
        }
    }
}
