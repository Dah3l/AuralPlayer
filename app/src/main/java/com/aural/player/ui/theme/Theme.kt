// Temas de Aural (Material 3)
package com.aural.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aural.player.R

/** Temas disponibles en la app (nombre de catálogo + icono propio del repo).
 * HALLOWEEN es un tema de temporada: solo aparece en el picker durante su
 * ventana (27-oct..1-nov) y se auto-activa al primer arranque en ella. */
enum class AppTheme(val displayName: String, val iconRes: Int) {
    HALLOWEEN("Halloween", R.drawable.ic_launcher_foreground),
    CHRISTMAS("Navidad", R.drawable.ic_launcher_foreground),
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

/**
 * Tipografía de terror (Creepster, SIL OFL): se aplica SOLO cuando el tema
 * Halloween está activo (título "Aural" del home y del splash). Requiere el
 * archivo app/src/main/res/font/halloween.ttf en el repo.
 */
val HalloweenFontFamily = FontFamily(Font(R.font.halloween))

// ---- Tema Bosque: el original (oscuro + verde turquesa) ----
private val BosqueColorScheme = darkColorScheme(
    primary = Color(0xFF2ED3B7),
    onPrimary = Color(0xFF07110E),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF1A1B1F),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF222429),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF2A2D35),
    onSurfaceVariant = Color(0xFF9AA1B0),
    outline = Color(0xFF363A44),
    outlineVariant = Color(0xFF2F333F),
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF1A1B1F),
    surfaceBright = Color(0xFF363A44),
    surfaceContainerLowest = Color(0xFF151619),
    surfaceContainerLow = Color(0xFF1E1F23),
    surfaceContainer = Color(0xFF222429),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF2A2D35)
)

// ---- Tema Océano: oscuro + azul ----
private val OceanoColorScheme = darkColorScheme(
    primary = Color(0xFF4DA6FF),
    onPrimary = Color(0xFF0A1420),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF191D23),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF212730),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF28313E),
    onSurfaceVariant = Color(0xFF9AA8B8),
    outline = Color(0xFF343E4B),
    outlineVariant = Color(0xFF2D3642),
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF191D23),
    surfaceBright = Color(0xFF343E4B),
    surfaceContainerLowest = Color(0xFF14171B),
    surfaceContainerLow = Color(0xFF1D222A),
    surfaceContainer = Color(0xFF212730),
    surfaceContainerHigh = Color(0xFF252C35),
    surfaceContainerHighest = Color(0xFF28313E)
)

// ---- Tema Atardecer: oscuro + naranja ----
private val AtardecerColorScheme = darkColorScheme(
    primary = Color(0xFFFF8C42),
    onPrimary = Color(0xFF1A0F08),
    secondary = Color(0xFFFFB37A),
    background = Color(0xFF1E1C1A),
    onBackground = Color(0xFFF0E8E0),
    surface = Color(0xFF272320),
    onSurface = Color(0xFFF0E8E0),
    surfaceVariant = Color(0xFF302A24),
    onSurfaceVariant = Color(0xFFB0A094),
    outline = Color(0xFF443C36),
    outlineVariant = Color(0xFF3A342F),
    error = Color(0xFFF2555A),
    surfaceDim = Color(0xFF1E1C1A),
    surfaceBright = Color(0xFF443C36),
    surfaceContainerLowest = Color(0xFF181615),
    surfaceContainerLow = Color(0xFF231F1C),
    surfaceContainer = Color(0xFF272320),
    surfaceContainerHigh = Color(0xFF2C2723),
    surfaceContainerHighest = Color(0xFF302A24)
)

// ---- Tema Claro: claro completo ----
private val ClaroColorScheme = lightColorScheme(
    primary = Color(0xFF0E9F6E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFFE9EBED),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFC8CACC),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFB7C0CA),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFDFE1E3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F2F3),
    surfaceContainer = Color(0xFFE9EBED),
    surfaceContainerHigh = Color(0xFFD9DBDC),
    surfaceContainerHighest = Color(0xFFC8CACC)
)

/** Esquema de colores de cada tema (lo usa la pantalla de temas para
 *  pintar cada tarjeta con los colores de su propio tema). */
