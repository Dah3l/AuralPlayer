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
import com.aural.player.R

/** Temas disponibles en la app (nombre de catálogo + icono propio del repo). */
enum class AppTheme(val displayName: String, val iconRes: Int) {
    BOSQUE("Aural Classic", R.drawable.ic_launcher_foreground),
    OCEANO("Abyssal Indigo", R.drawable.ic_launcher_foreground_oceano),
    ATARDECER("Solar Amber", R.drawable.ic_launcher_foreground_atardecer),
    CLARO("Prism", R.drawable.ic_launcher_foreground_claro),
    NEON("Neon Prime", R.drawable.ic_launcher_foreground_neon),
    PERLA("Lunar Pearl", R.drawable.ic_launcher_foreground_perla),
    ROSA("Sakura", R.drawable.ic_launcher_foreground_rosa),
    NIGHTLY_SAKURA("Nightly Sakura", R.drawable.ic_launcher_foreground_sakura_nightly),
    MEDIANOCHE("Midnight X", R.drawable.ic_launcher_foreground_medianoche),
    ROYAL_BLUE("Royal Blue", R.drawable.ic_launcher_foreground_royal_blue),
    WINE_RED("Wine Red", R.drawable.ic_launcher_foreground_wine_red),
    ORANGE("Orange", R.drawable.ic_launcher_foreground_orange),
    PLUM("Plum", R.drawable.ic_launcher_foreground_plum),
    MIDNIGHT_INDIGO("Midnight Indigo", R.drawable.ic_launcher_foreground_midnight_indigo),
    CHERRY("Cherry", R.drawable.ic_launcher_foreground_cherry),
    FOREST("Forest", R.drawable.ic_launcher_foreground_forest)
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
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF0E0F13),
    surfaceBright = Color(0xFF2A2E38),
    surfaceContainerLowest = Color(0xFF090A0D),
    surfaceContainerLow = Color(0xFF121317),
    surfaceContainer = Color(0xFF16181D),
    surfaceContainerHigh = Color(0xFF1B1E25),
    surfaceContainerHighest = Color(0xFF1E2129)
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
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF0D1117),
    surfaceBright = Color(0xFF28323F),
    surfaceContainerLowest = Color(0xFF080B0F),
    surfaceContainerLow = Color(0xFF11161E),
    surfaceContainer = Color(0xFF151B24),
    surfaceContainerHigh = Color(0xFF192029),
    surfaceContainerHighest = Color(0xFF1C2532)
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
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF12100E),
    surfaceBright = Color(0xFF38302A),
    surfaceContainerLowest = Color(0xFF0C0A09),
    surfaceContainerLow = Color(0xFF171310),
    surfaceContainer = Color(0xFF1B1714),
    surfaceContainerHigh = Color(0xFF201B17),
    surfaceContainerHighest = Color(0xFF241E18)
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
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE8EDF2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFEFF),
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE8EEF4)
)

/** Esquema de colores de cada tema (lo usa la pantalla de temas para
 *  pintar cada tarjeta con los colores de su propio tema). */
