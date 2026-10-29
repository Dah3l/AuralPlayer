// Temas de temporada de AuralPlayer.
//
// DISEÑO: la temporada es UN TEMA MÁS del menú (AppTheme.HALLOWEEN), no una
// paleta aplicada por fecha. Este archivo conserva los helpers de ventana
// de fechas y documenta el auto-aplicado:
//
// - Halloween: 27 de octubre – 1 de noviembre.
//   · Aparece en el picker de Temas solo durante su ventana.
//   · Al primer arranque dentro de la ventana se activa solo (guarda el
//     tema previo en DataStore y lo restaura al terminar la ventana).
//   · Si el usuario elige otro tema en el picker, se respeta: no se
//     re-aplica.
// - Navidad: 20 – 26 de diciembre (STUB: mismo patrón, el pack navideño
//   se construye en diciembre — ver "PATRÓN NAVIDAD" abajo).
package com.aural.player.ui.theme

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.MonthDay

/** Temporada activa (o ninguna). */
sealed interface SeasonalTheme {
    data object None : SeasonalTheme
    data object Halloween : SeasonalTheme
    data object Christmas : SeasonalTheme
}

// ---- Ventanas (MonthDay: no dependen del año) ----
private val HALLOWEEN_START = MonthDay.of(10, 27)
private val HALLOWEEN_END = MonthDay.of(11, 1)
private val CHRISTMAS_START = MonthDay.of(12, 20)
private val CHRISTMAS_END = MonthDay.of(12, 26)

/** Temporada según la fecha LOCAL del dispositivo. */
fun currentSeasonalTheme(today: LocalDate = LocalDate.now()): SeasonalTheme {
    val md = MonthDay.from(today)
    return when {
        !md.isBefore(HALLOWEEN_START) && !md.isAfter(HALLOWEEN_END) -> SeasonalTheme.Halloween
        !md.isBefore(CHRISTMAS_START) && !md.isAfter(CHRISTMAS_END) -> SeasonalTheme.Christmas
        else -> SeasonalTheme.None
    }
}

/** ¿Estamos dentro de la ventana de Halloween? (muestra el tema en el picker) */
fun isHalloweenWindow(today: LocalDate = LocalDate.now()): Boolean =
    currentSeasonalTheme(today) == SeasonalTheme.Halloween

/** ¿Estamos dentro de la ventana de Navidad? (stub: se usa en diciembre) */
fun isChristmasWindow(today: LocalDate = LocalDate.now()): Boolean =
    currentSeasonalTheme(today) == SeasonalTheme.Christmas

// ---- Colores de temporada (los usa el decorado y el tema Halloween) ----
/** Naranja calabaza. */
val HalloweenPumpkin = Color(0xFFFF7A1A)

/** Morado de acento. */
val HalloweenPurple = Color(0xFF8B5CF6)

/** Blanco fantasmal. */
val HalloweenGhost = Color(0xFFF2F0EA)

// ---------------------------------------------------------------------------
// PATRÓN NAVIDAD (diciembre): repetir lo de Halloween —
//   1. AppTheme.CHRISTMAS("Navidad", icono) + ChristmasColorScheme en Theme.kt.
//   2. En ThemesScreen: mostrarlo solo si isChristmasWindow().
//   3. Auto-aplicado: flag "christmas_applied_<año>" + clave
//      "theme_before_christmas" (mismo runSeasonalAutoTheme de
//      SettingsStore, generalizado a la temporada).
//   4. Decorado cuando el tema activo == CHRISTMAS (ChristmasHomeAmbient,
//      ChristmasSplashScene en SeasonalDecor.kt).
// ---------------------------------------------------------------------------