fun AppTheme.colorScheme(): androidx.compose.material3.ColorScheme = when (this) {
    AppTheme.HALLOWEEN -> HalloweenColorScheme
    AppTheme.CHRISTMAS -> ChristmasColorScheme
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
 * Tema raíz de la aplicación. El tema se elige en ajustes (pantalla Temas);
 * Halloween es un tema más del menú durante su ventana (27-oct..1-nov).
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

// ---- Tema Halloween: temporada 27-oct..1-nov (oscuro + naranja calabaza) ----
private val HalloweenColorScheme = darkColorScheme(
    primary = HalloweenPumpkin,
    onPrimary = Color(0xFF1A0D02),
    primaryContainer = Color(0xFF4A2408),
    onPrimaryContainer = Color(0xFFFFD9B3),
    secondary = HalloweenPurple,
    onSecondary = Color(0xFF0E0A1A),
    secondaryContainer = Color(0xFF2A1E4A),
    onSecondaryContainer = Color(0xFFD6C9FF),
    tertiary = Color(0xFFE8A33D),
    onTertiary = Color(0xFF1A1002),
    tertiaryContainer = Color(0xFF4A2E08),
    onTertiaryContainer = Color(0xFFFFE3B3),
    background = Color(0xFF161619),
    onBackground = HalloweenGhost,
    surface = Color(0xFF201D20),
    onSurface = HalloweenGhost,
    surfaceVariant = Color(0xFF2A262A),
    onSurfaceVariant = Color(0xFFB9B0AE),
    surfaceTint = HalloweenPumpkin,
    inverseSurface = HalloweenGhost,
    inverseOnSurface = Color(0xFF141114),
    inversePrimary = Color(0xFFB34E00),
    surfaceDim = Color(0xFF161619),
    surfaceBright = Color(0xFF3A3232),
    surfaceContainerLowest = Color(0xFF121213),
    surfaceContainerLow = Color(0xFF1C191C),
    surfaceContainer = Color(0xFF201D20),
    surfaceContainerHigh = Color(0xFF262226),
    surfaceContainerHighest = Color(0xFF2A262A),
    outline = Color(0xFF463A36),
    outlineVariant = Color(0xFF362E32),
    scrim = Color(0xFF000000),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD0D0)
)

// ---- Tema Navidad: temporada 20-dic..26-dic (noche + rojo navideño + dorado) ----
private val ChristmasColorScheme = darkColorScheme(
    primary = Color(0xFFE63946),
    onPrimary = Color(0xFF1A0505),
    primaryContainer = Color(0xFF4A1215),
    onPrimaryContainer = Color(0xFFFFD0D0),
    secondary = Color(0xFF2D6A4F),
    onSecondary = Color(0xFFE8F5EC),
    secondaryContainer = Color(0xFF1B4332),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = Color(0xFFD4AF37),
    onTertiary = Color(0xFF1A1202),
    tertiaryContainer = Color(0xFF4A3A08),
    onTertiaryContainer = Color(0xFFFFF3B3),
    background = Color(0xFF171B19),
    onBackground = Color(0xFFF2F5F2),
    surface = Color(0xFF1F2321),
    onSurface = Color(0xFFF2F5F2),
    surfaceVariant = Color(0xFF282E2A),
    onSurfaceVariant = Color(0xFFADB5AD),
    surfaceTint = Color(0xFFE63946),
    inverseSurface = Color(0xFFF2F5F2),
    inverseOnSurface = Color(0xFF131715),
    inversePrimary = Color(0xFFB32530),
    surfaceDim = Color(0xFF171B19),
    surfaceBright = Color(0xFF363F38),
    surfaceContainerLowest = Color(0xFF131514),
    surfaceContainerLow = Color(0xFF1B1F1B),
    surfaceContainer = Color(0xFF1F2321),
    surfaceContainerHigh = Color(0xFF242A25),
    surfaceContainerHighest = Color(0xFF282E2A),
    outline = Color(0xFF3F4740),
    outlineVariant = Color(0xFF333934),
    scrim = Color(0xFF000000),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD0D0)
)

// ---- Tema Neón: oscuro + cian/magenta brillante ----
private val NeonColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF001418),
    secondary = Color(0xFFFF00E5),
    background = Color(0xFF16161E),
    onBackground = Color(0xFFF0F0FF),
    surface = Color(0xFF1E1E2A),
    onSurface = Color(0xFFF0F0FF),
    surfaceVariant = Color(0xFF26263A),
    onSurfaceVariant = Color(0xFFA0A0C0),
    outline = Color(0xFF363651),
    outlineVariant = Color(0xFF2C2C46),
    error = Color(0xFFFF3366),
    surfaceDim = Color(0xFF16161E),
    surfaceBright = Color(0xFF363651),
    surfaceContainerLowest = Color(0xFF121218),
    surfaceContainerLow = Color(0xFF1A1A24),
    surfaceContainer = Color(0xFF1E1E2A),
    surfaceContainerHigh = Color(0xFF222230),
    surfaceContainerHighest = Color(0xFF26263A)
)

