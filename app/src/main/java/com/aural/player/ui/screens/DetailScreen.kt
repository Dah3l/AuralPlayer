@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
// Pantallas de detalle: álbum, artista, playlist y favoritas
package com.aural.player.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.Song
import coil.compose.AsyncImage
import com.aural.player.ui.components.CropImageDialog
import com.aural.player.ui.components.saveCroppedCover
import com.aural.player.ui.components.EmptyState
import com.aural.player.ui.components.SongRow
import com.aural.player.ui.components.formatDuration
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * Cabecera de detalle con imagen GRANDE: portada cuadrada a lo ancho
 * con el título encima (abajo a la izquierda) y los botones debajo.
 * Se reutiliza para Álbum, Artista, Playlist y Favoritas.
 */
@Composable
private fun DetailHeader(
    coverUri: String?,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    titleAlpha: Float = 1f,
    onImageHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        // Imagen cuadrada a lo ancho (la portada se cambia desde el menú ⋮)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { onImageHeightChanged(it.height) }
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
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
            // Sombra superior para que la flecha de atrás se lea sobre la foto
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
            // Sombra inferior + título y subtítulo sobre la imagen
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
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .alpha(titleAlpha)
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
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        // Botones: los mismos de siempre (reproducir + aleatorio)
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
    removableFromPlaylistId: String? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    /** Id de playlist de usuario para cambiar su foto (null = sin foto editable). */
    playlistCoverId: String? = null
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()

    // Colapso del header: al subir, la imagen se va y la barra superior
    // muestra solo título + flecha (el título no se duplica: cuando el
    // header está expandido vive sobre la imagen, no en la barra)
    var headerHeightPx by remember { mutableStateOf(1) }
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }
    // El título grande se desvanece cuando el scroll pasa el 75% de la
    // imagen: así no choca con el título de la barra al colapsar
    var imageHeightPx by remember { mutableStateOf(1) }
    val imageScrollFraction =
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / imageHeightPx.toFloat()).coerceIn(0f, 1f)
    val bigTitleAlpha = if (imageScrollFraction < 0.75f) 1f
        else (1f - (imageScrollFraction - 0.75f) / 0.25f).coerceIn(0f, 1f)

    // Foto de playlist: el menú ⋮ abre el selector de imágenes;
    // la imagen elegida pasa por el editor de recorte 1:1 antes de guardarse
    var pendingCropUri by remember { mutableStateOf<String?>(null) }
    val cropScope = rememberCoroutineScope()
    val cropContext = LocalContext.current
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { pendingCropUri = it.toString() }
    }
    val playlistCovers by viewModel.playlistCovers.collectAsStateWithLifecycle()
    // Confirmar el recorte: guarda el cuadrado y lo pone de portada
    val cropConfirm: (Bitmap) -> Unit = { cropped ->
        pendingCropUri = null
        val pid = playlistCoverId
        cropScope.launch {
            val saved = if (pid != null) saveCroppedCover(cropContext, pid, cropped) else null
            if (saved != null && pid != null) viewModel.setPlaylistCover(pid, saved)
        }
    }
    // Alto de la barra de estado: la barra colapsada se extiende hacia
    // arriba para cubrir la franja del notch con su mismo color
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    if (songs.isEmpty()) {
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
                if (playlistCoverId != null) {
                    val pid = playlistCoverId
                    CoverMenuButton(
                        hasCustomCover = !playlistCovers[pid].isNullOrBlank(),
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        onAddImage = { coverPicker.launch("image/*") },
                        onRemoveImage = { viewModel.setPlaylistCover(pid, "") }
                    )
                }
            }

            var showPicker by remember { mutableStateOf(false) }
            // Placeholder premium para listas vacías
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Todo tranquilo por aquí...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta lista está esperando su primera canción. ¿Le damos vida?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (removableFromPlaylistId != null && removableFromPlaylistId != FAVORITES_ID) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar canciones", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (showPicker && removableFromPlaylistId != null) {
                SongPickerDialog(
                    songs = viewModel.library.collectAsStateWithLifecycle().value.songs,
                    onDismiss = { showPicker = false },
                    onConfirm = { selected ->
                        selected.forEach { song ->
                            viewModel.addToPlaylist(removableFromPlaylistId, song.id)
                        }
                        showPicker = false
                    }
                )
            }
            // Editor de recorte tras elegir imagen en la galería
            CropDialogHost(
                pendingCropUri = pendingCropUri,
                playlistCoverId = playlistCoverId,
                onDismiss = { pendingCropUri = null },
                onConfirm = cropConfirm
            )
        }
        return
    }

        val headerCoverUri = when {
            playlistCoverId != null && playlistCovers[playlistCoverId] != null ->
                playlistCovers[playlistCoverId]
            else ->
                songs.firstOrNull()?.let { customCovers[it.id] } ?: coverUri
        }

        // ---- Lista de canciones (reordenable en playlists) ----
        // El estado viene de fuera: sobrevive a la navegación y la lista
        // queda donde estaba al volver atrás.
        val detailHaptics = LocalHapticFeedback.current
        // En playlists el reorden es LOCAL e instantáneo, como la cola (que vive
        // en memoria): el orden definitivo se guarda una sola vez, al soltar.
        val reorderable = removableFromPlaylistId != null
        var displaySongs by remember(removableFromPlaylistId) { mutableStateOf(songs) }
        val moveLocal: (Int, Int) -> Unit = { from, to ->
            val current = displaySongs
            if (from in current.indices && to in current.indices && from != to) {
                val reordered = current.toMutableList()
                reordered.add(to, reordered.removeAt(from))
                displaySongs = reordered
            }
        }
        // Reorden con sh.calvin.reorderable (la librería de InnerTune/OuterTune):
        // agarradera, auto-scroll de borde y vecinos que se deslizan, de serie.
        // El padding inferior aparta la zona de auto-scroll del MiniPlayer.
        val reorderableState = rememberReorderableLazyListState(
            listState,
            scrollThresholdPadding = PaddingValues(bottom = 96.dp)
        ) { from, to ->
            // El header ocupa el índice 0 de la lista: las canciones van +1
            if (reorderable && from.index >= 1 && to.index >= 1) {
                moveLocal(from.index - 1, to.index - 1)
                detailHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        // Seguir a la fuente cuando cambia por fuera (altas/bajas), nunca a mitad de arrastre
        LaunchedEffect(songs) {
            if (!reorderableState.isAnyItemDragging && displaySongs.map { it.id } != songs.map { it.id }) {
                displaySongs = songs
            }
        }
        // Al soltar, persistir el orden local si difiere del guardado
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            val pid = removableFromPlaylistId
            if (!reorderableState.isAnyItemDragging && pid != null &&
                displaySongs.map { it.id } != songs.map { it.id }
            ) {
                if (pid == FAVORITES_ID) {
                    viewModel.setFavoriteOrder(displaySongs.map { it.id })
                } else {
                    viewModel.setPlaylistOrder(pid, displaySongs.map { it.id })
                }
            }
        }
        val listSongs = if (reorderable) displaySongs else songs

        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item {
                DetailHeader(
                    coverUri = headerCoverUri,
                    title = title,
                    subtitle = "${songs.size} canciones · ${formatDuration(songs.sumOf { it.durationMs })}",
                    onPlay = { viewModel.playSongs(songs, 0, fromPlaylistId = removableFromPlaylistId) },
                    onShuffle = { viewModel.playSongs(songs.shuffled(), 0, shuffle = true, fromPlaylistId = removableFromPlaylistId) },
                    titleAlpha = bigTitleAlpha,
                    onImageHeightChanged = { imageHeightPx = it },
                    modifier = Modifier.onSizeChanged { headerHeightPx = it.height }
                )
            }
            itemsIndexed(listSongs, key = { _, song -> song.id }) { index, song ->
                if (reorderable) {
                    ReorderableItem(reorderableState, key = song.id) { isDragging ->
                        SongRow(
                            title = customMetadata[song.id]?.first?.ifBlank { song.title } ?: song.title,
                            subtitle = customMetadata[song.id]?.second?.ifBlank { song.artist } ?: song.artist,
                            coverUri = customCovers[song.id] ?: song.albumArtUri,
                            durationMs = song.durationMs,
                            isPlaying = song.id == playerState.currentSong?.id,
                            onClick = { viewModel.playSongs(listSongs, index, fromPlaylistId = removableFromPlaylistId) },
                            modifier = Modifier.graphicsLayer {
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Tres puntos: el mismo menú de la biblioteca; abierto
                                    // desde una playlist trae "Eliminar de la lista" en
                                    // vez de "Añadir a playlist"
                                    TextButton(onClick = { onSongMenu(song) }) {
                                        Text("···", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = "Arrastrar para reordenar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .draggableHandle()
                                            .size(28.dp)
                                    )
                                }
                            }
                        )
                    }
                } else {
                    SongRow(
                        title = customMetadata[song.id]?.first?.ifBlank { song.title } ?: song.title,
                        subtitle = customMetadata[song.id]?.second?.ifBlank { song.artist } ?: song.artist,
                        coverUri = customCovers[song.id] ?: song.albumArtUri,
                        durationMs = song.durationMs,
                        isPlaying = song.id == playerState.currentSong?.id,
                        onClick = { viewModel.playSongs(listSongs, index, fromPlaylistId = removableFromPlaylistId) },
                        trailing = {
                            TextButton(onClick = { onSongMenu(song) }) {
                                Text("···", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    )
                }
            }
        }
            // Barra superior superpuesta: fondo y título aparecen solo
            // cuando el header se colapsa al subir. La columna sube hasta
            // el borde de la pantalla para que la franja del notch tenga
            // el mismo color que la barra (surface, como el Now Playing)
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = -statusBarTop)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = collapseFraction)
                    )
                    // La barra consume sus toques: sin esto, un toque en
                    // la barra cae en la lista que queda detrás
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Spacer(Modifier.fillMaxWidth().height(statusBarTop))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            "Volver",
                            tint = lerp(
                                Color.White,
                                MaterialTheme.colorScheme.onSurface,
                                collapseFraction
                            )
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(collapseFraction),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (playlistCoverId != null) {
                        val pid = playlistCoverId
                        CoverMenuButton(
                            hasCustomCover = !playlistCovers[pid].isNullOrBlank(),
                            iconTint = lerp(
                                Color.White,
                                MaterialTheme.colorScheme.onSurface,
                                collapseFraction
                            ),
                            onAddImage = { coverPicker.launch("image/*") },
                            onRemoveImage = { viewModel.setPlaylistCover(pid, "") }
                        )
                    }
                }
            }
        }
        // Editor de recorte tras elegir imagen en la galería
        CropDialogHost(
            pendingCropUri = pendingCropUri,
            playlistCoverId = playlistCoverId,
            onDismiss = { pendingCropUri = null },
            onConfirm = cropConfirm
        )
}


/**
 * Menú ⋮ de portada (solo playlists): agregar imagen o eliminar la
 * actual. Sin foto personalizada, el header vuelve a su imagen por
 * defecto (portada de la primera canción o la nota musical).
 */
@Composable
private fun CoverMenuButton(
    hasCustomCover: Boolean,
    iconTint: Color,
    onAddImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, "Opciones de portada", tint = iconTint)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Agregar imagen") },
                onClick = { expanded = false; onAddImage() }
            )
            if (hasCustomCover) {
                DropdownMenuItem(
                    text = { Text("Eliminar imagen") },
                    onClick = { expanded = false; onRemoveImage() }
                )
            }
        }
    }
}

/** Muestra el editor de recorte cuando hay una imagen pendiente. */
@Composable
private fun CropDialogHost(
    pendingCropUri: String?,
    playlistCoverId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val pid = playlistCoverId
    if (pendingCropUri != null && pid != null) {
        CropImageDialog(
            imageUri = pendingCropUri,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}


@Composable
private fun SongPickerDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar canciones") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (song.id in selected) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = song.id in selected,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(songs.filter { it.id in selected }) },
                enabled = selected.isNotEmpty()
            ) { Text("Agregar (${selected.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}