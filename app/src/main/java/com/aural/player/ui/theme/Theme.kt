// Tema oscuro minimalista de Aural (Material 3)
package com.aural.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Paleta: fondo casi negro, superficies escalonadas, acento verde-azulado ----
private val Background = Color(0xFF0E0F13)   // Fondo principal
private val Surface = Color(0xFF16181D)      // Superficies elevadas
private val SurfaceVariant = Color(0xFF1E2129) // Chips, contenedores suaves
private val Outline = Color(0xFF2A2E38)      // Divisores sutiles
private val Accent = Color(0xFF2ED3B7)       // Color de acento (verde-turquesa)
private val OnBackground = Color(0xFFE8EAF0) // Texto principal
private val OnSurfaceMuted = Color(0xFF9AA1B0) // Texto secundario

/** Esquema de colores oscuro fijo: la app es siempre dark por diseño. */
private val AuralColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF07110E),
    secondary = Color(0xFF8AB4F8),
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    outlineVariant = Color(0xFF232733),
    error = Color(0xFFF2555A)
)

/** Tipografía limpia con pesos ligeros y espaciado amplio en etiquetas. */
private val AuralTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)

/**
 * Tema raíz de la aplicación. Se ignora el modo del sistema:
 * Aural es exclusivamente oscuro, como su contraparte web.
 */
@Composable
fun AuralTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuralColorScheme,
        typography = AuralTypography,
        content = content
    )
}