fun AppTheme.colorScheme(): androidx.compose.material3.ColorScheme = when (this) {
    AppTheme.BOSQUE -> BosqueColorScheme
    AppTheme.OCEANO -> OceanoColorScheme
    AppTheme.ATARDECER -> AtardecerColorScheme
    AppTheme.CLARO -> ClaroColorScheme
    AppTheme.NEON -> NeonColorScheme
    AppTheme.PERLA -> PerlaColorScheme
    AppTheme.ROSA -> RosaColorScheme
    AppTheme.NIGHTLY_SAKURA -> NightlySakuraColorScheme
    AppTheme.MEDIANOCHE -> MedianocheColorScheme
    AppTheme.ROYAL_BLUE -> RoyalBlueColorScheme
    AppTheme.WINE_RED -> WineRedColorScheme
    AppTheme.ORANGE -> OrangeColorScheme
    AppTheme.PLUM -> PlumColorScheme
    AppTheme.MIDNIGHT_INDIGO -> MidnightIndigoColorScheme
    AppTheme.CHERRY -> CherryColorScheme
    AppTheme.FOREST -> ForestColorScheme
}

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
    val colorScheme = theme.colorScheme()
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
    error = Color(0xFFFF3366),
    surfaceDim = Color(0xFF0A0A12),
    surfaceBright = Color(0xFF2A2A45),
    surfaceContainerLowest = Color(0xFF06060C),
    surfaceContainerLow = Color(0xFF0E0E18),
    surfaceContainer = Color(0xFF12121E),
    surfaceContainerHigh = Color(0xFF161624),
    surfaceContainerHighest = Color(0xFF1A1A2E)
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
    error = Color(0xFFFF3B30),
    surfaceDim = Color(0xFFE3E3E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFEEF0F2),
    surfaceContainerHighest = Color(0xFFE8E8ED)
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
    error = Color(0xFFFF3B30),
    surfaceDim = Color(0xFFF3E0E6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBFC),
    surfaceContainer = Color(0xFFFFF5F7),
    surfaceContainerHigh = Color(0xFFFFEDF2),
    surfaceContainerHighest = Color(0xFFFFE8EE)
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
    error = Color(0xFFFF5555),
    surfaceDim = Color(0xFF121216),
    surfaceBright = Color(0xFF2E2E3A),
    surfaceContainerLowest = Color(0xFF0C0C0F),
    surfaceContainerLow = Color(0xFF16161A),
    surfaceContainer = Color(0xFF1A1A20),
    surfaceContainerHigh = Color(0xFF1E1E26),
    surfaceContainerHighest = Color(0xFF23232C)
)

// ---- Tema Nightly Sakura: el rosa de Sakura en modo oscuro ----
private val NightlySakuraColorScheme = darkColorScheme(
    primary = Color(0xFFFF6FA5),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF8AB5),
    background = Color(0xFF121013),
    onBackground = Color(0xFFF5EEF2),
    surface = Color(0xFF1A171B),
    onSurface = Color(0xFFF5EEF2),
    surfaceVariant = Color(0xFF262027),
    onSurfaceVariant = Color(0xFFAEA0AA),
    outline = Color(0xFF37303A),
    outlineVariant = Color(0xFF2A242E),
    error = Color(0xFFFF5555),
    surfaceDim = Color(0xFF121013),
    surfaceBright = Color(0xFF37303A),
    surfaceContainerLowest = Color(0xFF0D0B0E),
    surfaceContainerLow = Color(0xFF161317),
    surfaceContainer = Color(0xFF1A171B),
    surfaceContainerHigh = Color(0xFF201A21),
    surfaceContainerHighest = Color(0xFF262027)
)

// ---- Tema Royal Blue: claro, crema y azul royal (dúo de diseño) ----
private val RoyalBlueColorScheme = lightColorScheme(
    primary = Color(0xFF014BAA),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3E74C4),
    background = Color(0xFFF8F3F0),
    onBackground = Color(0xFF1C2430),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2430),
    surfaceVariant = Color(0xFFEDE6E0),
    onSurfaceVariant = Color(0xFF5C6672),
    outline = Color(0xFFDCD2C8),
    outlineVariant = Color(0xFFE7DFD7),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE3DAD2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFBF9),
    surfaceContainer = Color(0xFFF8F3F0),
    surfaceContainerHigh = Color(0xFFF1EAE4),
    surfaceContainerHighest = Color(0xFFEDE6E0)
)

// ---- Tema Wine Red: claro, arena y rojo vino ----
private val WineRedColorScheme = lightColorScheme(
    primary = Color(0xFF7F011F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFA63A50),
    background = Color(0xFFF5EBD0),
    onBackground = Color(0xFF2E1A1C),
    surface = Color(0xFFFFFAF0),
    onSurface = Color(0xFF2E1A1C),
    surfaceVariant = Color(0xFFEEDFC0),
    onSurfaceVariant = Color(0xFF6E5250),
    outline = Color(0xFFDCC9A3),
    outlineVariant = Color(0xFFE8D9B8),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE7D8B4),
    surfaceBright = Color(0xFFFFFAF0),
    surfaceContainerLowest = Color(0xFFFFFDF6),
    surfaceContainerLow = Color(0xFFFAF1DC),
    surfaceContainer = Color(0xFFF5EBD0),
    surfaceContainerHigh = Color(0xFFF0E3C4),
    surfaceContainerHighest = Color(0xFFEEDFC0)
)

