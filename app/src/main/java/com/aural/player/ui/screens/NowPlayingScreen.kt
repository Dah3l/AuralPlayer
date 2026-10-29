// Pantalla "Now Playing": carátula grande, controles y cola
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import android.graphics.Bitmap
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.aural.player.data.Song
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aural.player.playback.PlayerUiState
import com.aural.player.playback.RepeatMode
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.formatDuration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Edit
import com.aural.player.ui.components.EqualizerDialog
import com.aural.player.ui.components.CoverPickerDialog
import com.aural.player.ui.components.EditMetadataDialog
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.MusicNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Search
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.media3.common.C

/**
 * Pantalla completa de reproducción con degradado sutil de fondo,
 * carátula grande, barra de seek, controles, favorito y cola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: com.aural.player.AuralViewModel,
    state: PlayerUiState,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lrcMapState by viewModel.lrcMap.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    val song = state.currentSong
    var showQueue by remember { mutableStateOf(false) }
    val queueListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val queueHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Espejo local de la cola: la librería de reorden exige que la lista
    // renderizada cambie en el acto dentro de onMove, y el reproductor aplica
    // el movimiento a su ritmo a través del controlador de Media3.
    var queueDisplay by remember { mutableStateOf(state.queue) }
    val queueReorderState = rememberReorderableLazyListState(
        queueListState,
        scrollThresholdPadding = PaddingValues(bottom = 24.dp)
    ) { from, to ->
        if (from.index in queueDisplay.indices && to.index in queueDisplay.indices && from.index != to.index) {
            queueDisplay = queueDisplay.toMutableList().apply { add(to.index, removeAt(from.index)) }
            onMoveQueueItem(from.index, to.index)
            queueHaptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
            )
        }
    }

    // Resincronizar el espejo cuando la cola cambia por fuera de un arrastre
    // (quitar un tema, saltar de pista, cola nueva…)
    androidx.compose.runtime.LaunchedEffect(state.queue) {
        if (!queueReorderState.isAnyItemDragging && state.queue != queueDisplay) {
            queueDisplay = state.queue
        }
    }

    // Llaves únicas por aparición: la misma canción puede estar dos veces en
    // la cola y la librería exige llaves estables y sin repetir. Se derivan
    // emparejando la lista anterior con la nueva por id, en orden.
    val queueKeyMemory = remember {
        object {
            var songs: List<Song> = emptyList()
            var keys: List<Long> = emptyList()
            var seed: Long = 1L
        }
    }
    val queueKeys: List<Long> = remember(queueDisplay) {
        val pool = queueKeyMemory.songs.mapIndexed { i, s ->
            s.id to queueKeyMemory.keys.getOrElse(i) { 0L }
        }.toMutableList()
        val newKeys = queueDisplay.map { s ->
            val match = pool.indexOfFirst { it.first == s.id }
            if (match >= 0) pool.removeAt(match).second else queueKeyMemory.seed++
        }
        queueKeyMemory.songs = queueDisplay
        queueKeyMemory.keys = newKeys
        newKeys
    }

    // Al abrir la cola, posicionar en la canción actual
    androidx.compose.runtime.LaunchedEffect(showQueue) {
        if (showQueue) {
            queueListState.scrollToItem(state.currentIndex.coerceAtLeast(0))
        }
    }

    // Fondo oscuro con un halo de acento muy suave en la parte superior
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Scrim hermano: bloquea los toques para que no lleguen a la lista
        // detrás. Al estar DETRÁS del contenido (no ser su padre), solo ve
        // lo que nadie usó y JAMÁS compite con los gestos de los hijos
        // (carrusel, slider, botones): el contenido siempre los ve primero.
        // (El bloqueador anterior era padre del carrusel y se tragaba los
        // eventos del swipe: por eso la mayoría de los swipes morían.)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                    }
                }
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // ---- Barra superior: cerrar + indicador ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "REPRODUCIENDO AHORA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    NowPlayingMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        song = song,
                        viewModel = viewModel,
                        state = state,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onShowLyrics = { showLyrics = true }
                    )
                }
            }

            if (song == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "Nada en reproducción",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // ---- Carátula grande: carrusel con efecto por distancia ----
            // En REPOSO es idéntico al diseño anterior: una sola portada
            // centrada y limpia, sin franjas (la transformación por distancia
            // vale 0). En MOVIMIENTO cada portada se escala (mín 0.85) y se
            // atenúa (mín 0.4) según su distancia al centro; el glow del fondo
            // funde entre los colores dominantes; el título/artista se
            // desvanece y cambia al cruzar el 50%.
            // - Ventana [vecinoAnterior?, actual, vecinoSiguiente?] con
            //   índices SIEMPRE del timeline (un solo espacio de índices).
            //   Sin wrap: en los extremos el pager rebota.
            // - La canción cambia SOLO al asentarse el pager en una vecina
            //   (directo, sin regla de 3 s; respeta el shuffle).
            val coverWindow = remember(
                state.currentIndex, state.prevNeighborIndex,
                state.nextNeighborIndex
            ) {
                buildList {
                    val cur = state.currentIndex
                    val prev = state.prevNeighborIndex
                    if (prev != C.INDEX_UNSET && prev != cur && prev >= 0) add(prev)
                    if (cur >= 0) add(cur)
                    val next = state.nextNeighborIndex
                    if (next != C.INDEX_UNSET && next != cur && next >= 0) add(next)
                }
            }
            val coverCenterPage = coverWindow.indexOf(state.currentIndex).coerceAtLeast(0)
            val latestCoverWindow by rememberUpdatedState(coverWindow)
            val latestCenterPage by rememberUpdatedState(coverCenterPage)
            val coverPagerState = rememberPagerState(
                initialPage = coverCenterPage,
                pageCount = { latestCoverWindow.size }
            )
            // Estado fresco para el watcher de asentamiento (ninguna
            // recomposición le deja valores viejos).
            val latestCurrentIndex by rememberUpdatedState(state.currentIndex)
            val latestOnPlayQueueItem by rememberUpdatedState(onPlayQueueItem)

            // Modelo GALERÍA: sin observadores custom de release. El pager
            // sigue el dedo 1:1 con su física nativa; al soltar, si el
            // arrastre/fling lo llevó a una portada vecina, la animación se
            // completa sola (y si no, vuelve sola a su sitio). La canción
            // cambia SOLO cuando el pager SE ASIENTA quieto en la vecina:
            // el deslizamiento de salida/entrada nunca se interrumpe y
            // siempre se ve completo. NUNCA se dispara el cambio con el
            // pager en movimiento.
            LaunchedEffect(coverPagerState) {
                snapshotFlow { coverPagerState.currentPage to coverPagerState.isScrollInProgress }
                    .collect { (page, scrolling) ->
                        if (!scrolling) {
                            val center = latestCenterPage
                            if (page != center) {
                                // Página vecina: resolver su índice del
                                // timeline y cambiar la canción (directo,
                                // sin regla de 3 s; respeta el shuffle).
                                val targetIndex = latestCoverWindow.getOrNull(page)
                                if (targetIndex != null
                                    && targetIndex != C.INDEX_UNSET
                                    && targetIndex != latestCurrentIndex
                                ) {
                                    latestOnPlayQueueItem(targetIndex)
                                } else if (center < latestCoverWindow.size) {
                                    // Red de seguridad: si no hay cambio
                                    // válido, volver al centro.
                                    coverPagerState.animateScrollToPage(center)
                                }
                            }
                        }
                    }
            }

            // Reproductor -> carrusel: si la canción cambia por otra vía
            // (botones, avance automático, aleatorio), reconstruir la
            // ventana y volver a la página central.
            LaunchedEffect(coverWindow, state.currentIndex) {
                val count = latestCoverWindow.size
                if (count > 0) {
                    val target = latestCenterPage.coerceIn(0, count - 1)
                    if (!coverPagerState.isScrollInProgress && coverPagerState.currentPage != target) {
                        coverPagerState.scrollToPage(target)
                    }
                }
            }

            // Canciones de la ventana (actual + vecinas), resueltas una vez
            // por ventana: durante el arrastre la ventana es estable.
            val coverItems = remember(coverWindow, customCovers) {
                coverWindow.map { idx ->
                    viewModel.playback.songAtTimelineIndex(idx)?.let { s ->
                        CoverItem(
                            id = s.id,
                            title = s.title,
                            artist = s.artist,
                            uri = customCovers[s.id] ?: s.albumArtUri
                        )
                    }
                }
            }

            // ---- Color dominante por canción (glow del fondo) ----
            // Cache id -> (uri usada, color): si el usuario cambia la
            // carátula personalizada, la uri cambia y se recalcula.
            val context = LocalContext.current
            val dominantCache = remember { mutableStateMapOf<Long, Pair<String?, Color>>() }
            val fallbackDominant = MaterialTheme.colorScheme.primary
            val paletteTargets = remember(coverItems) {
                coverItems.mapNotNull { it?.let { item -> item.id to item.uri } }
                    .distinctBy { it.first }
            }
            LaunchedEffect(paletteTargets, fallbackDominant) {
                val loader = Coil.imageLoader(context)
                val fallbackArgb = fallbackDominant.toArgb()
                paletteTargets.forEach { (id, uri) ->
                    if (uri.isNullOrBlank()) return@forEach
                    if (dominantCache[id]?.first == uri) return@forEach
                    try {
                        val result = loader.execute(
                            ImageRequest.Builder(context)
                                .data(uri)
                                .allowHardware(false)
                                .build()
                        )
                        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
                        if (bitmap != null) {
                            val small = Bitmap.createScaledBitmap(bitmap, 96, 96, false)
                            val dominant = withContext(Dispatchers.Default) {
                                Palette.from(small).generate()
                                    .getDominantColor(fallbackArgb)
                            }
                            dominantCache[id] = uri to Color(dominant)
                        }
                    } catch (_: Exception) { /* fallback: color primario */ }
                }
            }

            CoverCarousel(
                coverPagerState = coverPagerState,
                coverItems = coverItems,
                coverCenterPage = coverCenterPage,
                currentSongId = song.id,
                dominantColors = dominantCache,
                fallbackDominant = fallbackDominant,
                modifier = Modifier.fillMaxWidth()
            )
            // ---- Línea actual de la letra (debajo de la carátula) ----
            var lrcLines by remember(song?.id) {
                mutableStateOf<List<com.aural.player.data.LrcLine>>(emptyList())
            }
            val lrcPath = song?.let { lrcMapState[it.id] }
            LaunchedEffect(song?.id, lrcPath) {
                lrcLines = if (lrcPath != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val content = java.io.File(lrcPath).readText()
                            com.aural.player.data.LrcParser.parse(content)
                        } catch (e: Exception) { emptyList() }
                    }
                } else emptyList()
            }
            // La letra ocupa el espacio flexible y se centra verticalmente
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (lrcLines.isNotEmpty()) {
                    val currentIdx = com.aural.player.data.LrcParser.currentIndex(
                        lrcLines, state.positionMs
                    )
                    val currentLine = lrcLines.getOrNull(currentIdx)?.text ?: ""
                    if (currentLine.isNotBlank()) {
                        Text(
                            currentLine,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .clickable { showLyrics = true }
                        )
                    }
                }
            }

            // ---- Título + artista + estrella ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CarouselTitleText(
                    coverPagerState = coverPagerState,
                    coverItems = coverItems,
                    coverCenterPage = coverCenterPage,
                    customMetadata = customMetadata,
                    fallbackTitle = song.title,
                    fallbackArtist = song.artist.ifBlank { "Artista desconocido" },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite) {
                    AnimatedContent(
                        targetState = isFavorite,
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = tween(160))) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)))
                        },
                        label = "favorito"
                    ) { fav ->
                        Icon(
                            imageVector = if (fav) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (fav) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---- Barra de progreso ----
            val duration = state.durationMs.coerceAtLeast(1L)
            Slider(
                value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
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
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            // ---- Controles ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Modo: sin repetir → repetir todo → repetir uno → aleatorio
                IconButton(onClick = onCycleRepeat) {
                    val modeKey = when {
                        state.shuffleEnabled -> 3
                        state.repeatMode == RepeatMode.ONE -> 2
                        state.repeatMode == RepeatMode.ALL -> 1
                        else -> 0
                    }
                    AnimatedContent(
                        targetState = modeKey,
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = tween(160))) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)))
                        },
                        label = "modo"
                    ) { mode ->
                        when (mode) {
                            3 -> Icon(Icons.Rounded.Shuffle, "Aleatorio",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                            2 -> Icon(Icons.Rounded.RepeatOne, "Repetir una",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                            1 -> Icon(Icons.Rounded.Repeat, "Repetir todas",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                            else -> Icon(Icons.Rounded.Repeat, "Sin repetir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp))
                        }
                    }
                }
                // Anterior
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                // Play/Pausa (botón principal grande)
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = tween(160))) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)))
                        },
                        label = "play_pause"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause
                            else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                // Siguiente
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                // Cola
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, "Cola",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp))
                }
            }

            Spacer(
                Modifier
                    .height(48.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }

    // ---- Cola como hoja inferior ----
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            // Altura fija: el modal no crece al hacer scroll
            Column(Modifier.fillMaxHeight(0.6f)) {
                Text(
                    "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
                LazyColumn(
                    state = queueListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                itemsIndexed(queueDisplay, key = { index, _ -> queueKeys.getOrElse(index) { index.toLong() } }) { index, item ->
                    ReorderableItem(
                        queueReorderState,
                        key = queueKeys.getOrElse(index) { index.toLong() }
                    ) { isDragging ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isDragging) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .graphicsLayer {
                                    scaleX = if (isDragging) 1.03f else 1f
                                    scaleY = if (isDragging) 1.03f else 1f
                                }
                                .clickable { onPlayQueueItem(index) }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == state.currentIndex)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = customMetadata[item.id]?.first?.ifBlank { item.title } ?: item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (index == state.currentIndex)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = customMetadata[item.id]?.second?.ifBlank { item.artist } ?: item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Arrastrar para reordenar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle()
                                    .size(24.dp)
                            )
                            IconButton(onClick = { onRemoveFromQueue(index) }) {
                                Icon(Icons.Rounded.Close, "Quitar de la cola",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (state.queue.isEmpty()) {
                    item {
                        Text(
                            "La cola está vacía",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            } // fin LazyColumn
        } // fin Column altura fija
    }
    }

    // Overlay de letras sincronizadas
    if (showLyrics) {
        val lrcPath = song?.let { lrcMapState[it.id] }
        if (lrcPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Scrim hermano: bloquea lo que LyricsScreen no consumió
                // (el botón ⋮ está casi en el mismo sitio que el de
                // compartir de la cabecera de letras), sin robarle sus
                // gestos internos.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown().consume()
                            }
                        }
                )
                LyricsScreen(
                    lrcPath = lrcPath,
                    title = song?.let {
                        customMetadata[it.id]?.first?.ifBlank { it.title } ?: it.title
                    } ?: "",
                    artist = song?.let {
                        customMetadata[it.id]?.second?.ifBlank { it.artist } ?: it.artist
                    } ?: "",
                    coverUri = song?.let { customCovers[it.id] ?: it.albumArtUri },
                    currentPositionMs = { state.positionMs },
                    onBack = { showLyrics = false }
                )
            }
        } else {
            showLyrics = false
        }
    }
}

