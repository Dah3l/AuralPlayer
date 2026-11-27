// Secciones de la biblioteca separadas de LibraryScreen (mismo paquete).
package com.aural.player.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Playlist
import com.aural.player.data.PodcastEpisode
import com.aural.player.data.PodcastProgress
import com.aural.player.data.PodcastShow
import com.aural.player.data.RadioStation
import com.aural.player.data.Song
import com.aural.player.data.TopArtist
import com.aural.player.data.WeekStats
import com.aural.player.data.birthdayDisplayName
import com.aural.player.data.greetingDisplayName
import com.aural.player.data.isBirthdayToday
import com.aural.player.data.todayDateKey
import com.aural.player.ui.components.BirthdayModal
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.FeatureDot
import com.aural.player.ui.components.FeatureKeys
import com.aural.player.ui.components.HalloweenHomeAmbient
import com.aural.player.ui.components.ChristmasHomeAmbient
import com.aural.player.ui.components.MediaGridCard
import com.aural.player.ui.components.NewsModal
import com.aural.player.ui.components.ProfileFormDialog
import com.aural.player.ui.theme.HalloweenFontFamily
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.components.formatDuration
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.components.AlphabetFastScroller
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistAdd
import com.aural.player.youtube.YouTubeManager
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import com.aural.player.data.tabLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.aural.player.data.SettingsStore
import com.aural.player.data.UpdateCheckResult
import com.aural.player.data.UpdateInfo
import com.aural.player.data.checkForUpdate
import com.aural.player.data.installedVersionName
import com.aural.player.ui.components.UpdateAvailableDialog
import androidx.compose.material.icons.filled.Download
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.LinearProgressIndicator


// --------------------------- Búsqueda por pills (1.3.0) ---------------------------

/**
 * Selector de fuente de la búsqueda: Locales (por defecto) u Online.
 * Las pills superiores mandan el tipo (Canciones/Artistas/Álbumes/
 * Playlists); este selector solo decide de dónde salen los resultados.
 */
@Composable
internal fun SearchSourceSelector(
    online: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to "Locales", true to "Online").forEach { (value, label) ->
            val selected = online == value
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onChange(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal const val ONLINE_EMPTY_TEXT =
    "Sin resultados. En algunas regiones YouTube limita el catálogo; sin VPN los resultados pueden no ser óptimos."

/**
 * Estado vacío online con presencia: globo terráqueo vectorial
 * dibujado en Canvas (sin emojis ni imágenes), título corto y
 * la referencia regional en tono secundario. El mismo en las
 * cuatro pestañas Online.
 */
@Composable
internal fun OnlineEmptyState(
    title: String = "Sin resultados",
    note: String = "En algunas regiones YouTube limita el catálogo; sin VPN los resultados pueden no ser óptimos."
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(76.dp)) {
            val stroke = Stroke(width = 3f)
            val radius = size.minDimension / 2f - 4f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(primary, radius = radius, center = center, style = stroke)
            drawLine(
                primary,
                Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y),
                strokeWidth = 3f
            )
            drawOval(
                primary,
                topLeft = Offset(center.x - radius * 0.45f, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 0.9f, radius * 2f),
                style = stroke
            )
            drawArc(
                primary,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius * 0.55f),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 1.1f),
                style = stroke
            )
            drawArc(
                primary,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius * 0.55f),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 1.1f),
                style = stroke
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Aviso único de la primera búsqueda online: el streaming de
 * YouTube puede variar por región y con VPN los resultados y
 * la reproducción suelen ser mejores. Se descarta con
 * «Entendido» y no vuelve (persistente en SettingsStore).
 */
@Composable
internal fun OnlineVpnNoticeBanner(viewModel: AuralViewModel) {
    val seen by viewModel.onlineVpnNoticeSeen.collectAsStateWithLifecycle()
    if (seen) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "El streaming de YouTube puede variar por región; con VPN los resultados y la reproducción suelen ser mejores.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { viewModel.dismissOnlineVpnNotice() }) {
            Text("Entendido")
        }
    }
}

