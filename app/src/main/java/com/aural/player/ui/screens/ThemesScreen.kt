// Pantalla de temas: rejilla de tarjetas, cada una pintada con su propio tema
package com.aural.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.theme.colorScheme
import com.aural.player.ui.theme.isHalloweenWindow

/**
 * Selector de temas a pantalla completa: cada tarjeta es una miniatura
 * del splash de su tema (icono grande sobre su fondo). Al tocar una, el
 * tema se aplica a toda la app al momento.
 */
@Composable
fun ThemesScreen(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onBack: () -> Unit,
    gridState: LazyGridState
) {
    // Halloween aparece en el menú solo durante su ventana (27-oct..1-nov).
    // Navidad (diciembre): añadir aquí su condición con isChristmasWindow().
    val visibleThemes = AppTheme.entries.filter {
        it != AppTheme.HALLOWEEN || isHalloweenWindow()
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
                    Icons.Rounded.ArrowBack, "Volver",
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
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp
            ),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
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

/** Tarjeta de un tema: su fondo, su icono en grande y su nombre. */
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
            // El icono del tema en grande, como en el splash
            Image(
                painter = painterResource(id = theme.iconRes),
                contentDescription = null,
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
            // Etiquetita de temporada (Halloween solo aparece en su ventana)
            if (theme == AppTheme.HALLOWEEN) {
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
                    Icons.Rounded.Check,
                    contentDescription = "Tema activo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
