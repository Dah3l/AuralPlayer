// Icono dinámico del lanzador: el icono sigue al tema aplicado en la app
package com.aural.player

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Cambia la entrada de lanzador activa según el tema.
 *
 * La actividad principal (tema Bosque, icono original) y cada alias de
 * AndroidManifest (uno por tema) son componentes intercambiables: se activa
 * el que corresponde al tema y se desactivan los demás, siempre activando
 * primero el destino para que la app nunca se quede sin icono. Los cambios
 * van con DONT_KILL_APP para no cerrar la app en curso.
 */
object AppIconManager {

    private const val MAIN = "com.aural.player.MainActivity"

    private val ALIASES = listOf(
        "com.aural.player.IconOceano",
        "com.aural.player.IconAtardecer",
        "com.aural.player.IconClaro",
        "com.aural.player.IconNeon",
        "com.aural.player.IconPerla",
        "com.aural.player.IconRosa",
        "com.aural.player.IconMedianoche"
    )

    /** Componente de lanzador que corresponde a cada tema (por nombre del enum AppTheme). */
    private fun componentForTheme(themeName: String): String = when (themeName) {
        "OCEANO" -> "com.aural.player.IconOceano"
        "ATARDECER" -> "com.aural.player.IconAtardecer"
        "CLARO" -> "com.aural.player.IconClaro"
        "NEON" -> "com.aural.player.IconNeon"
        "PERLA" -> "com.aural.player.IconPerla"
        "ROSA" -> "com.aural.player.IconRosa"
        "MEDIANOCHE" -> "com.aural.player.IconMedianoche"
        else -> MAIN // BOSQUE (y cualquier valor desconocido): icono original
    }

    /** Aplica el icono del tema indicado. Llamar desde un hilo de fondo. */
    fun applyForTheme(context: Context, themeName: String) {
        val target = componentForTheme(themeName)
        val pm = context.packageManager
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            (listOf(MAIN) + ALIASES)
                .filter { it != target }
                .forEach { name ->
                    pm.setComponentEnabledSetting(
                        ComponentName(context.packageName, name),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
        } catch (e: Exception) {
            android.util.Log.e("AppIconManager", "No se pudo cambiar el icono: ${e.message}")
        }
    }
}