/**
 * Menú de opciones del Now Playing: agregar a playlist, temporizador,
 * velocidad, compartir y eliminar de la cola.
 */
@Composable
private fun NowPlayingMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: com.aural.player.data.Song?,
    viewModel: com.aural.player.AuralViewModel,
    state: com.aural.player.playback.PlayerUiState,
    onRemoveFromQueue: (Int) -> Unit,
    onShowLyrics: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()

    // Canción con metadatos personalizados aplicados
    val effectiveSong = song?.let { s ->
        val meta = customMetadata[s.id]
        if (meta != null) s.copy(
            title = meta.first.ifBlank { s.title },
            artist = meta.second.ifBlank { s.artist },
            album = meta.third.ifBlank { s.album }
        ) else s
    }

    var showPlaylists by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showTimer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSpeed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLrcPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLyricsSearch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showEqualizer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showCoverPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showEditMetadata by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            song?.let { s -> viewModel.setCustomCover(s.id, it.toString()) }
        }
        showCoverPicker = false
    }
    var isSearchingLyrics by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var lyricsSearchError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var lrcFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var lrcLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lrcMap by viewModel.lrcMap.collectAsStateWithLifecycle()
    val hasLrc = song?.let { lrcMap.containsKey(it.id) } ?: false
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        DropdownMenuItem(
            text = { Text("Agregar a la lista de reproducción") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
            onClick = { onDismiss(); showPlaylists = true }
        )
        DropdownMenuItem(
            text = { Text(if (sleepMinutes != null) "Temporizador: ${sleepMinutes} min" else "Temporizador de suspensión") },
            leadingIcon = { Icon(Icons.Rounded.Timer, null) },
            onClick = { onDismiss(); showTimer = true }
        )
        DropdownMenuItem(
            text = { Text("Velocidad de reproducción") },
            leadingIcon = { Icon(Icons.Rounded.Speed, null) },
            onClick = { onDismiss(); showSpeed = true }
        )
        DropdownMenuItem(
            text = { Text("Ecualizador") },
            leadingIcon = { Icon(Icons.Rounded.Tune, null) },
            onClick = { onDismiss(); showEqualizer = true }
        )
        DropdownMenuItem(
            text = { Text("Cambiar portada") },
            leadingIcon = { Icon(Icons.Rounded.Image, null) },
            onClick = { onDismiss(); showCoverPicker = true }
        )
        DropdownMenuItem(
            text = { Text("Editar información") },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); showEditMetadata = true }
        )
        DropdownMenuItem(
            text = { Text("Compartir") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = {
                onDismiss()
                song?.let {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(it.uri))
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, "Compartir canción")
                    )
                }
            }
        )
        DropdownMenuItem(
            text = { Text(if (hasLrc) "Ver letra" else "Agregar letra") },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
            onClick = {
                onDismiss()
                if (hasLrc) {
                    onShowLyrics()
                } else {
                    showLrcPicker = true
                    lrcLoading = true
                    scope.launch {
                        lrcFiles = viewModel.scanLrcFiles()
                        lrcLoading = false
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Buscar letra en línea") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            onClick = {
                onDismiss()
                lyricsSearchError = null
                showLyricsSearch = true
            }
        )
        DropdownMenuItem(
            text = { Text("Eliminar de la cola") },
            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
            onClick = {
                onDismiss()
                song?.let { s ->
                    val idx = state.queue.indexOfFirst { it.id == s.id }
                    if (idx >= 0) onRemoveFromQueue(idx)
                }
            }
        )
    }

    // Diálogo del ecualizador
    if (showEqualizer) {
        EqualizerDialog(onDismiss = { showEqualizer = false })
    }

    // Diálogo: editar metadatos
    if (showEditMetadata && song != null) {
        val meta = customMetadata[song.id]
        EditMetadataDialog(
            initialTitle = meta?.first ?: song.title,
            initialArtist = meta?.second ?: song.artist,
            initialAlbum = meta?.third ?: song.album,
            onSave = { title, artist, album ->
                viewModel.setCustomMetadata(song.id, title, artist, album)
                showEditMetadata = false
            },
            onDismiss = { showEditMetadata = false }
        )
    }

    // Diálogo: cambiar portada
    if (showCoverPicker && effectiveSong != null) {
        CoverPickerDialog(
            artist = effectiveSong.artist,
            title = effectiveSong.title,
            hasCustomCover = customCovers.containsKey(effectiveSong.id),
            onSelect = { coverUrl ->
                viewModel.setCustomCover(effectiveSong.id, coverUrl)
                showCoverPicker = false
            },
            onPickFromGallery = {
                galleryLauncher.launch("image/*")
            },
            onRemove = {
                viewModel.setCustomCover(effectiveSong.id, "")
                showCoverPicker = false
            },
            onDismiss = { showCoverPicker = false }
        )
    }

    // Diálogo: elegir playlist
    if (showPlaylists && song != null) {
        AlertDialog(
            onDismissRequest = { showPlaylists = false },
            title = { Text("Agregar a playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                viewModel.addToPlaylist(pl.id, song.id)
                                showPlaylists = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pl.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (playlists.isEmpty()) {
                        Text("No tienes playlists. Créalas en la pestaña Playlists.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylists = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo: temporizador
    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Temporizador de suspensión") },
            text = {
                Column {
                    listOf(15, 30, 60, 90).forEach { mins ->
                        TextButton(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins minutos", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (sleepMinutes != null) {
                        TextButton(
                            onClick = {
                                viewModel.cancelSleepTimer()
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar temporizador",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimer = false }) { Text("Cerrar") }
            }
        )
    }

    // Diálogo: velocidad
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Velocidad de reproducción") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(
                            onClick = {
                                viewModel.playback.setPlaybackSpeed(speed)
                                showSpeed = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = if (speed == 1f) "1x (Normal)" else "${speed}x"
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeed = false }) { Text("Cerrar") }
            }
        )
    }

    // Diálogo buscar letra en línea
    if (showLyricsSearch) {
        com.aural.player.ui.components.LyricsSearchDialog(
            initialTrack = effectiveSong?.title ?: "",
            initialArtist = effectiveSong?.artist ?: "",
            isSearching = isSearchingLyrics,
            errorMessage = lyricsSearchError,
            onDismiss = { showLyricsSearch = false },
            onSearch = { track, artist ->
                isSearchingLyrics = true
                lyricsSearchError = null
                scope.launch {
                    val found = song?.let {
                        viewModel.searchAndSaveLyrics(it.id, track, artist)
                    } ?: false
                    isSearchingLyrics = false
                    if (found) {
                        showLyricsSearch = false
                        onShowLyrics()
                    } else {
                        lyricsSearchError = "No se encontró la letra. Prueba con otro nombre."
                    }
                }
            }
        )
    }

    // Diálogo selector de .lrc
    if (showLrcPicker) {
        com.aural.player.ui.components.LrcPickerDialog(
            lrcFiles = lrcFiles,
            isLoading = lrcLoading,
            onDismiss = { showLrcPicker = false },
            onSelect = { lrcPath ->
                song?.let { viewModel.setLrcForSong(it.id, lrcPath) }
                showLrcPicker = false
            }
        )
    }
}


/** Datos mínimos de cada página del carrusel de carátulas. */
private data class CoverItem(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String?
)

/**
 * Carrusel de carátulas del Now Playing.
 *
 * En REPOSO muestra UNA portada centrada y limpia, idéntica al diseño
 * anterior (la transformación por distancia vale 0). Durante el ARRASTRE
 * cada portada se escala (mín 0.85) y atenúa (mín 0.4) según su distancia
 * al centro, y el glow del fondo funde entre el color dominante de la
 * actual y el de la portada entrante.
 *
 * Solo este composable se recompone durante el arrastre (lee el offset
 * del pager); el resto de la pantalla no se entera.
 */
@Composable
private fun CoverCarousel(
    coverPagerState: PagerState,
    coverItems: List<CoverItem?>,
    coverCenterPage: Int,
    currentSongId: Long,
    dominantColors: Map<Long, Pair<String?, Color>>,
    fallbackDominant: Color,
    modifier: Modifier = Modifier
) {
    // Lecturas del scroll: limitadas a este scope.
    val currentPage = coverPagerState.currentPage
    val offsetFraction = coverPagerState.currentPageOffsetFraction
    fun distanceToCenter(page: Int): Float =
        abs((currentPage - page) + offsetFraction).coerceIn(0f, 1f)

    // Glow: en reposo, el dominante de la actual. Durante el arrastre
    // funde hacia el dominante de la portada entrante (la vecina más
    // cercana al centro, distinta de la actual).
    val currentDominant = dominantColors[currentSongId]?.second ?: fallbackDominant
    var incomingPage = -1
    var incomingD = Float.MAX_VALUE
    for (p in coverItems.indices) {
        if (p == coverCenterPage) continue
        val d = distanceToCenter(p)
        if (d < incomingD) {
            incomingD = d
            incomingPage = p
        }
    }
    val glowColor = if (incomingPage < 0) {
        currentDominant
    } else {
        val incomingDominant = coverItems.getOrNull(incomingPage)?.id
            ?.let { dominantColors[it]?.second } ?: currentDominant
        lerp(currentDominant, incomingDominant, (1f - incomingD).coerceIn(0f, 1f))
    }

    BoxWithConstraints(
        modifier
            .padding(top = 8.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.18f),
                        glowColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    radius = 600f
                )
            )
    ) {
        val pageWidth = maxWidth
        // Margen lateral para que la sombra de la portada (24 dp) nunca se
        // recorte en los bordes del viewport.
        val coverSideMargin = 32.dp
        HorizontalPager(
            state = coverPagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            // Transform por distancia al centro (patrón oficial de carrusel
            // con Pager): en reposo d=0 → escala 1 y alpha 1, idéntico al
            // diseño anterior.
            val d = abs((coverPagerState.currentPage - page) + coverPagerState.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            val item = coverItems.getOrNull(page)
            // La página ocupa todo el ancho; la portada va centrada con
            // margen lateral para que su sombra NUNCA se recorte en los
            // bordes del viewport.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageWidth)
                    .graphicsLayer {
                        val s = 1f - 0.15f * d
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.6f * d
                    },
                contentAlignment = Alignment.Center
            ) {
                com.aural.player.ui.components.CoverArt(
                    uri = item?.uri,
                    size = pageWidth - coverSideMargin * 2,
                    modifier = Modifier.shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    cornerRadius = 16.dp
                )
            }
        }
    }
}

/**
 * Título + artista del Now Playing con comportamiento de carrusel: en
 * reposo muestra la canción en reproducción; durante el arrastre se
 * desvanece (alpha = 1 - progreso) y el texto cambia a la canción de la
 * página más cercana al centro (cruza al 50%).
 *
 * Solo este composable se recompone durante el arrastre. La letra
 * sincronizada sigue atada a la canción real en reproducción.
 */
@Composable
private fun CarouselTitleText(
    coverPagerState: PagerState,
    coverItems: List<CoverItem?>,
    coverCenterPage: Int,
    customMetadata: Map<Long, Triple<String, String, String>>,
    fallbackTitle: String,
    fallbackArtist: String,
    modifier: Modifier = Modifier
) {
    val offsetFraction = coverPagerState.currentPageOffsetFraction
    // Página más cercana al centro: el texto cambia al cruzar el 50%.
    var nearestPage = coverCenterPage
    var nearestD = Float.MAX_VALUE
    for (p in coverItems.indices) {
        val d = abs((coverPagerState.currentPage - p) + offsetFraction)
        if (d < nearestD) {
            nearestD = d
            nearestPage = p
        }
    }
    val item = coverItems.getOrNull(nearestPage)
    val title = item?.let { customMetadata[it.id]?.first?.ifBlank { it.title } ?: it.title }
        ?: fallbackTitle
    val artist = item?.let {
        customMetadata[it.id]?.second?.ifBlank { it.artist.ifBlank { "Artista desconocido" } }
            ?: it.artist.ifBlank { "Artista desconocido" }
    } ?: fallbackArtist
    Column(modifier.alpha(1f - abs(offsetFraction).coerceIn(0f, 1f))) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 2000
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
