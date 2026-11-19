// Barras de multiselección: la superior (cerrar + conteo + "Todas")
// y la inferior (Playlist / A la cola / Favorita / Eliminar), como
// en la app Android. La animación de entrada/salida la pone quien
// las muestra (AnimatedVisibility con deslizamiento).
package com.aural.player.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = AuralIcons.Close,
                    contentDescription = "Cancelar selección",
                    tint = scheme.onSurface
                )
            }
            Text(
                text = "$count seleccionada${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = AuralIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Todas",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary
                )
            }
        }
    }
}

@Composable
fun SelectionBottomBar(
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectionActionButton(
                icon = AuralIcons.PlaylistAdd,
                label = "Playlist",
                tint = scheme.onSurface,
                onClick = onAddToPlaylist
            )
            SelectionActionButton(
                icon = AuralIcons.QueueMusic,
                label = "A la cola",
                tint = scheme.onSurface,
                onClick = onAddToQueue
            )
            SelectionActionButton(
                icon = AuralIcons.Heart,
                label = "Favorita",
                tint = scheme.error,
                onClick = onFavorite
            )
            SelectionActionButton(
                icon = AuralIcons.Trash,
                label = "Eliminar",
                tint = scheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(text = label, color = tint, fontSize = 11.sp)
    }
}
