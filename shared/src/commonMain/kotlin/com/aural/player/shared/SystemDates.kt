// Fecha y hora del sistema sin librerías externas: ambas plataformas
// son JVM y java.time está garantizado (Android 26+ y escritorio).
// Existe porque kotlinx-datetime no llegaba al instalador de
// escritorio y abrir los temas moría con NoClassDefFoundError
// (kotlinx/datetime/Clock$System, builds 1.2.11–1.2.13).
package com.aural.player.shared

data class DateParts(val year: Int, val month: Int, val day: Int)

expect object SystemDates {
    /** Fecha LOCAL de hoy. */
    fun today(): DateParts

    /** Clave "yyyy-MM-dd" de hoy menos [daysAgo] días. */
    fun keyDaysAgo(daysAgo: Int): String

    /** Hora LOCAL actual (0–23), para el saludo. */
    fun hour(): Int

    /** Milisegundos desde epoch (cachés con TTL). */
    fun nowEpochMillis(): Long
}
