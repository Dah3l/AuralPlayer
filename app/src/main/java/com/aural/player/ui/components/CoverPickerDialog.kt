// Diálogo para buscar y elegir portada: campos editables + resultados.
package com.aural.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aural.player.data.CoverArtClient
import com.aural.player.data.CoverArtResult
import kotlinx.coroutines.launch

@Composable
fun CoverPickerDialog(
    artist: String,
    title: String,
    hasCustomCover: Boolean,
    onSelect: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchTitle by remember { mutableStateOf(title) }
    var searchArtist by remember { mutableStateOf(artist) }
    var results by remember { mutableStateOf<List<CoverArtResult>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        scope.launch {
            isLoading = true
            error = false
            results = CoverArtClient.search(searchArtist, searchTitle)
            isLoading = false
            if (results.isNullOrEmpty()) error = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar portada") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Campos editables
                OutlinedTextField(
                    value = searchTitle,
                    onValueChange = { searchTitle = it },
                    label = { Text("Canción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchArtist,
                    onValueChange = { searchArtist = it },
                    label = { Text("Artista") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { doSearch() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Buscar")
                    }
                    OutlinedButton(
                        onClick = onPickFromGallery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Galería")
                    }
                }

                if (hasCustomCover) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar portada personalizada")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Resultados
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 200.dp, max = 320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Buscando portadas...")
                            }
                        }
                        error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🌐", style = MaterialTheme.typography.displayMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No se encontraron portadas.\nPrueba usar VPN y reintenta.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        results != null -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(results!!) { result ->
                                    Column(
                                        modifier = Modifier.clickable {
                                            onSelect(result.artworkUrl)
                                        },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = result.thumbnailUrl,
                                            contentDescription = result.collectionName,
                                            modifier = Modifier.size(100.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            result.collectionName.ifBlank { result.trackName },
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
