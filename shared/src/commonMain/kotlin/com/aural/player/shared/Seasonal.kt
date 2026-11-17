// Ventanas de temporada de AuralPlayer (versión multiplataforma de
// la lógica de fechas de Seasonal.kt de Android).
//
// - Halloween: 27 de octubre – 1 de noviembre.
//   · Aparece en el picker de Temas solo durante su ventana.
// - Navidad: 20 – 26 de diciembre.
// Los colores de temporada viven en Theme.kt (esquemas Halloween y
// Christmas); aquí solo va la fecha, con kotlinx-datetime.
package com.aural.player.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Temporada activa (o ninguna). */
sealed interface SeasonalTheme {
    data object None : SeasonalTheme
    data object Halloween : SeasonalTheme
    data object Christmas : SeasonalTheme
}

/** Temporada según la fecha LOCAL del dispositivo. */
fun currentSeasonalTheme(
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
): SeasonalTheme {
    val month = today.monthNumber
    val day = today.dayOfMonth
    return when {
        (month == 10 && day >= 27) || (month == 11 && day == 1) -> SeasonalTheme.Halloween
        month == 12 && day in 20..26 -> SeasonalTheme.Christmas
        else -> SeasonalTheme.None
    }
}

/** ¿Estamos dentro de la ventana de Halloween? (muestra el tema en el picker) */
fun isHalloweenWindow(
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
): Boolean = currentSeasonalTheme(today) == SeasonalTheme.Halloween

/** ¿Estamos dentro de la ventana de Navidad? (muestra el tema en el picker) */
fun isChristmasWindow(
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
): Boolean = currentSeasonalTheme(today) == SeasonalTheme.Christmas