/** Aviso de estado online (sin conexión / error) sobre la pestaña activa. */
@Composable
internal fun OnlineSearchNotice(hasInternet: Boolean, onlineError: String?) {
    val text = when {
        !hasInternet -> "Sin conexión: resultados en línea no disponibles"
        onlineError != null -> "No se pudieron cargar resultados en línea"
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Esqueletos de carga a pantalla completa con shimmer: la
 * lista llena el área de resultados con la misma fila de las
 * canciones y la rejilla hace lo propio para Álbumes y
 * Artistas. Mientras se ve esto, nunca se muestra el vacío.
 */
@Composable
internal fun OnlineSkeletonList() {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(14) { SearchSkeletonRow(brush) }
    }
}

@Composable
internal fun OnlineSkeletonGrid(minSize: androidx.compose.ui.unit.Dp) {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(12) { SearchSkeletonCard(brush) }
    }
}

/** Gradiente que se desplaza en bucle sobre los esqueletos. */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "skeletonShimmerProgress"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val x = progress * 900f
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 450f, 0f),
        end = Offset(x, 0f)
    )
}

/**
 * Artistas online con el mismo diseño de la pestaña local:
 * cuadrícula de tarjetas con portada, nombre y subtítulo.
 */
@Composable
internal fun OnlineArtistsTab(
    artists: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    if (artists.isEmpty()) {
        OnlineEmptyState(); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(artists.distinctBy { it.url }, key = { it.url }) { artist ->
                MediaGridCard(
                    coverUri = artist.thumbnailUrl,
                    title = artist.title,
                    subtitle = artist.subtitle.ifBlank { "Artista" },
                    onClick = { onOpen(artist) }
                )
            }
        }
        val artistNames = remember(artists) { artists.map { it.title } }
        AlphabetFastScroller(
            keys = artistNames,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Álbumes online con el mismo diseño de la pestaña local:
 * cuadrícula de tarjetas con portada, título y artista.
 */
@Composable
internal fun OnlineAlbumsTab(
    albums: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    if (albums.isEmpty()) {
        OnlineEmptyState(); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(albums.distinctBy { it.url }, key = { it.url }) { album ->
                MediaGridCard(
                    coverUri = album.thumbnailUrl,
                    title = album.title,
                    subtitle = album.subtitle,
                    onClick = { onOpen(album) }
                )
            }
        }
        val albumTitles = remember(albums) { albums.map { it.title } }
        AlphabetFastScroller(
            keys = albumTitles,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Playlists online con el mismo molde de fila de Playlists:
 * miniatura cuadrada, nombre, subtítulo y botón de reproducir.
 */
@Composable
internal fun OnlinePlaylistsTab(
    playlists: List<com.aural.player.youtube.OnlineCollectionResult>,
    onOpen: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    onPlay: (com.aural.player.youtube.OnlineCollectionResult) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (playlists.isEmpty()) {
        OnlineEmptyState(); return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(playlists.distinctBy { it.url }, key = { it.url }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = playlist.thumbnailUrl, size = 48.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playlist.subtitle.isNotBlank()) {
                        Text(
                            playlist.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { onPlay(playlist) }) {
                    Icon(
                        Icons.Rounded.PlayArrow, "Reproducir",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Aviso de consumo de datos del primer streaming (una sola vez). */
@Composable
internal fun StreamingNoticeDialog(viewModel: AuralViewModel) {
    val streamingNoticePending by viewModel.streamingNoticePending.collectAsStateWithLifecycle()
    if (streamingNoticePending) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelStreamingNotice() },
            title = { Text("Consumo de datos") },
            text = {
                Text("Escuchar en línea usa datos móviles. Puedes cambiar la calidad o limitarlo a Wi-Fi en Ajustes → Sonido.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmStreamingNotice() }) {
                    Text("Escuchar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStreamingNotice() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Menú ⋮ de una canción online: el mismo en búsqueda y en el detalle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineSongMenuSheet(
    result: com.aural.player.youtube.YTResult,
    playlists: List<Playlist>,
    viewModel: AuralViewModel,
    youTubeManager: YouTubeManager?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 4.dp),
            maxLines = 1
        )
        TextButton(
            onClick = {
                onDismiss()
                scope.launch {
                    viewModel.enqueueOnlineSong(
                        viewModel.registerOnlineSong(result), next = true
                    )
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Reproducir siguiente") }
        TextButton(
            onClick = {
                onDismiss()
                scope.launch {
                    viewModel.enqueueOnlineSong(
                        viewModel.registerOnlineSong(result), next = false
                    )
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Añadir a la cola") }
        TextButton(
            onClick = {
                onDismiss()
                youTubeManager?.download(result) { fileName, mediaId, tagsEmbedded ->
                    if (fileName != null) {
                        viewModel.addDownloadToPlaylist(
                            fileName, mediaId, result, tagsEmbedded
                        )
                        if (!tagsEmbedded) {
                            viewModel.applyYouTubeMetadataFallback(
                                mediaId, result.title, result.artist,
                                result.thumbnailUrl
                            )
                        }
                        android.widget.Toast.makeText(
                            appContext,
                            "Canción descargada",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
        ) { Text("Descargar") }
        Text(
            "Guardar en playlist",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp)
        )
        if (playlists.isEmpty()) {
            Text(
                "No tienes playlists todavía",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            playlists.forEach { pl ->
                TextButton(
                    onClick = {
                        onDismiss()
                        scope.launch {
                            val song = viewModel.registerOnlineSong(result)
                            viewModel.addToPlaylist(pl.id, song.id)
                        }
                    },
                    modifier = Modifier.align(Alignment.Start).padding(start = 12.dp)
                ) { Text(pl.name) }
            }
        }
        Box(Modifier.padding(bottom = 24.dp)) {}
    }
}


@Composable
private fun SearchSkeletonRow(brush: Brush) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun SearchSkeletonCard(brush: Brush) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(13.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(brush)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
    }
}

@Composable
private fun OnlineCollectionRow(
    collection: com.aural.player.youtube.OnlineCollectionResult,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(uri = collection.thumbnailUrl, size = 48.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (collection.subtitle.isNotBlank()) {
                Text(
                    text = collection.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "Reproducir",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

// --------------------------- Pestaña: Canciones ---------------------------

@Composable
internal fun SongsTab(
    songs: List<Song>,
    favorites: Set<Long>,
    currentId: Long?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onFavorite: (Long) -> Unit,
    onMenu: (Song) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    customCovers: Map<Long, String> = emptyMap(),
    customMetadata: Map<Long, Triple<String, String, String>> = emptyMap(),
    // v1.0.7: selección múltiple.
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Song) -> Unit = {},
    onEnterSelection: (Song) -> Unit = {},
    emptyText: String = "Sin resultados para tu búsqueda"
) {
    if (songs.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    val scope = rememberCoroutineScope()
    // Defensa global de claves: si la fuente trae un id
    // repetido (p. ej. el mismo video online dos veces), la
    // LazyColumn crashea con «Key was already used». Se
    // deduplica una sola vez y la lista única alimenta
    // también el scroller A–Z (mismos índices).
    val uniqueSongs = remember(songs) { songs.distinctBy { it.id } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp, end = 32.dp)
        ) {
            items(uniqueSongs, key = { it.id }) { song ->
                val meta = customMetadata[song.id]
                SongRow(
                    title = meta?.first?.ifBlank { song.title } ?: song.title,
                    subtitle = listOf(
                        meta?.second?.ifBlank { song.artist } ?: song.artist,
                        meta?.third?.ifBlank { song.album } ?: song.album
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    coverUri = customCovers[song.id] ?: song.albumArtUri,
                    durationMs = song.durationMs,
                    isPlaying = song.id == currentId,
                    onClick = {
                        if (selectionMode) onToggleSelect(song) else onPlay(song)
                    },
                    onLongClick = { onEnterSelection(song) },
                    selected = song.id in selectedIds,
                    showCheckbox = selectionMode,
                    onlineBadge = song.isOnline,
                    trailing = {
                        if (!selectionMode) {
                            TextButton(onClick = { onMenu(song) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                )
            }
        }
        val songTitles = remember(uniqueSongs) { uniqueSongs.map { it.title } }
        AlphabetFastScroller(
            keys = songTitles,
            onJumpToIndex = { index -> scope.launch { listState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Artistas ---------------------------

@Composable
internal fun ArtistsTab(
    artists: List<com.aural.player.data.Artist>,
    onOpen: (String) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
,
    customCovers: Map<Long, String> = emptyMap()
) {
    if (artists.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(artists.distinctBy { it.name }, key = { it.name }) { artist ->
                MediaGridCard(
                    coverUri = artist.representativeSong.albumArtUri,
                    title = artist.name,
                    subtitle = "${artist.songs.size} ${if (artist.songs.size == 1) "canción" else "canciones"}",
                    onClick = { onOpen(artist.name) }
                )
            }
        }
        val artistNames = remember(artists) { artists.map { it.name } }
        AlphabetFastScroller(
            keys = artistNames,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Álbumes ---------------------------

@Composable
internal fun AlbumsTab(
    albums: List<com.aural.player.data.Album>,
    onOpen: (String) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
,
    customCovers: Map<Long, String> = emptyMap()
) {
    if (albums.isEmpty()) {
        EmptyState("Sin resultados"); return
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 36.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(albums.distinctBy { it.id }, key = { it.id }) { album ->
                MediaGridCard(
                    coverUri = album.songs.firstOrNull()?.albumArtUri,
                    title = album.title,
                    subtitle = album.artist,
                    onClick = { onOpen(album.title) }
                )
            }
        }
        val albumTitles = remember(albums) { albums.map { it.title } }
        AlphabetFastScroller(
            keys = albumTitles,
            onJumpToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------- Pestaña: Carpetas ---------------------------

/**
 * Carpetas en lista (no cuadrícula): tocar abre la lista de canciones
 * (como álbum/playlist), el ojo oculta la carpeta con confirmación.
 */
@Composable
internal fun FoldersTab(
    folders: List<com.aural.player.data.Folder>,
    onOpen: (com.aural.player.data.Folder) -> Unit,
    onHideFolder: (com.aural.player.data.Folder) -> Unit = {}
) {
    if (folders.isEmpty()) {
        EmptyState("Sin carpetas"); return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(folders.distinctBy { it.path }, key = { it.path }) { folder ->
            FolderRow(
                folder = folder,
                onClick = { onOpen(folder) },
                onHide = { onHideFolder(folder) }
            )
        }
    }
}

/** Fila de carpeta: icono, nombre, conteo y botón para ocultarla. */
@Composable
private fun FolderRow(
    folder: com.aural.player.data.Folder,
    onClick: () -> Unit,
    onHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.songs.size} ${if (folder.songs.size == 1) "canción" else "canciones"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = "Ocultar carpeta",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --------------------------- Diálogo nueva playlist ---------------------------

@Composable
internal fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Crear playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Crear", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Diálogo para guardar la Playlist del día como permanente (nombre editable). */
@Composable
internal fun SaveDailyPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("Playlist del día") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Guardar como permanente") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Guardar", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// --------------------------- Pestaña: Playlists ---------------------------

@Composable
internal fun PlaylistsTab(
    playlists: List<Playlist>,
    songCount: (Playlist) -> Int,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onExport: (Playlist) -> Unit,
    onImport: () -> Unit,
    hasFavorites: Boolean,
    onOpenFavorites: () -> Unit,
    downloadsCount: Int,
    onOpenDownloads: () -> Unit,
    dailySongCount: Int,
    dailyCoverUri: String?,
    onOpenDaily: () -> Unit,
    onPlayDaily: () -> Unit,
    onSaveDailyPermanent: () -> Unit,
    recentSongCount: Int,
    recentCoverUri: String?,
    onOpenRecent: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    playlistCovers: Map<String, String>,
    firstSongCover: (Playlist) -> String?,
    onRemoveCover: (Playlist) -> Unit,
    showFixedEntries: Boolean = true
) {
    LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        item {
            Text(
                text = "Tuyas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        if (showFixedEntries) {
        // Entrada fija de Favoritos
        item {
            val favInteraction = remember { MutableInteractionSource() }
            val favPressed by favInteraction.collectIsPressedAsState()
            val favScale by animateFloatAsState(
                targetValue = if (favPressed) 0.98f else 1f,
                label = "fav_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(favScale)
                    .clickable(
                        interactionSource = favInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenFavorites() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = null, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Favoritas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hasFavorites) "Tu selección" else "Toca el ♡ en tus canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenFavorites) {
                    Icon(Icons.Rounded.Favorite, "Abrir favoritas",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Entrada fija de Descargas
        item {
            val dlInteraction = remember { MutableInteractionSource() }
            val dlPressed by dlInteraction.collectIsPressedAsState()
            val dlScale by animateFloatAsState(
                targetValue = if (dlPressed) 0.98f else 1f,
                label = "dl_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(dlScale)
                    .clickable(
                        interactionSource = dlInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenDownloads() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = null, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Descargas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (downloadsCount > 0) "$downloadsCount ${if (downloadsCount == 1) "canción" else "canciones"}"
                        else "Tus canciones de YouTube",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, "Abrir descargas",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // Entrada fija de Agregadas recientemente (v1.0.9): va ANTES de la
        // Playlist del día; su icono de la derecha es un reloj (lista
        // basada en tiempo) que abre la lista, como el corazón de
        // Favoritas y el icono de Descargas.
        item {
            val recentInteraction = remember { MutableInteractionSource() }
            val recentPressed by recentInteraction.collectIsPressedAsState()
            val recentScale by animateFloatAsState(
                targetValue = if (recentPressed) 0.98f else 1f,
                label = "recent_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(recentScale)
                    .clickable(
                        interactionSource = recentInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenRecent() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = recentCoverUri, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Agregadas recientemente", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (recentSongCount > 0)
                            "$recentSongCount ${if (recentSongCount == 1) "canción nueva" else "canciones nuevas"}"
                        else "Todavía no hay canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenRecent) {
                    Icon(Icons.Rounded.Schedule, "Abrir",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // Entrada fija de Playlist del día (UNA sola instancia; rota a diario)
        item {
            val dailyInteraction = remember { MutableInteractionSource() }
            val dailyPressed by dailyInteraction.collectIsPressedAsState()
            val dailyScale by animateFloatAsState(
                targetValue = if (dailyPressed) 0.98f else 1f,
                label = "daily_press"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(dailyScale)
                    .clickable(
                        interactionSource = dailyInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onOpenDaily() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(uri = dailyCoverUri, size = 48.dp)
                Spacer(Modifier.height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Playlist del día", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (dailySongCount > 0)
                            "$dailySongCount ${if (dailySongCount == 1) "canción aleatoria" else "canciones aleatorias"} · se renueva cada día"
                        else "Se genera al abrir la app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayDaily) {
                    Icon(Icons.Rounded.PlayArrow, "Reproducir",
                        tint = MaterialTheme.colorScheme.primary)
                }
                var showDailyMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showDailyMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Opciones")
                    }
                    DropdownMenu(
                        expanded = showDailyMenu,
                        onDismissRequest = { showDailyMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Guardar como permanente") },
                            onClick = {
                                showDailyMenu = false
                                onSaveDailyPermanent()
                            }
                        )
                    }
                }
            }
        }
        // Botones crear / importar playlist
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = onCreate, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Crear playlist", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onImport) {
                    Text("Importar playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        }
        if (playlists.isEmpty()) {
            item { EmptyState("Todavía no has creado playlists") }
        } else {
            items(playlists.filter { it.name != "Descargas" }.distinctBy { it.id }, key = { it.id }) { pl ->
                val rowInteraction = remember { MutableInteractionSource() }
                val rowPressed by rowInteraction.collectIsPressedAsState()
                val rowScale by animateFloatAsState(
                    targetValue = if (rowPressed) 0.98f else 1f,
                    label = "playlist_press"
                )
                // El conteo se calcula una sola vez por fila (no en cada Text).
                val count = songCount(pl)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(rowScale)
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) { onOpen(pl) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArt(uri = playlistCovers[pl.id] ?: firstSongCover(pl), size = 48.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "$count ${if (count == 1) "canción" else "canciones"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onPlayPlaylist(pl) }) {
                        Icon(Icons.Rounded.PlayArrow, "Reproducir",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar") },
                                onClick = {
                                    showMenu = false
                                    onRename(pl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    showMenu = false
                                    onDelete(pl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar lista") },
                                onClick = {
                                    showMenu = false
                                    onExport(pl)
                                }
                            )
                            if (playlistCovers.containsKey(pl.id)) {
                                DropdownMenuItem(
                                    text = { Text("Quitar foto") },
                                    onClick = {
                                        showMenu = false
                                        onRemoveCover(pl)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --------------------------- Emisoras y Podcasts ---------------------------

/** Estado guía de una colección vacía: texto + botón a buscar. */
@Composable
internal fun CollectionGuideEmptyState(text: String, onSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSearch) {
            Text("Buscar")
        }
    }
}

/** Fila del molde Playlists: miniatura cuadrada, nombre,
 *  subtítulo, ▶ y ⋮. El toque en la fila puede abrir el
 *  detalle; el ▶ siempre reproduce. */
@Composable
internal fun CollectionRow(
    title: String,
    subtitle: String,
    coverUri: String?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    showMenu: Boolean = true,
    onClick: () -> Unit = onPlay,
    menuLabel: String = "Quitar",
    onMenuAction: () -> Unit = onRemove
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(uri = coverUri, size = 48.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                Icons.Rounded.PlayArrow, "Reproducir",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (showMenu) Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, "Opciones")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(menuLabel) },
                    onClick = {
                        menuOpen = false
                        onMenuAction()
                    }
                )
            }
        }
    }
}

/** Vista general de Emisoras: solo las guardadas por el usuario. */
@Composable
internal fun RadioTab(
    stations: List<RadioStation>,
    onPlay: (RadioStation) -> Unit,
    onRemove: (RadioStation) -> Unit,
    onSearch: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenDetail: (RadioStation) -> Unit = onPlay
) {
    if (stations.isEmpty()) {
        CollectionGuideEmptyState(
            text = "Aquí van tus emisoras. Busca una y agrégala desde su resultado",
            onSearch = onSearch
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Text(
                text = "Tuyas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        items(stations.distinctBy { it.id }, key = { it.id }) { station ->
            CollectionRow(
                title = station.name,
                subtitle = radioSubtitle(station),
                coverUri = station.logoUrl,
                onPlay = { onPlay(station) },
                onRemove = { onRemove(station) },
                onClick = { onOpenDetail(station) },
                menuLabel = "Quitar de mis Emisoras"
            )
        }
    }
}

/** Vista general de Podcasts: solo los guardados por el usuario. */
@Composable
internal fun PodcastsTab(
    shows: List<PodcastShow>,
    progress: Map<String, PodcastProgress>,
    onPlay: (PodcastShow) -> Unit,
    onRemove: (PodcastShow) -> Unit,
    onSearch: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenDetail: (PodcastShow) -> Unit = onPlay
) {
    if (shows.isEmpty()) {
        CollectionGuideEmptyState(
            text = "Aquí van tus podcasts. Busca uno y agrégalo desde su ficha",
            onSearch = onSearch
        )
        return
    }
    val inProgressCount = progress.values.count { !it.listened && it.positionMs > 0L }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        if (inProgressCount > 0) {
            item {
                CollectionRow(
                    title = "Sigue escuchando",
                    subtitle = "$inProgressCount ${if (inProgressCount == 1) "episodio" else "episodios"} a medias",
                    coverUri = null,
                    onPlay = {
                        val inProgressShow = shows.firstOrNull { show ->
                            progress.any { (key, value) ->
                                key.startsWith(show.feedUrl + "|") &&
                                    !value.listened && value.positionMs > 0L
                            }
                        }
                        inProgressShow?.let(onPlay)
                    },
                    onRemove = {},
                    showMenu = false
                )
            }
        }
        item {
            Text(
                text = "Tuyos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        items(shows.distinctBy { it.feedUrl }, key = { it.feedUrl }) { show ->
            CollectionRow(
                title = show.title,
                subtitle = show.author.ifBlank { "Podcast" },
                coverUri = show.imageUrl,
                onPlay = { onPlay(show) },
                onRemove = { onRemove(show) },
                onClick = { onOpenDetail(show) },
                menuLabel = "Quitar de mis Podcasts"
            )
        }
    }
}

private fun radioSubtitle(station: RadioStation): String {
    val parts = mutableListOf<String>()
    if (station.country.isNotBlank()) parts += station.country
    if (station.genre.isNotBlank()) parts += station.genre
    if (station.bitrateKbps > 0) parts += "${station.bitrateKbps} kbps"
    return parts.joinToString(" · ").ifBlank { "En vivo" }
}

/** Resultados de búsqueda de emisoras (Radio Browser). */
@Composable
internal fun RadioSearchTab(
    stations: List<RadioStation>,
    savedIds: Set<String>,
    searching: Boolean,
    pending: Boolean,
    hasInternet: Boolean,
    onPlay: (RadioStation) -> Unit,
    onToggleSaved: (RadioStation) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (!hasInternet) {
        OnlineEmptyState(
            title = "Sin conexión",
            note = "Sin conexión: resultados en línea no disponibles"
        )
    } else if ((searching || pending) && stations.isEmpty()) {
        OnlineSkeletonList()
    } else if (stations.isEmpty()) {
        OnlineEmptyState(
            note = "Prueba con otro nombre, país o género."
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(stations.distinctBy { it.id }, key = { it.id }) { station ->
                val saved = station.id in savedIds
                CollectionRow(
                    title = station.name,
                    subtitle = radioSubtitle(station),
                    coverUri = station.logoUrl,
                    onPlay = { onPlay(station) },
                    onRemove = { onToggleSaved(station) },
                    menuLabel = if (saved) "Quitar de mis Emisoras"
                    else "Agregar a mis Emisoras",
                    onMenuAction = { onToggleSaved(station) }
                )
            }
        }
    }
}

/** Resultados de búsqueda de podcasts (directorio). */
@Composable
internal fun PodcastSearchTab(
    shows: List<PodcastShow>,
    savedFeedUrls: Set<String>,
    searching: Boolean,
    pending: Boolean,
    hasInternet: Boolean,
    onOpen: (PodcastShow) -> Unit,
    onPlay: (PodcastShow) -> Unit,
    onToggleSaved: (PodcastShow) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (!hasInternet) {
        OnlineEmptyState(
            title = "Sin conexión",
            note = "Sin conexión: resultados en línea no disponibles"
        )
    } else if ((searching || pending) && shows.isEmpty()) {
        OnlineSkeletonList()
    } else if (shows.isEmpty()) {
        OnlineEmptyState(
            note = "Prueba con otro título o autor."
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(shows.distinctBy { it.feedUrl }, key = { it.feedUrl }) { show ->
                val saved = show.feedUrl in savedFeedUrls
                CollectionRow(
                    title = show.title,
                    subtitle = show.author.ifBlank { "Podcast" },
                    coverUri = show.imageUrl,
                    onPlay = { onPlay(show) },
                    onRemove = { onToggleSaved(show) },
                    onClick = { onOpen(show) },
                    menuLabel = if (saved) "Quitar de mis Podcasts"
                    else "Agregar a mis Podcasts",
                    onMenuAction = { onToggleSaved(show) }
                )
            }
        }
    }
}

/** Cabecera hero de las fichas de emisora y podcast. */
@Composable
private fun CollectionDetailHeader(
    coverUri: String?,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    menuLabel: String,
    onMenuAction: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val statusBarTop = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    Column(
        Modifier
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            if (!coverUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(coverUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarTop + 4.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        "Volver",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            "Opciones",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(menuLabel) },
                            onClick = {
                                menuOpen = false
                                onMenuAction()
                            }
                        )
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Ficha de emisora: hero sin lista, con «Sonando ahora». */
@Composable
internal fun RadioStationDetailScreen(
    station: RadioStation,
    saved: Boolean,
    nowPlaying: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        CollectionDetailHeader(
            coverUri = station.logoUrl,
            title = station.name,
            subtitle = radioSubtitle(station),
            onBack = onBack,
            menuLabel = if (saved) "Quitar de mis Emisoras"
            else "Agregar a mis Emisoras",
            onMenuAction = onToggleSaved
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    "Reproducir",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Button(onClick = onToggleSaved) {
                Text(if (saved) "Agregada" else "Agregar")
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Sonando ahora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = nowPlaying?.takeIf { it.isNotBlank() } ?: "En vivo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatPodcastDate(publishedMs: Long): String {
    if (publishedMs <= 0L) return ""
    return try {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es"))
            .format(java.util.Date(publishedMs))
    } catch (e: Exception) {
        ""
    }
}

/** Ficha de podcast: hero, acciones y lista de episodios. */
@Composable
internal fun PodcastDetailScreen(
    show: PodcastShow,
    episodes: List<PodcastEpisode>,
    loading: Boolean,
    progress: Map<String, PodcastProgress>,
    saved: Boolean,
    onBack: () -> Unit,
    onPlayShow: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    onToggleSaved: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val subtitle = listOf(
        show.author,
        if (loading) "Cargando episodios…"
        else "${episodes.size} ${if (episodes.size == 1) "episodio" else "episodios"}"
    ).filter { it.isNotBlank() }.joinToString(" · ")
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Column {
            CollectionDetailHeader(
                coverUri = show.imageUrl,
                title = show.title,
                subtitle = subtitle,
                onBack = onBack,
                menuLabel = if (saved) "Quitar de mis Podcasts"
                else "Agregar a mis Podcasts",
                onMenuAction = onToggleSaved
            )
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onPlayShow,
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Button(onClick = onToggleSaved) {
                    Text(if (saved) "Agregado" else "Agregar")
                }
            }
            }
        }
        if (loading && episodes.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (episodes.isEmpty()) {
            item {
                Text(
                    text = "Sin episodios disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
        items(
            episodes.distinctBy {
                PodcastEpisode.progressKey(show.feedUrl, it.guid)
            },
            key = { PodcastEpisode.progressKey(show.feedUrl, it.guid) }
        ) { episode ->
            val savedProgress = progress[
                PodcastEpisode.progressKey(show.feedUrl, episode.guid)
            ]
            val metaParts = mutableListOf<String>()
            formatPodcastDate(episode.publishedMs)
                .takeIf { it.isNotBlank() }?.let { metaParts += it }
            if (episode.durationMs > 0L) {
                metaParts += formatDuration(episode.durationMs)
            }
            when {
                savedProgress?.listened == true -> metaParts += "Escuchado"
                savedProgress != null && savedProgress.positionMs > 0L ->
                    metaParts += "Vas en ${formatDuration(savedProgress.positionMs)}"
            }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayEpisode(episode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArt(
                        uri = episode.imageUrl ?: show.imageUrl,
                        size = 48.dp
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            episode.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onPlayEpisode(episode) }) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            "Reproducir episodio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (savedProgress != null &&
                    !savedProgress.listened &&
                    savedProgress.positionMs > 0L &&
                    episode.durationMs > 0L
                ) {
                    LinearProgressIndicator(
                        progress = {
                            (savedProgress.positionMs.toFloat() /
                                episode.durationMs.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(3.dp)
                    )
                }
            }
        }
    }
}
