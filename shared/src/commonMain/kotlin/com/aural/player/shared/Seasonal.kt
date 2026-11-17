// Ventanas de temporada de AuralPlayer (versión multiplataforma de
// la lógica de fechas de Seasonal.kt de Android).
//
// - Halloween: 27 de octubre – 1 de noviembre.
//   · Aparece en el picker de Temas solo durante su ventana.
// - Navidad: 20 – 26 de diciembre.
// Los colores de temporada viven en Theme.kt (esquemas Halloween y
// Christmas); aquí solo va la fecha, del reloj del sistema.
package com.aural.player.shared

/** Temporada activa (o ninguna). */
sealed interface SeasonalTheme {
    data object None : SeasonalTheme
    data object Halloween : SeasonalTheme
    data object Christmas : SeasonalTheme
}

/** Temporada según la fecha LOCAL del dispositivo. */
fun currentSeasonalTheme(
    today: DateParts = SystemDates.today()
): SeasonalTheme {
    val month = today.month
    val day = today.day
    return when {
        (month == 10 && day >= 27) || (month == 11 && day == 1) -> SeasonalTheme.Halloween
        month == 12 && day in 20..26 -> SeasonalTheme.Christmas
        else -> SeasonalTheme.None
    }
}

/** ¿Estamos dentro de la ventana de Halloween? (muestra el tema en el picker) */
fun isHalloweenWindow(
    today: DateParts = SystemDates.today()
): Boolean = currentSeasonalTheme(today) == SeasonalTheme.Halloween

/** ¿Estamos dentro de la ventana de Navidad? (muestra el tema en el picker) */
fun isChristmasWindow(
    today: DateParts = SystemDates.today()
): Boolean = currentSeasonalTheme(today) == SeasonalTheme.Christmas
