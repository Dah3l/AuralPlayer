// Diálogo para buscar letra en línea (canción + artista editables)
package com.aural.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LyricsSearchDialog(
    initialTrack: String,
    initialArtist: String,
    isSearching: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSearch: (track: String, artist: String) -> Unit
) {
    var track by remember { mutableStateOf(initialTrack) }
    var artist by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar letra en línea") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = track,
                    onValueChange = { track = it },
                    label = { Text("Canción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching
                )
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSearch(track.trim(), artist.trim()) },
                enabled = !isSearching && track.isNotBlank() && artist.isNotBlank()
            ) {
                Text("Buscar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSearching) {
                Text("Cancelar")
            }
        }
    )
}