// ---- Tema Orange: claro, leche y naranja quemado ----
private val OrangeColorScheme = lightColorScheme(
    primary = Color(0xFFEC5E27),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF08A54),
    background = Color(0xFFFDFDF1),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFF3F0DC),
    onSurfaceVariant = Color(0xFF6B5D4F),
    outline = Color(0xFFE4DFC4),
    outlineVariant = Color(0xFFEDEAD2),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFECE9D2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFEFEF8),
    surfaceContainer = Color(0xFFFDFDF1),
    surfaceContainerHigh = Color(0xFFF6F3E2),
    surfaceContainerHighest = Color(0xFFF3F0DC)
)

// ---- Tema Plum: claro, leche rosada y ciruela profundo ----
private val PlumColorScheme = lightColorScheme(
    primary = Color(0xFF381932),
    onPrimary = Color(0xFFFFF3E6),
    secondary = Color(0xFF6E4466),
    background = Color(0xFFFFF3E6),
    onBackground = Color(0xFF2A1426),
    surface = Color(0xFFFFFAF5),
    onSurface = Color(0xFF2A1426),
    surfaceVariant = Color(0xFFF7E4D2),
    onSurfaceVariant = Color(0xFF71596A),
    outline = Color(0xFFEBD3BE),
    outlineVariant = Color(0xFFF2E2CE),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFF0DCC4),
    surfaceBright = Color(0xFFFFFAF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF8EF),
    surfaceContainer = Color(0xFFFFF3E6),
    surfaceContainerHigh = Color(0xFFF9EADA),
    surfaceContainerHighest = Color(0xFFF7E4D2)
)

// ---- Tema Midnight Indigo: claro, vainilla e índigo noche ----
private val MidnightIndigoColorScheme = lightColorScheme(
    primary = Color(0xFF282B4A),
    onPrimary = Color(0xFFF5F2E4),
    secondary = Color(0xFF565B85),
    background = Color(0xFFEEEBDA),
    onBackground = Color(0xFF1E2033),
    surface = Color(0xFFF8F6EC),
    onSurface = Color(0xFF1E2033),
    surfaceVariant = Color(0xFFE2DEC9),
    onSurfaceVariant = Color(0xFF5A5C6B),
    outline = Color(0xFFD3CFB6),
    outlineVariant = Color(0xFFE0DCC6),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFDCD8C2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFF8F6EC),
    surfaceContainerLow = Color(0xFFF4F1E2),
    surfaceContainer = Color(0xFFEEEBDA),
    surfaceContainerHigh = Color(0xFFE6E2CE),
    surfaceContainerHighest = Color(0xFFE2DEC9)
)

// ---- Tema Cherry: claro, alabastro y cereza ----
private val CherryColorScheme = lightColorScheme(
    primary = Color(0xFF890149),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB83A75),
    background = Color(0xFFF3F3E7),
    onBackground = Color(0xFF2E1220),
    surface = Color(0xFFFDFDF8),
    onSurface = Color(0xFF2E1220),
    surfaceVariant = Color(0xFFE9E7D2),
    onSurfaceVariant = Color(0xFF6E5560),
    outline = Color(0xFFDBD8BE),
    outlineVariant = Color(0xFFE6E4CC),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE0DEC6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8EE),
    surfaceContainer = Color(0xFFF3F3E7),
    surfaceContainerHigh = Color(0xFFECEAD8),
    surfaceContainerHighest = Color(0xFFE9E7D2)
)

// ---- Tema Forest: claro, hoja y verde bosque ----
private val ForestColorScheme = lightColorScheme(
    primary = Color(0xFF064734),
    onPrimary = Color(0xFFE0FFC2),
    secondary = Color(0xFF2E7D5F),
    background = Color(0xFFE0FFC2),
    onBackground = Color(0xFF0C2B1E),
    surface = Color(0xFFF6FFEA),
    onSurface = Color(0xFF0C2B1E),
    surfaceVariant = Color(0xFFCDF2A4),
    onSurfaceVariant = Color(0xFF44604F),
    outline = Color(0xFFBFE392),
    outlineVariant = Color(0xFFD8F7B4),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFCFF0A2),
    surfaceBright = Color(0xFFF6FFEA),
    surfaceContainerLowest = Color(0xFFF9FFF0),
    surfaceContainerLow = Color(0xFFE9FFD0),
    surfaceContainer = Color(0xFFE0FFC2),
    surfaceContainerHigh = Color(0xFFD9FAB2),
    surfaceContainerHighest = Color(0xFFCDF2A4)
)
