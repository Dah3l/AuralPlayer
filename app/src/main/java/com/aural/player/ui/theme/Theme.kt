// Temas de Aural (Material 3)
package com.aural.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Temas disponibles en la app. */
enum class AppTheme(val displayName: String, val emoji: String) {
    BOSQUE("Bosque", "🌿"),
    OCEANO("Océano", "🌊"),
    ATARDECER("Atardecer", "🌅"),
    CLARO("Claro", "☀️"),
    NEON("Neón", "🌃"),
    PERLA("Perla", "🤍"),
    ROSA("Rosa", "🌸"),
    MEDIANOCHE("Medianoche", "🌌")
}

// ---- Tema Bosque: el original (oscuro + verde turquesa) ----
private val BosqueColorScheme = darkColorScheme(
    primary = Color(0xFF2ED3B7),
    onPrimary = Color(0xFF07110E),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1E2129),
    onSurfaceVariant = Color(0xFF9AA1B0),
    outline = Color(0xFF2A2E38),
    outlineVariant = Color(0xFF232733),
    error = Color(0xFFF2555A)
)

// ---- Tema Océano: oscuro + azul ----
private val OceanoColorScheme = darkColorScheme(
    primary = Color(0xFF4DA6FF),
    onPrimary = Color(0xFF0A1420),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF151B24),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1C2532),
    onSurfaceVariant = Color(0xFF9AA8B8),
    outline = Color(0xFF28323F),
    outlineVariant = Color(0xFF212A36),
    error = Color(0xFFF2555A)
)

// ---- Tema Atardecer: oscuro + naranja ----
private val AtardecerColorScheme = darkColorScheme(
    primary = Color(0xFFFF8C42),
    onPrimary = Color(0xFF1A0F08),
    secondary = Color(0xFFFFB37A),
    background = Color(0xFF12100E),
    onBackground = Color(0xFFF0E8E0),
    surface = Color(0xFF1B1714),
    onSurface = Color(0xFFF0E8E0),
    surfaceVariant = Color(0xFF241E18),
    onSurfaceVariant = Color(0xFFB0A094),
    outline = Color(0xFF38302A),
    outlineVariant = Color(0xFF2E2823),
    error = Color(0xFFF2555A)
)

// ---- Tema Claro: claro completo ----
private val ClaroColorScheme = lightColorScheme(
    primary = Color(0xFF0E9F6E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626)
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
 * Tema raíz de la aplicación. El tema se elige en ajustes.
 */
@Composable
fun AuralTheme(
    theme: AppTheme = AppTheme.BOSQUE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppTheme.BOSQUE -> BosqueColorScheme
        AppTheme.OCEANO -> OceanoColorScheme
        AppTheme.ATARDECER -> AtardecerColorScheme
        AppTheme.CLARO -> ClaroColorScheme
        AppTheme.NEON -> NeonColorScheme
        AppTheme.PERLA -> PerlaColorScheme
        AppTheme.ROSA -> RosaColorScheme
        AppTheme.MEDIANOCHE -> MedianocheColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuralTypography,
        content = content
    )
}

// ---- Tema Neón: oscuro + cian/magenta brillante ----
private val NeonColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF001418),
    secondary = Color(0xFFFF00E5),
    background = Color(0xFF0A0A12),
    onBackground = Color(0xFFF0F0FF),
    surface = Color(0xFF12121E),
    onSurface = Color(0xFFF0F0FF),
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFFA0A0C0),
    outline = Color(0xFF2A2A45),
    outlineVariant = Color(0xFF20203A),
    error = Color(0xFFFF3366)
)

// ---- Tema Perla: claro + grises suaves ----
private val PerlaColorScheme = lightColorScheme(
    primary = Color(0xFF5A6C7D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A9BA8),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF2C2C2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30)
)

// ---- Tema Rosa: claro + rosas ----
private val RosaColorScheme = lightColorScheme(
    primary = Color(0xFFFF4D8D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF8AB5),
    background = Color(0xFFFFF5F7),
    onBackground = Color(0xFF3A2A30),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF3A2A30),
    surfaceVariant = Color(0xFFFFE8EE),
    onSurfaceVariant = Color(0xFF8A5A6B),
    outline = Color(0xFFF5C6D6),
    outlineVariant = Color(0xFFFFD6E3),
    error = Color(0xFFFF3B30)
)

// ---- Tema Medianoche: oscuro + grises y violeta ----
private val MedianocheColorScheme = darkColorScheme(
    primary = Color(0xFF9D7BFF),
    onPrimary = Color(0xFF0F0A1E),
    secondary = Color(0xFF7B8CFF),
    background = Color(0xFF121216),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF1A1A20),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF23232C),
    onSurfaceVariant = Color(0xFF9A9AA5),
    outline = Color(0xFF2E2E3A),
    outlineVariant = Color(0xFF26262F),
    error = Color(0xFFFF5555)
)
