// Pantalla de temas: rejilla de tarjetas, cada una pintada con su propio tema.
// Réplica de la pantalla de Android (ThemesScreen.kt), con columnas
// adaptables al ancho de la ventana en vez de 2 fijas.
package com.aural.player.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Selector de temas a pantalla completa: cada tarjeta es una miniatura
 * del splash de su tema (marca grande sobre su fondo). Al tocar una, el
 * tema se aplica a toda la app al momento.
 */
@Composable
fun ThemesScreen(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    // Halloween aparece solo durante su ventana (27-oct..1-nov).
    // Navidad aparece solo durante la suya (20-dic..26-dic).
    val visibleThemes = AppTheme.entries.filter {
        (it != AppTheme.HALLOWEEN || isHalloweenWindow()) &&
            (it != AppTheme.CHRISTMAS || isChristmasWindow())
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    AuralIcons.ArrowBack, "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Temas",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${visibleThemes.size} temas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visibleThemes, key = { it.name }) { theme ->
                ThemeCard(
                    theme = theme,
                    selected = theme == currentTheme,
                    onClick = { onSelect(theme) }
                )
            }
        }
    }
}

/** Tarjeta de un tema: su fondo, su marca en grande y su nombre. */
@Composable
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = theme.colorScheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "theme_card_press"
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(shape)
            .background(scheme.background)
            .then(
                if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier.border(1.dp, scheme.outline, shape)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // La marca del tema en grande, como en el splash
            LogoMark(
                color = scheme.primary,
                modifier = Modifier.size(62.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = theme.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                maxLines = 1
            )
            // Etiquetita de temporada (Halloween/Navidad solo aparecen en su ventana)
            if (theme == AppTheme.HALLOWEEN || theme == AppTheme.CHRISTMAS) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Temporada",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .background(scheme.primary, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AuralIcons.Check,
                    contentDescription = "Tema activo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
