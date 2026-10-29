// Temas de temporada de AuralPlayer: se activan solos por fecha.
//
// - Halloween: 27 de octubre – 1 de noviembre (paleta + decoración, construido).
// - Navidad: 20 – 26 de diciembre (STUB: ventana definida y puntos de
//   enganche listos; el pack navideño se construye en diciembre).
package com.aural.player.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.MonthDay

/** Temporada activa (o ninguna). Base lista para Navidad. */
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

/**
 * Temporada según la fecha LOCAL del dispositivo.
 * Halloween hoy 29-oct → activo al compilar.
 */
fun currentSeasonalTheme(today: LocalDate = LocalDate.now()): SeasonalTheme {
    val md = MonthDay.from(today)
    return when {
        !md.isBefore(HALLOWEEN_START) && !md.isAfter(HALLOWEEN_END) -> SeasonalTheme.Halloween
        !md.isBefore(CHRISTMAS_START) && !md.isAfter(CHRISTMAS_END) -> SeasonalTheme.Christmas
        else -> SeasonalTheme.None
    }
}

// ---- Paleta Halloween ----
/** Naranja calabaza. */
val HalloweenPumpkin = Color(0xFFFF7A1A)

/** Morado de acento. */
val HalloweenPurple = Color(0xFF8B5CF6)

/** Blanco fantasmal. */
val HalloweenGhost = Color(0xFFF2F0EA)

/**
 * Aplica la paleta Halloween SOBRE el esquema del usuario (solo en su
 * ventana): negros profundos, primario naranja calabaza, toques morados.
 * El resto de tokens se heredan del tema elegido.
 */
fun ColorScheme.withHalloween(): ColorScheme = copy(
    primary = HalloweenPumpkin,
    onPrimary = Color(0xFF1A0D02),
    primaryContainer = Color(0xFF4A2408),
    onPrimaryContainer = Color(0xFFFFD9B3),
    secondary = HalloweenPurple,
    onSecondary = Color(0xFF0E0A1A),
    background = Color(0xFF0A0A0D),
    onBackground = HalloweenGhost,
    surface = Color(0xFF141114),
    onSurface = HalloweenGhost,
    surfaceVariant = Color(0xFF1E1A1E),
    onSurfaceVariant = Color(0xFFB9B0AE),
    surfaceDim = Color(0xFF0A0A0D),
    surfaceBright = Color(0xFF2E2626),
    surfaceContainerLowest = Color(0xFF060607),
    surfaceContainerLow = Color(0xFF100D10),
    surfaceContainer = Color(0xFF141114),
    surfaceContainerHigh = Color(0xFF1A161A),
    surfaceContainerHighest = Color(0xFF1E1A1E),
    outline = Color(0xFF3A2E2A),
    outlineVariant = Color(0xFF2A2226)
)

/**
 * Punto de enganche de la paleta navideña (diciembre): aquí irá el
 * `withChristmas()` cuando se construya el pack.
 */
fun seasonalColorScheme(base: ColorScheme): ColorScheme {
    return when (currentSeasonalTheme()) {
        SeasonalTheme.Halloween -> base.withHalloween()
        // Navidad (diciembre): aplicar withChristmas() aquí.
        SeasonalTheme.Christmas -> base
        SeasonalTheme.None -> base
    }
}
