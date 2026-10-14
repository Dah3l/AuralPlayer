// Pantalla de Descargas — búsqueda y descarga de YouTube
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aural.player.youtube.YTResult
import com.aural.player.youtube.YouTubeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    youTubeManager: YouTubeManager,
    onDownloadComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val results by youTubeManager.results.collectAsState()
    val isSearching by youTubeManager.isSearching.collectAsState()
    val downloading by youTubeManager.downloading.collectAsState()
    val progress by youTubeManager.progress.collectAsState()
    val error by youTubeManager.error.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Descargas",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Busca en YouTube y descarga el audio",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Buscador
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Artista, álbum o canción") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { youTubeManager.search(query) },
            enabled = query.isNotBlank() && !isSearching,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Buscar en YouTube")
        }

        // Indicador de búsqueda
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Buscando...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Error
        error?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { youTubeManager.clearError() }) {
                        Text("OK")
                    }
                }
            }
        }

        // Resultados
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(results, key = { it.videoId }) { result ->
                val isDl = downloading.contains(result.videoId)
                ListItem(
                    headlineContent = {
                        Text(result.title, maxLines = 1)
                    },
                    supportingContent = {
                        Text(
                            "${result.artist} • ${formatDuration(result.durationSec)}",
                            maxLines = 1
                        )
                    },
                    trailingContent = {
                        if (isDl) {
                            val pct = progress[result.videoId] ?: 0f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(64.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${(pct * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                youTubeManager.download(result) { success ->
                                    if (success) onDownloadComplete()
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Descargar")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
