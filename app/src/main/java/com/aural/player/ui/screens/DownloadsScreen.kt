// Pantalla de Descargas — diseño premium
package com.aural.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aural.player.youtube.YTResult
import com.aural.player.youtube.YouTubeManager

private val YouTubeRed = Color(0xFFE5484D)
private val YouTubeRedDark = Color(0xFFC93A3E)

/** Descarga terminada para la subpestaña Completadas:
 *  canciones y podcasts en una sola lista por fecha. */
private data class CompletedDownloadTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val timestampMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    youTubeManager: YouTubeManager,
    viewModel: com.aural.player.AuralViewModel? = null,
    onDownloadComplete: (String?, Long, YTResult, Boolean) -> Unit = { _, _, _, _ -> },
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val results by youTubeManager.results.collectAsState()
    val isSearching by youTubeManager.isSearching.collectAsState()
    val downloading by youTubeManager.downloading.collectAsState()
    val progress by youTubeManager.progress.collectAsState()
    val error by youTubeManager.error.collectAsState()
    // v1.0.7: indicador de descarga exitosa por videoId -> nombre de archivo.
    // v1.0.7: el indicador vive en el manager para no perderse en el rescan.
    val downloadedOk by youTubeManager.downloaded.collectAsStateWithLifecycle()
    val ytDetails by youTubeManager.details.collectAsStateWithLifecycle()
    val podcastDetails = viewModel?.podcastDownloadDetails
        ?.collectAsStateWithLifecycle()?.value ?: emptyMap()
    val podcastDownloads = viewModel?.podcastDownloads
        ?.collectAsStateWithLifecycle()?.value ?: emptyMap()
    val podcastProgress = viewModel?.podcastProgress
        ?.collectAsStateWithLifecycle()?.value ?: emptyMap()
    val playlists = viewModel?.playlists
        ?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val downloadedSongs = remember(playlists, downloadedOk) {
        val pl = playlists.firstOrNull { it.name == "Descargas" }
        if (pl != null && viewModel != null) {
            viewModel.songsOfPlaylist(pl)
        } else {
            emptyList()
        }
    }
    // Subpestañas de Descargas: 0 = Búsqueda, 1 = Completadas.
    var downloadsTab by remember { mutableStateOf(0) }
    val completedDownloads = remember(
        downloadedSongs, podcastDownloads, podcastProgress
    ) {
        val podcasts = podcastDownloads.entries.map { (key, download) ->
            val meta = podcastProgress[key]
            CompletedDownloadTile(
                key = "done-pod-$key",
                title = meta?.episodeTitle?.ifBlank { download.fileName }
                    ?: download.fileName,
                subtitle = listOfNotNull(
                    meta?.showTitle?.takeIf { it.isNotBlank() },
                    meta?.durationMs?.takeIf { it > 0L }
                        ?.let { formatDuration(it / 1000L) }
                        ?: meta?.audioBytes?.takeIf { it > 0L }
                            ?.let { formatBytes(it) }
                ).joinToString(" · ").ifBlank { "Podcast descargado" },
                thumbnailUrl = meta?.episodeImageUrl ?: meta?.showImageUrl,
                timestampMs = download.downloadedAtMs
            )
        }
        val songs = downloadedSongs.distinctBy { it.id }.map { song ->
            CompletedDownloadTile(
                key = "done-song-${song.id}",
                title = song.title,
                subtitle = "${song.artist} · " +
                    formatDuration(song.durationMs / 1000L),
                thumbnailUrl = song.albumArtUri,
                timestampMs = song.dateAddedSec * 1000L
            )
        }
        (podcasts + songs).sortedByDescending { it.timestampMs }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Header premium
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(YouTubeRed, YouTubeRedDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Buscador con el botón de YouTube como icono al lado.
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            label = "search_scale"
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("¿Qué quieres escuchar hoy?") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    downloadsTab = 0
                    youTubeManager.search(query)
                },
                enabled = query.isNotBlank() && !isSearching,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YouTubeRed,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar en YouTube"
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Subpestañas: lo buscado y lo ya descargado.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Búsqueda", "Completadas").forEachIndexed { index, label ->
                val selected = downloadsTab == index
                Button(
                    onClick = { downloadsTab = index },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            YouTubeRed
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
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
                    IconButton(onClick = { youTubeManager.clearError() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            }
        }

        // Resultados en tarjetas
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Descargas activas: canciones y podcasts con su
            // progreso, tamaño y cancelar aquí mismo.
            val activeDownloads = ytDetails.values.map { it to true } +
                podcastDetails.values.map { it to false }
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        "Descargando",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(
                    activeDownloads,
                    key = { (detail, isSong) ->
                        (if (isSong) "yt-" else "pod-") + detail.key
                    }
                ) { (detail, isSong) ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        detail.title.ifBlank { "Descarga" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        downloadDetailLine(detail),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isSong) {
                                            youTubeManager.cancelDownload(detail.key)
                                        } else {
                                            viewModel?.cancelPodcastDownload(detail.key)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancelar descarga"
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { detail.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = YouTubeRed
                            )
                        }
                    }
                }
            }
            // Completadas: canciones y podcasts terminados,
            // de más reciente a más antigua, con el mismo
            // tile de las búsquedas.
            if (downloadsTab == 1) {
                if (completedDownloads.isEmpty()) {
                    item {
                        Text(
                            "Todavía no hay descargas completadas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(
                    completedDownloads,
                    key = { it.key }
                ) { completed ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (completed.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = completed.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.Center),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    completed.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    completed.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.width(64.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Descargada",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (downloadsTab == 0) items(results.distinctBy { it.videoId }, key = { it.videoId }) { result ->
                val isDl = result.videoId in downloading
                val pct = progress[result.videoId] ?: 0f

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
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
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (result.thumbnailUrl != null) {
                                AsyncImage(
                                    model = result.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Info
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
                                "${result.artist} · ${formatDuration(result.durationSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Botón descarga / progreso / éxito
                        val justDownloaded = downloadedOk[result.videoId]
                        if (justDownloaded != null) {
                            // v1.0.7: solo el check de descarga exitosa.
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.width(64.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Descargada",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else if (isDl) {
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
                                    onClick = { youTubeManager.cancelDownload(result.videoId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancelar",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            val dlInteraction = remember { MutableInteractionSource() }
                            val dlPressed by dlInteraction.collectIsPressedAsState()
                            val dlScale by animateFloatAsState(
                                if (dlPressed) 0.9f else 1f,
                                label = "dl_scale"
                            )
                            IconButton(
                                onClick = {
                                    youTubeManager.download(result) { fileName, mediaId, tagsEmbedded ->
                                        // v1.0.7: marcar éxito para el indicador.
                                        if (fileName != null) {
                                            youTubeManager.markDownloaded(result.videoId, fileName)
                                        }
                                        onDownloadComplete(fileName, mediaId, result, tagsEmbedded)
                                    }
                                },
                                interactionSource = dlInteraction,
                                modifier = Modifier.scale(dlScale)
                            ) {
                                Icon(
                                    Icons.Default.Download,
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
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L ->
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L ->
        String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun downloadDetailLine(
    detail: com.aural.player.data.ActiveDownload
): String {
    val parts = mutableListOf<String>()
    if (detail.totalBytes > 0L) {
        parts += "${formatBytes(detail.downloadedBytes)} de " +
            formatBytes(detail.totalBytes)
    } else if (detail.downloadedBytes > 0L) {
        parts += formatBytes(detail.downloadedBytes)
    }
    parts += "${detail.percent}%"
    if (detail.speedBytesPerSec > 0L) {
        parts += "${formatBytes(detail.speedBytesPerSec)}/s"
    }
    return parts.joinToString(" · ")
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