// ---- Tema Perla: claro + grises suaves ----
private val PerlaColorScheme = lightColorScheme(
    primary = Color(0xFF5A6C7D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A9BA8),
    background = Color(0xFFE6E6E8),
    onBackground = Color(0xFF2C2C2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFFC6C6C8),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFCECED3),
    error = Color(0xFFFF3B30),
    surfaceDim = Color(0xFFDCDCDE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFEFF0),
    surfaceContainer = Color(0xFFE6E6E8),
    surfaceContainerHigh = Color(0xFFD6D6D8),
    surfaceContainerHighest = Color(0xFFC6C6C8)
)

// ---- Tema Rosa: claro + rosas ----
private val RosaColorScheme = lightColorScheme(
    primary = Color(0xFFFF4D8D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF8AB5),
    background = Color(0xFFF0E6E8),
    onBackground = Color(0xFF3A2A30),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF3A2A30),
    surfaceVariant = Color(0xFFCEC6C8),
    onSurfaceVariant = Color(0xFF8A5A6B),
    outline = Color(0xFFF5C6D6),
    outlineVariant = Color(0xFFE6C1CC),
    error = Color(0xFFFF3B30),
    surfaceDim = Color(0xFFE6DCDE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EFF0),
    surfaceContainer = Color(0xFFF0E6E8),
    surfaceContainerHigh = Color(0xFFDFD6D8),
    surfaceContainerHighest = Color(0xFFCEC6C8)
)

// ---- Tema Medianoche: oscuro + grises y violeta ----
private val MedianocheColorScheme = darkColorScheme(
    primary = Color(0xFF9D7BFF),
    onPrimary = Color(0xFF0F0A1E),
    secondary = Color(0xFF7B8CFF),
    background = Color(0xFF1E1E22),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF26262C),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF2F2F38),
    onSurfaceVariant = Color(0xFF9A9AA5),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF32323B),
    error = Color(0xFFFF5555),
    surfaceDim = Color(0xFF1E1E22),
    surfaceBright = Color(0xFF3A3A46),
    surfaceContainerLowest = Color(0xFF18181B),
    surfaceContainerLow = Color(0xFF222226),
    surfaceContainer = Color(0xFF26262C),
    surfaceContainerHigh = Color(0xFF2A2A32),
    surfaceContainerHighest = Color(0xFF2F2F38)
)

// ---- Tema Nightly Sakura: el rosa de Sakura en modo oscuro ----
private val NightlySakuraColorScheme = darkColorScheme(
    primary = Color(0xFFFF6FA5),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF8AB5),
    background = Color(0xFF1E1C1F),
    onBackground = Color(0xFFF5EEF2),
    surface = Color(0xFF262327),
    onSurface = Color(0xFFF5EEF2),
    surfaceVariant = Color(0xFF322C33),
    onSurfaceVariant = Color(0xFFAEA0AA),
    outline = Color(0xFF433C46),
    outlineVariant = Color(0xFF36303A),
    error = Color(0xFFFF5555),
    surfaceDim = Color(0xFF1E1C1F),
    surfaceBright = Color(0xFF433C46),
    surfaceContainerLowest = Color(0xFF19171A),
    surfaceContainerLow = Color(0xFF221F23),
    surfaceContainer = Color(0xFF262327),
    surfaceContainerHigh = Color(0xFF2C262D),
    surfaceContainerHighest = Color(0xFF322C33)
)

// ---- Tema Royal Blue: claro, crema y azul royal (dúo de diseño) ----
private val RoyalBlueColorScheme = lightColorScheme(
    primary = Color(0xFF014BAA),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3E74C4),
    background = Color(0xFFE9E4E2),
    onBackground = Color(0xFF1C2430),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2430),
    surfaceVariant = Color(0xFFC8C4C2),
    onSurfaceVariant = Color(0xFF5C6672),
    outline = Color(0xFFDCD2C8),
    outlineVariant = Color(0xFFD0C9C2),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFDFDBD8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EDEC),
    surfaceContainer = Color(0xFFE9E4E2),
    surfaceContainerHigh = Color(0xFFD9D4D2),
    surfaceContainerHighest = Color(0xFFC8C4C2)
)

// ---- Tema Wine Red: claro, arena y rojo vino ----
private val WineRedColorScheme = lightColorScheme(
    primary = Color(0xFF7F011F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFA63A50),
    background = Color(0xFFE6DDC4),
    onBackground = Color(0xFF2E1A1C),
    surface = Color(0xFFFFFAF0),
    onSurface = Color(0xFF2E1A1C),
    surfaceVariant = Color(0xFFC6BEA9),
    onSurfaceVariant = Color(0xFF6E5250),
    outline = Color(0xFFDCC9A3),
    outlineVariant = Color(0xFFD1C3A6),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFDCD4BB),
    surfaceBright = Color(0xFFFFFAF0),
    surfaceContainerLowest = Color(0xFFFFFDF6),
    surfaceContainerLow = Color(0xFFEFE8D6),
    surfaceContainer = Color(0xFFE6DDC4),
    surfaceContainerHigh = Color(0xFFD6CEB6),
    surfaceContainerHighest = Color(0xFFC6BEA9)
)

