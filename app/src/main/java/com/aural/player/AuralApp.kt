// Aplicación: instancia única de los componentes compartidos
package com.aural.player

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.aural.player.data.MusicRepository
import com.aural.player.data.SettingsStore
import com.aural.player.playback.PlaybackManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** true mientras alguna actividad de la app está visible (para la isla dinámica). */
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Icono único garantizado: una versión anterior con icono dinámico
        // pudo dejar MainActivity desactivada al cambiar el tema; si es así,
        // se rehabilita aquí (sin tocar nada cuando ya está bien).
        ensureMainActivityEnabled()
        // Guardar reporte de crashes para diagnóstico (temporal)
        setupCrashReporter()
        repository = MusicRepository(this)
        settings = SettingsStore(this)
        playback = PlaybackManager(this)
        // Conectar el MediaController con el servicio en segundo plano
        playback.connect()
        // v1.0.7: rastrear si la app está en primer plano (isla dinámica
        // solo se muestra fuera de la app).
        registerActivityLifecycleCallbacks(ForegroundTracker())
    }

    /** Cuenta actividades resumed: >0 = app visible. */
    private inner class ForegroundTracker : ActivityLifecycleCallbacks {
        private var resumedCount = 0
        override fun onActivityResumed(activity: Activity) {
            resumedCount++
            _isForeground.value = true
        }
        override fun onActivityPaused(activity: Activity) {
            resumedCount = (resumedCount - 1).coerceAtLeast(0)
            if (resumedCount == 0) _isForeground.value = false
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /** Rehabilita MainActivity si quedó desactivada (icono único de la app). */
    private fun ensureMainActivityEnabled() {
        try {
            val component = ComponentName(this, MainActivity::class.java)
            if (packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            Log.w("AuralApp", "No se pudo verificar el icono: ${e.message}")
        }
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

/**
 * Al actualizar la app, el sistema despierta el proceso con este aviso:
 * con eso basta, porque AuralApp.onCreate ya garantiza el icono único.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Sin trabajo propio: la autocuración vive en AuralApp.onCreate.
    }
}
