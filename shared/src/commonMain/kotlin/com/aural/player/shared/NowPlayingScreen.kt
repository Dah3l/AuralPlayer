// Now Playing de escritorio: dos columnas siempre — portada + título
// + controles a la izquierda, letra completa sincronizada a la derecha.
// Fondo ambiental con los colores de la portada, carátula grande con
// brillo, barra de progreso, transporte con modo de repetición/
// aleatorio y el menú ⋮ con los 10 ítems.
package com.aural.player.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    val song = state.currentSong
    // Si la canción actual desaparece (borrada o quitada de la cola), se cierra.
    LaunchedEffect(song) {
        if (song == null) onDismiss()
    }
    if (song != null) {
        NowPlayingContent(song, state, actions, onDismiss, onNotice)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(
    song: Song,
    state: PlaybackState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // Contraste de la letra: fondo oscuro → letra clara y viceversa.
    val lyricsDark = isDarkBackground(scheme.background)
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var showEditMetadata by remember { mutableStateOf(false) }
    var showLyricsPicker by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchingLyrics by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var lyricsReload by remember { mutableStateOf(0) }

    val coverBitmap = rememberCoverImage(song, actions::loadCoverBytes)
    val palette = remember(coverBitmap) { extractPalette(coverBitmap, scheme.primary) }

    // Letra: guardada o buscada en línea; se recarga al agregar una nueva.
    var lrcLines by remember(song.filePath) { mutableStateOf<List<LrcLine>>(emptyList()) }
    LaunchedEffect(song.filePath, lyricsReload) {
        lrcLines = emptyList()
        val content = runCatching { actions.loadLyrics(song) }.getOrNull()
        if (!content.isNullOrBlank()) {
            val parsed = LrcParser.parse(content)
            lrcLines = if (parsed.isNotEmpty()) parsed
            else content.lines().filter { it.isNotBlank() }.map { LrcLine(0L, it.trim()) }
        }
    }
    val currentLineIndex = if (lrcLines.isEmpty()) -1
    else LrcParser.currentIndex(lrcLines, state.positionMs)
    // La letra vive en la columna derecha del Now Playing: se desplaza
    // sola a la línea actual, sin pelearle el scroll al usuario.
    val lyricsListState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex < 0) return@LaunchedEffect
        val visible = lyricsListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (currentLineIndex !in visible) {
            lyricsListState.animateScrollToItem(currentLineIndex.coerceAtLeast(0))
        }
    }

    val isFavorite = song.filePath in state.favorites

    // ---- Progreso (declarado arriba porque lo usa el bloque de búsqueda) ----
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val sliderValue = if (dragging) dragValue * duration.toFloat()
    else state.positionMs.toFloat().coerceIn(0f, duration.toFloat())

    // ---- Bloques reutilizables: en ventana ancha se reordenan en 2 columnas ----
    val coverBlock: @Composable (Dp) -> Unit = { size ->
        // Carátula grande con brillo del color dominante
        CoverArt(
            bitmap = coverBitmap,
            modifier = Modifier.size(size).shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = palette.firstOrNull()?.copy(alpha = 0.6f) ?: scheme.primary
            ),
            cornerRadius = 16.dp,
            showBorder = false
        )
    }
    val lyricsBlock: @Composable (Modifier) -> Unit = { modifier ->
        // Letra completa en la columna derecha (la línea actual resalta).
        if (lrcLines.isEmpty()) {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Esta canción no tiene letra guardada.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Agregar letra",
                        color = scheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { lyricsError = null; showLyricsPicker = true }
                    )
                }
            }
        } else {
            LazyColumn(
                state = lyricsListState,
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lrcLines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                    val isCurrent = index == currentLineIndex
                    Text(
                        text = line.text,
                        style = if (isCurrent) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isCurrent && lyricsDark -> scheme.primary
                            isCurrent -> scheme.onBackground
                            lyricsDark -> Color.White.copy(alpha = 0.75f)
                            else -> Color.Black.copy(alpha = 0.75f)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
    val titleBlock: @Composable () -> Unit = {
        // Título + artista + corazón
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
            IconButton(onClick = { actions.toggleFavorite(song) }) {
                Icon(
                    imageVector = if (isFavorite) AuralIcons.Heart else AuralIcons.HeartOutline,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
    val seekBlock: @Composable () -> Unit = {
        // Barra de progreso (busca solo al soltar)
        Slider(
            value = sliderValue,
            onValueChange = {
                dragValue = (it / duration).coerceIn(0f, 1f)
                dragging = true
            },
            onValueChangeFinished = { dragging = false; actions.seek(dragValue) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.surfaceVariant
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            spotColor = scheme.primary.copy(alpha = 0.5f)
                        )
                        .background(scheme.primary, shape = CircleShape)
                )
            },
            track = { sliderState ->
                val fraction = if (sliderState.valueRange.endInclusive > 0) {
                    (sliderState.value - sliderState.valueRange.start) /
                        (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                } else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(scheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(scheme.primary, shape = RoundedCornerShape(4.dp))
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(sliderValue.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    val transportBlock: @Composable () -> Unit = {
        // Controles
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modo: sin repetir → repetir todo → repetir uno → aleatorio
            IconButton(onClick = { actions.cyclePlaybackMode() }) {
                val (icon, tint) = when {
                    state.shuffleEnabled -> AuralIcons.Shuffle to scheme.primary
                    state.repeatMode == RepeatMode.ONE -> AuralIcons.RepeatOne to scheme.primary
                    state.repeatMode == RepeatMode.ALL -> AuralIcons.Repeat to scheme.primary
                    else -> AuralIcons.Repeat to scheme.onSurfaceVariant
                }
                Icon(icon, contentDescription = "Modo de reproducción", tint = tint, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { actions.previous() }) {
                Icon(
                    imageVector = AuralIcons.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape, spotColor = scheme.primary.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable { actions.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) AuralIcons.Pause else AuralIcons.Play,
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { actions.next() }) {
                Icon(
                    imageVector = AuralIcons.SkipNext,
                    contentDescription = "Siguiente",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(
                    imageVector = AuralIcons.QueueMusic,
                    contentDescription = "Cola",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
    val volumeBlock: @Composable () -> Unit = {
        // Volumen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { actions.toggleMute() }) {
                Icon(
                    imageVector = if (state.muted || state.volume == 0) AuralIcons.VolumeOff else AuralIcons.VolumeUp,
                    contentDescription = if (state.muted) "Activar sonido" else "Silenciar",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Slider(
                value = state.volume / 100f,
                onValueChange = { actions.setVolume((it * 100).roundToInt().coerceIn(0, 100)) },
                modifier = Modifier.width(180.dp),
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.outlineVariant
                )
            )
            Text(
                text = "${state.volume}%",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.width(44.dp)
            )
        }
    }

    // Dos columnas siempre (el ancho mínimo de la ventana, 960 dp,
    // alcanza para ambas): portada + controles a la izquierda, letra
    // completa a la derecha. La portada se ajusta al espacio
    // disponible (alto y ancho) con tope de 380 dp.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        NowPlayingAmbientBackground(palette = palette)
        val coverSize = minOf(maxHeight * 0.36f, maxWidth * 0.38f)
            .coerceIn(180.dp, 380.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Barra superior: cerrar + indicador + menú ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = AuralIcons.ChevronDown,
                        contentDescription = "Cerrar",
                        tint = scheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "REPRODUCIENDO AHORA",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = AuralIcons.MoreVert,
                            contentDescription = "Opciones",
                            tint = scheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(scheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Agregar a la lista de reproducción") },
                            leadingIcon = { Icon(AuralIcons.PlaylistAdd, null) },
                            onClick = { showMenu = false; showPlaylists = true }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when {
                                        state.sleepMinutes != null ->
                                            "Temporizador: ${state.sleepMinutes} min"
                                        state.stopMode == StopMode.SONG ->
                                            "Temporizador: al terminar la canción"
                                        state.stopMode == StopMode.QUEUE ->
                                            "Temporizador: al terminar la lista"
                                        else -> "Temporizador de suspensión"
                                    }
                                )
                            },
                            leadingIcon = { Icon(AuralIcons.Timer, null) },
                            onClick = { showMenu = false; showTimer = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Velocidad de reproducción") },
                            leadingIcon = { Icon(AuralIcons.Gauge, null) },
                            onClick = { showMenu = false; showSpeed = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Ecualizador") },
                            leadingIcon = { Icon(AuralIcons.Tune, null) },
                            onClick = { showMenu = false; showEqualizer = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Cambiar portada") },
                            leadingIcon = { Icon(AuralIcons.Image, null) },
                            onClick = { showMenu = false; showCoverPicker = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar información") },
                            leadingIcon = { Icon(AuralIcons.Pencil, null) },
                            onClick = { showMenu = false; showEditMetadata = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar letra") },
                            leadingIcon = { Icon(AuralIcons.MusicNote, null) },
                            onClick = { showMenu = false; lyricsError = null; showLyricsPicker = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir") },
                            leadingIcon = { Icon(AuralIcons.Share, null) },
                            onClick = { showMenu = false; showShare = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar de la cola") },
                            leadingIcon = { Icon(AuralIcons.MinusCircle, null) },
                            onClick = {
                                showMenu = false
                                if (state.currentIndex >= 0) actions.removeFromQueue(state.currentIndex)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar canción", color = scheme.error) },
                            leadingIcon = {
                                Icon(AuralIcons.Trash, null, tint = scheme.error)
                            },
                            onClick = { showMenu = false; showDeleteConfirm = true }
                        )
                    }
                }
            }

            // Dos columnas: portada + controles a la izquierda, letra
            // completa a la derecha.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    coverBlock(coverSize)
                    Spacer(Modifier.height(16.dp))
                    titleBlock()
                    Spacer(Modifier.height(8.dp))
                    seekBlock()
                    Spacer(Modifier.height(8.dp))
                    transportBlock()
                    Spacer(Modifier.height(16.dp))
                    volumeBlock()
                }
                Spacer(Modifier.width(32.dp))
                Column(
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    lyricsBlock(Modifier.weight(1f))
                }
            }
        }

        // ---- Cola ----
        if (showQueue) {
            QueueSheet(
                queue = state.queue,
                currentIndex = state.currentIndex,
                loadCoverBytes = actions::loadCoverBytes,
                onPlay = { index -> actions.playFrom(state.queue, index) },
                onRemove = { index -> actions.removeFromQueue(index) },
                onMove = { from, to -> actions.moveQueueItem(from, to) },
                onDismiss = { showQueue = false }
            )
        }
    }

    // ---- Diálogos del menú ----
    if (showPlaylists) {
        AddToPlaylistDialog(
            songs = listOf(song),
            playlists = state.playlists,
            onAdd = { id -> actions.addToPlaylist(id, song); showPlaylists = false },
            onCreateAndAdd = { name -> actions.createPlaylistAndAdd(name, song); showPlaylists = false },
            onDismiss = { showPlaylists = false }
        )
    }
    if (showTimer) {
        SleepTimerDialog(
            sleepMinutes = state.sleepMinutes,
            stopMode = state.stopMode,
            onMinutes = { actions.startSleepTimer(it) },
            onStopMode = { actions.setStopMode(it) },
            onCancelTimer = { actions.cancelSleepTimer() },
            onDismiss = { showTimer = false }
        )
    }
    if (showSpeed) {
        SpeedDialog(
            currentSpeed = state.playbackSpeed,
            onSelect = { actions.setPlaybackSpeed(it) },
            onDismiss = { showSpeed = false }
        )
    }
    if (showEqualizer) {
        EqualizerDialog(
            enabled = state.eqEnabled,
            preamp = state.eqPreamp,
            amps = state.eqAmps,
            bandFrequencies = state.eqBandFrequencies,
            onApply = { enabled, preamp, amps -> actions.applyEqualizer(enabled, preamp, amps) },
            onDismiss = { showEqualizer = false }
        )
    }
    if (showCoverPicker) {
        CoverPickerDialog(
            artist = song.artist,
            title = song.title,
            hasCustom = actions.hasCustomCover(song),
            onSelect = { url ->
                scope.launch {
                    if (actions.saveCoverFromUrl(song, url)) {
                        forgetCoverImage(song)
                        onNotice("Portada actualizada")
                    } else {
                        onNotice("No se pudo descargar la portada")
                    }
                }
            },
            onPickFromGallery = {
                if (actions.pickCustomCover(song)) {
                    forgetCoverImage(song)
                    onNotice("Portada actualizada")
                }
            },
            onRemove = {
                actions.removeCustomCover(song)
                forgetCoverImage(song)
                onNotice("Portada quitada")
            },
            onDismiss = { showCoverPicker = false }
        )
    }
    if (showEditMetadata) {
        EditMetadataDialog(
            initialTitle = song.title,
            initialArtist = song.artist,
            initialAlbum = song.album,
            onSave = { title, artist, album ->
                when (actions.editMetadata(song, title, artist, album)) {
                    EditResult.SAVED_FILE -> onNotice("Guardado en el archivo")
                    EditResult.SAVED_OVERLAY -> onNotice("Guardado solo en AuralPlayer")
                    EditResult.FAILED -> onNotice("No se pudo guardar")
                }
            },
            onDismiss = { showEditMetadata = false }
        )
    }
    if (showLyricsPicker) {
        LyricsPickerDialog(
            initialTrack = song.title,
            initialArtist = song.artist,
            isSearching = searchingLyrics,
            errorMessage = lyricsError,
            onSearch = { track, artist ->
                searchingLyrics = true
                lyricsError = null
                scope.launch {
                    val ok = runCatching {
                        actions.searchAndSaveLyrics(song, track, artist)
                    }.getOrDefault(false)
                    searchingLyrics = false
                    if (ok) {
                        showLyricsPicker = false
                        lyricsReload++
                    } else {
                        lyricsError = "No se encontró la letra. Prueba con otro nombre."
                    }
                }
            },
            onPickFile = {
                if (actions.pickLrcFile(song)) {
                    showLyricsPicker = false
                    lyricsReload++
                }
            },
            onDismiss = { showLyricsPicker = false }
        )
    }
    if (showShare) {
        ShareDialog(
            song = song,
            onCopyLink = {
                actions.copyText(AuralLink.shareText(song.title, song.artist))
                onNotice("Enlace copiado")
            },
            onReveal = { actions.revealInExplorer(song.filePath) },
            onDismiss = { showShare = false }
        )
    }
    if (showDeleteConfirm) {
        DeleteSongDialog(
            song = song,
            onConfirm = { actions.deleteSongs(listOf(song)) },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
