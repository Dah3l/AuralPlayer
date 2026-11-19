// Pantalla de Descargas — búsqueda y descarga de audio de YouTube
// directo a la biblioteca. Puerto de la pantalla del móvil
// (DownloadsScreen.kt), con el estado en PlaybackState y las acciones
// en PlayerActions; el motor vive en DesktopPlayer (NewPipeExtractor).
package com.aural.player.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.launch

private val YouTubeRed = Color(0xFFE5484D)
private val YouTubeRedDark = Color(0xFFC93A3E)

/** Miniaturas de YouTube en memoria (máx 40): se cargan bajo demanda. */
private object UrlImageCache {
    private val client by lazy { createHttpClient() }
    private val cache = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        return try {
            val bytes = client.get(url).bodyAsBytes()
            decodeImageBitmap(bytes)?.also {
                if (cache.size >= 40) cache.remove(cache.keys.first())
                cache[url] = it
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun rememberUrlImage(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url == null) null else UrlImageCache.load(url)
    }
    return bitmap
}

@Composable
fun DownloadsScreen(
    state: PlaybackState,
    actions: PlayerActions,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val doSearch: () -> Unit = {
        if (query.isNotBlank() && !state.youTubeSearching) {
            scope.launch { actions.searchYouTube(query) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Cabecera
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(YouTubeRed, YouTubeRedDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AuralIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Descargas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Audio de YouTube, directo a tu biblioteca",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Buscador
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("¿Qué quieres escuchar hoy?") },
            leadingIcon = {
                Icon(AuralIcons.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(AuralIcons.Close, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Botón protagonista
        Button(
            onClick = { doSearch() },
            enabled = query.isNotBlank() && !state.youTubeSearching,
            colors = ButtonDefaults.buttonColors(
                containerColor = YouTubeRed,
                disabledContainerColor = scheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (state.youTubeSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Buscando...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(AuralIcons.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Buscar en YouTube", fontWeight = FontWeight.Bold)
            }
        }

        // Error
        state.youTubeError?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = scheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { actions.clearYouTubeError() }) {
                        Icon(AuralIcons.Close, contentDescription = "Cerrar")
                    }
                }
            }
        }

        // Estado vacío
        if (state.youTubeResults.isEmpty() && !state.youTubeSearching &&
            state.youTubeError == null
        ) {
            Text(
                "Busca una canción o un artista y descárgala en audio: " +
                    "llega a tu biblioteca y suena como cualquier otra canción.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        // Resultados en tarjetas
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 24.dp, top = 8.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.youTubeResults, key = { it.videoId }) { result ->
                    val download = state.downloads.firstOrNull { it.videoId == result.videoId }
                    val isDownloading = download != null &&
                        (download.status == DownloadStatus.QUEUED ||
                            download.status == DownloadStatus.DOWNLOADING)
                    val isDone = download?.status == DownloadStatus.DONE
                    val pct = download?.progress ?: 0f
                    val thumbnail = rememberUrlImage(result.thumbnailUrl)

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = scheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Miniatura
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceVariant)
                            ) {
                                if (thumbnail != null) {
                                    Image(
                                        bitmap = thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        AuralIcons.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.Center),
                                        tint = scheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Información
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    result.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${result.uploader} · ${formatDuration(result.durationSec)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Descarga / progreso / éxito
                            if (isDone) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Icon(
                                        AuralIcons.Check,
                                        contentDescription = "Descargada",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else if (isDownloading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = YouTubeRed
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${(pct * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(
                                        onClick = { actions.cancelDownload(result.videoId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            AuralIcons.Close,
                                            contentDescription = "Cancelar",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { actions.downloadYouTube(result) }
                                ) {
                                    Icon(
                                        AuralIcons.Download,
                                        contentDescription = "Descargar",
                                        tint = YouTubeRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            LazyListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
