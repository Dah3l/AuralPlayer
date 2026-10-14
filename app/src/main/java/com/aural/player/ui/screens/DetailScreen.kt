// Pantallas de detalle: álbum, artista, playlist y favoritas
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Song
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.components.formatDuration

/**
 * Cabecera común de detalle con portada grande, título, subtítulo
 * y botones de reproducir/aleatorio.
 */
@Composable
private fun DetailHeader(
    coverUri: String?,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CoverArt(uri = coverUri, size = 190.dp, cornerRadius = 16.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Botón principal redondo de acento: reproducir todo
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Rounded.PlayArrow, "Reproducir todo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp))
            }
            // Botón secundario redondo: reproducción aleatoria
            IconButton(
                onClick = onShuffle,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Rounded.Shuffle, "Aleatorio",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Pantalla genérica de detalle que muestra una lista de canciones con
 * cabecera. Se reutiliza para Álbum, Artista, Playlist y Favoritas.
 */
@Composable
fun DetailScreen(
    viewModel: AuralViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    coverUri: String?,
    onBack: () -> Unit,
    onSongMenu: (Song) -> Unit,
    removableFromPlaylistId: String? = null
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // ---- Barra superior con volver ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Volver")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (songs.isEmpty()) {
            EmptyState("Esta lista no tiene canciones todavía")
            return@Column
        }

        DetailHeader(
            coverUri = coverUri,
            title = title,
            subtitle = "${songs.size} canciones · ${formatDuration(songs.sumOf { it.durationMs })}",
            onPlay = { viewModel.playSongs(songs, 0) },
            onShuffle = { viewModel.playSongs(songs.shuffled(), 0, shuffle = true) }
        )

        Spacer(Modifier.height(8.dp))

        // ---- Lista de canciones ----
        LazyColumn(Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    title = song.title,
                    subtitle = song.artist,
                    coverUri = song.albumArtUri,
                    durationMs = song.durationMs,
                    isPlaying = song.id == playerState.currentSong?.id,
                    onClick = { viewModel.playSongs(songs, songs.indexOf(song)) },
                    trailing = {
                        Row {
                            IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                Icon(
                                    imageVector = if (favorites.contains(song.id))
                                        Icons.Rounded.Favorite
                                    else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (favorites.contains(song.id))
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (removableFromPlaylistId != null) {
                                // Opción de quitar de la playlist actual
                                TextButton(onClick = {
                                    viewModel.removeFromPlaylist(removableFromPlaylistId, song.id)
                                }) {
                                    Text("Quitar", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                TextButton(onClick = { onSongMenu(song) }) {
                                    Text("···", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