// ---- Tema Orange: claro, leche y naranja quemado ----
private val OrangeColorScheme = lightColorScheme(
    primary = Color(0xFFEC5E27),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF08A54),
    background = Color(0xFFEEEEE3),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFCDCDC3),
    onSurfaceVariant = Color(0xFF6B5D4F),
    outline = Color(0xFFE4DFC4),
    outlineVariant = Color(0xFFD5D3BD),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE4E4D9),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F4ED),
    surfaceContainer = Color(0xFFEEEEE3),
    surfaceContainerHigh = Color(0xFFDDDDD3),
    surfaceContainerHighest = Color(0xFFCDCDC3)
)

// ---- Tema Plum: claro, leche rosada y ciruela profundo ----
private val PlumColorScheme = lightColorScheme(
    primary = Color(0xFF381932),
    onPrimary = Color(0xFFFFF3E6),
    secondary = Color(0xFF6E4466),
    background = Color(0xFFF0E4D8),
    onBackground = Color(0xFF2A1426),
    surface = Color(0xFFFFFAF5),
    onSurface = Color(0xFF2A1426),
    surfaceVariant = Color(0xFFCEC4BA),
    onSurfaceVariant = Color(0xFF71596A),
    outline = Color(0xFFEBD3BE),
    outlineVariant = Color(0xFFDACBB9),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFE6DBCF),
    surfaceBright = Color(0xFFFFFAF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EDE6),
    surfaceContainer = Color(0xFFF0E4D8),
    surfaceContainerHigh = Color(0xFFDFD4C9),
    surfaceContainerHighest = Color(0xFFCEC4BA)
)

// ---- Tema Midnight Indigo: claro, vainilla e índigo noche ----
private val MidnightIndigoColorScheme = lightColorScheme(
    primary = Color(0xFF282B4A),
    onPrimary = Color(0xFFF5F2E4),
    secondary = Color(0xFF565B85),
    background = Color(0xFFE0DDCD),
    onBackground = Color(0xFF1E2033),
    surface = Color(0xFFF8F6EC),
    onSurface = Color(0xFF1E2033),
    surfaceVariant = Color(0xFFC1BEB0),
    onSurfaceVariant = Color(0xFF5A5C6B),
    outline = Color(0xFFD3CFB6),
    outlineVariant = Color(0xFFCAC6B2),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFD6D4C4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFF8F6EC),
    surfaceContainerLow = Color(0xFFE8E6D8),
    surfaceContainer = Color(0xFFE0DDCD),
    surfaceContainerHigh = Color(0xFFD0CEBF),
    surfaceContainerHighest = Color(0xFFC1BEB0)
)

// ---- Tema Cherry: claro, alabastro y cereza ----
private val CherryColorScheme = lightColorScheme(
    primary = Color(0xFF890149),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB83A75),
    background = Color(0xFFE4E4D9),
    onBackground = Color(0xFF2E1220),
    surface = Color(0xFFFDFDF8),
    onSurface = Color(0xFF2E1220),
    surfaceVariant = Color(0xFFC4C4BB),
    onSurfaceVariant = Color(0xFF6E5560),
    outline = Color(0xFFDBD8BE),
    outlineVariant = Color(0xFFCFCDB8),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFDBDBD0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDEDE6),
    surfaceContainer = Color(0xFFE4E4D9),
    surfaceContainerHigh = Color(0xFFD4D4CA),
    surfaceContainerHighest = Color(0xFFC4C4BB)
)

// ---- Tema Forest: claro, hoja y verde bosque ----
private val ForestColorScheme = lightColorScheme(
    primary = Color(0xFF064734),
    onPrimary = Color(0xFFE0FFC2),
    secondary = Color(0xFF2E7D5F),
    background = Color(0xFFD3F0B6),
    onBackground = Color(0xFF0C2B1E),
    surface = Color(0xFFF6FFEA),
    onSurface = Color(0xFF0C2B1E),
    surfaceVariant = Color(0xFFB5CE9D),
    onSurfaceVariant = Color(0xFF44604F),
    outline = Color(0xFFBFE392),
    outlineVariant = Color(0xFFC2DEA2),
    error = Color(0xFFDC2626),
    surfaceDim = Color(0xFFCAE6AF),
    surfaceBright = Color(0xFFF6FFEA),
    surfaceContainerLowest = Color(0xFFF9FFF0),
    surfaceContainerLow = Color(0xFFE0F5CA),
    surfaceContainer = Color(0xFFD3F0B6),
    surfaceContainerHigh = Color(0xFFC4DFA9),
    surfaceContainerHighest = Color(0xFFB5CE9D)
)
