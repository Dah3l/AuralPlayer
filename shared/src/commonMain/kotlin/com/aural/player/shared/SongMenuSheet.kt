// Hoja inferior con acciones para una canción concreta, igual que la de
// Android: título, añadir a la cola, favorita, compartir Aural link y la
// lista de playlists directa para añadir. En PC vive dentro de la
// ventana (velo + tarjeta inferior centrada, con entrada animada).
package com.aural.player.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SongMenuSheet(
    song: Song,
    playlists: List<Playlist>,
    isFavorite: Boolean,
    fromPlaylistId: String? = null,
    onDismiss: () -> Unit,
    onEnqueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareAuralLink: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onRemoveFromPlaylist: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var showCreateField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(scheme.surface)
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp)
            ) {
                // Tirador
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.outline.copy(alpha = 0.6f))
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                SheetAction("Añadir a la cola") { onEnqueue(); onDismiss() }
                SheetAction(if (isFavorite) "Quitar de favoritas" else "Marcar como favorita") {
                    onToggleFavorite(); onDismiss()
                }
                SheetAction("Compartir Aural link") { onShareAuralLink(); onDismiss() }
                if (fromPlaylistId != null) {
                    SheetAction("Eliminar de la lista", color = scheme.error) {
                        onRemoveFromPlaylist(); onDismiss()
                    }
                } else {
                    Text(
                        "Añadir a playlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    if (playlists.isEmpty()) {
                        Text(
                            "No tienes playlists todavía",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        playlists.forEach { playlist ->
                            val contains = song.filePath in playlist.songPaths
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !contains) {
                                        onAddToPlaylist(playlist.id)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = playlist.name,
                                    color = scheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (contains) {
                                    Text("✓", color = scheme.primary)
                                }
                            }
                        }
                    }
                    if (showCreateField) {
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nueva playlist") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showCreateField = false; newName = "" }) {
                                Text("Cancelar")
                            }
                            TextButton(
                                enabled = newName.isNotBlank(),
                                onClick = { onCreateAndAdd(newName.trim()); onDismiss() }
                            ) {
                                Text("Crear", color = scheme.primary)
                            }
                        }
                    } else {
                        SheetAction("Nueva playlist…") { showCreateField = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
