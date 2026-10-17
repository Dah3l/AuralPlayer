// Diálogo para buscar y elegir portada en línea.
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
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var results by remember { mutableStateOf<List<CoverArtResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(artist, title) {
        isLoading = true
        error = false
        results = CoverArtClient.search(artist, title)
        isLoading = false
        if (results.isEmpty()) error = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir portada") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
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
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results) { result ->
                                Column(
                                    modifier = Modifier.clickable {
                                        onSelect(result.artworkUrl)
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = result.thumbnailUrl,
                                        contentDescription = result.collectionName,
                                        modifier = Modifier
                                            .size(100.dp),
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
