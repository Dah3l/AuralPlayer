// Pantalla "Now Playing": carátula grande, controles y cola
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.Color as AColor
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.pow
import java.io.File
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.aural.player.data.LrcParser
import com.aural.player.data.Song
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aural.player.playback.PlayerUiState
import com.aural.player.playback.RepeatMode
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.HalloweenGhosts
import com.aural.player.ui.components.formatDuration
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.theme.HalloweenPumpkin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Edit
import com.aural.player.ui.components.CoverPickerDialog
import com.aural.player.ui.components.EditMetadataDialog
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.MusicNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.LaunchedEffect
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
    onOpenEqualizer: () -> Unit = {},
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lrcMapState by viewModel.lrcMap.collectAsStateWithLifecycle()
    val customCovers by viewModel.customCovers.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()
    // Decoración Halloween: solo si el tema activo es Halloween (tema real del menú).
    val themeName by viewModel.appTheme.collectAsStateWithLifecycle()
    val isHalloweenTheme = themeName == AppTheme.HALLOWEEN.name
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

    // Fondo ambiental (v1.0.8): mesh gradient con los colores de la
    // portada (o del tema si no hay), manchas con deriva lenta + velo
    // del fondo del tema para que el texto se lea + grano anti-bandas.
    // Sin blur por hardware: igual en todos los dispositivos (API 26+).
    // Los contenedores de dentro (Column, pager, letras) no pintan
    // fondo: son transparentes y no interrumpen el efecto.
    // La paleta se calcula una sola vez aquí: el mesh y el glow de la
    // portada usan los mismos colores, así el glow nunca desentona
    // con el fondo.
    val currentArtUri = song?.let { customCovers[it.id] ?: it.albumArtUri }
    val glassPalette = rememberGlassPalette(currentArtUri)
    Box(Modifier.fillMaxSize()) {
        NowPlayingAmbientBackground(
            palette = glassPalette,
            modifier = Modifier.fillMaxSize()
        )
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
        // Fantasmas (tema Halloween activo): al fondo, dibujo puro.
        // Navidad (diciembre): enganchar decoración navideña con theme == CHRISTMAS.
        if (isHalloweenTheme) {
            HalloweenGhosts(Modifier.fillMaxSize())
        }
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
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar",
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
                        onShowLyrics = { showLyrics = true },
                        onOpenEqualizer = onOpenEqualizer
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
                                    // Volver al centro ANTES de cambiar la canción:
                                    // el salto es instantáneo e invisible (la página
                                    // central de la ventana nueva muestra la misma
                                    // canción que la página asentada de la vieja).
                                    // Sin esto hay un frame con el pager en la página
                                    // vieja pero ya con la ventana nueva -> flash de
                                    // la canción vecina en título y portada.
                                    coverPagerState.scrollToPage(center)
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

            CoverCarousel(
                coverPagerState = coverPagerState,
                coverItems = coverItems,
                glowColor = glassPalette.firstOrNull() ?: MaterialTheme.colorScheme.primary,
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
                    .height(64.dp)
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
                modifier = Modifier.fillMaxSize()
            ) {
                // Mesh ambiental con la paleta de la portada (la misma
                // del Now Playing): la ventana de letras vive en el
                // mismo mundo visual.
                NowPlayingAmbientBackground(
                    palette = glassPalette,
                    modifier = Modifier.fillMaxSize()
                )
                // Velo extra de la ventana de letras: el fondo queda
                // más tapado que en el Now Playing para que las líneas
                // se lean sin esfuerzo; los colores del mesh asoman
                // suave en los bordes.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                )
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
    onShowLyrics: () -> Unit,
    onOpenEqualizer: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val stopMode by viewModel.stopMode.collectAsStateWithLifecycle()
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
    var showLyricsPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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

    // v1.0.9: eliminar la canción actual (borra el archivo del dispositivo).
    var showDeleteConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var pendingDeleteId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val id = pendingDeleteId
        if (result.resultCode == android.app.Activity.RESULT_OK && id != null) {
            viewModel.confirmDelete(listOf(id))
            android.widget.Toast.makeText(context, "Canción eliminada", android.widget.Toast.LENGTH_SHORT).show()
        }
        pendingDeleteId = null
    }

    // v1.0.9: hoja única de compartir + hoja de la Aural Card.
    var showShareSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showCardSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var cardLines by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    var cardLinkDefaultOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Abre la hoja de la Aural Card: lee y parsea la letra (.lrc) de la canción.
    // `withLink` = la tarjeta acompaña al Aural link (su interruptor nace encendido).
    val openCardSheet: (Boolean) -> Unit = { withLink ->
        val s = song
        if (s == null) {
            android.widget.Toast.makeText(context, "Sin canción", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                val lines = withContext(Dispatchers.IO) {
                    try {
                        val path = lrcMap[s.id] ?: return@withContext emptyList<String>()
                        LrcParser.parse(File(path).readText()).map { it.text }.filter { it.isNotBlank() }
                    } catch (e: Exception) {
                        emptyList<String>()
                    }
                }
                if (lines.isEmpty()) {
                    android.widget.Toast.makeText(context, "No se pudo leer la letra", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    cardLines = lines
                    cardLinkDefaultOn = withLink
                    showCardSheet = true
                }
            }
        }
    }

    // Comparte la tarjeta "Escuchando ahora" (portada + canción).
    // La usan el ítem del menú y la hoja de compartir.
    fun shareNowPlaying() {
        val s = effectiveSong ?: song
        if (s == null) {
            android.widget.Toast.makeText(
                context, "Sin canción", android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            scope.launch {
                try {
                    val cover = loadCardCoverBitmap(
                        context,
                        customCovers[s.id] ?: s.albumArtUri
                    )
                    val bitmap = captureCard(context) {
                        NowPlayingCardContent(
                            title = s.title,
                            artist = s.artist,
                            coverBitmap = cover
                        )
                    }
                    shareCardImage(
                        context = context,
                        bitmap = bitmap,
                        fileName = "aural-escuchando.png",
                        chooserTitle = "Compartir escuchando ahora"
                    )
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context, "No se pudo compartir",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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
            text = {
                Text(
                    when {
                        sleepMinutes != null -> "Temporizador: ${sleepMinutes} min"
                        stopMode == com.aural.player.playback.StopMode.SONG ->
                            "Temporizador: al terminar la canción"
                        stopMode == com.aural.player.playback.StopMode.QUEUE ->
                            "Temporizador: al terminar la lista"
                        else -> "Temporizador de suspensión"
                    }
                )
            },
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
            onClick = { onDismiss(); onOpenEqualizer() }
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
            text = { Text("Agregar letra") },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
            onClick = {
                onDismiss()
                lyricsSearchError = null
                showLyricsPicker = true
                lrcLoading = true
                scope.launch {
                    lrcFiles = viewModel.scanLrcFiles()
                    lrcLoading = false
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Compartir") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = { onDismiss(); showShareSheet = true }
        )
        DropdownMenuItem(
            text = { Text("Compartir escuchando ahora") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = { onDismiss(); shareNowPlaying() }
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
        DropdownMenuItem(
            text = { Text("Eliminar canción", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                song?.let {
                    pendingDeleteId = it.id
                    showDeleteConfirm = true
                }
            }
        )
    }

    // v1.0.9: hoja única de compartir (archivo / Aural link / Aural Card).
    if (showShareSheet && song != null) {
        ShareSheet(
            title = effectiveSong?.title ?: song.title,
            artist = effectiveSong?.artist ?: song.artist,
            coverUri = customCovers[song.id] ?: song.albumArtUri,
            hasLrc = hasLrc,
            onShareFile = {
                showShareSheet = false
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(song.uri))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(shareIntent, "Compartir canción")
                )
            },
            onShareLink = { withCard ->
                showShareSheet = false
                if (withCard) {
                    openCardSheet(true)
                } else {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, com.aural.player.AuralLink.shareText(effectiveSong ?: song, customMetadata[song.id]))
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, "Compartir Aural link")
                    )
                }
            },
            onShareCard = {
                showShareSheet = false
                openCardSheet(false)
            },
            onShareNowPlaying = {
                showShareSheet = false
                shareNowPlaying()
            },
            onDismiss = { showShareSheet = false }
        )
    }

    // Hoja de la Aural Card (se abre desde el panel de compartir).
    if (showCardSheet && song != null) {
        LyricCardSheet(
            title = effectiveSong?.title ?: song.title,
            artist = effectiveSong?.artist ?: song.artist,
            coverUri = customCovers[song.id] ?: song.albumArtUri,
            lines = cardLines,
            onDismiss = { showCardSheet = false },
            linkText = com.aural.player.AuralLink.shareText(effectiveSong ?: song, customMetadata[song.id]),
            linkDefaultOn = cardLinkDefaultOn
        )
    }

    // (v1.0.7: el ecualizador ahora es pantalla propia, se abre con onOpenEqualizer)

    // v1.0.9: confirmación antes de borrar el archivo de la canción.
    if (showDeleteConfirm && song != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteId = null },
            title = { Text("Eliminar canción") },
            text = {
                Text("¿Eliminar \"${effectiveSong?.title ?: song.title}\"? Se borrará el archivo del dispositivo.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    val id = pendingDeleteId
                    if (id != null) {
                        val request = viewModel.buildDeleteRequest(listOf(id))
                        if (request != null) {
                            deleteLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                            )
                        } else {
                            // API <30: el borrado directo ya se hizo en el ViewModel.
                            android.widget.Toast.makeText(context, "Canción eliminada", android.widget.Toast.LENGTH_SHORT).show()
                            pendingDeleteId = null
                        }
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false; pendingDeleteId = null }) { Text("Cancelar") }
            }
        )
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
                    TextButton(
                        onClick = {
                            viewModel.setStopMode(com.aural.player.playback.StopMode.SONG)
                            showTimer = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Al terminar la canción", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            viewModel.setStopMode(com.aural.player.playback.StopMode.QUEUE)
                            showTimer = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Al terminar la lista", modifier = Modifier.fillMaxWidth())
                    }
                    if (sleepMinutes != null || stopMode != null) {
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

    // Diálogo unificado para agregar letra: en línea primero, local después.
    if (showLyricsPicker) {
        LyricsPickerDialog(
            initialTrack = effectiveSong?.title ?: "",
            initialArtist = effectiveSong?.artist ?: "",
            isSearching = isSearchingLyrics,
            errorMessage = lyricsSearchError,
            lrcFiles = lrcFiles,
            lrcLoading = lrcLoading,
            onDismiss = { showLyricsPicker = false },
            onSearch = { track, artist ->
                isSearchingLyrics = true
                lyricsSearchError = null
                scope.launch {
                    val found = song?.let {
                        viewModel.searchAndSaveLyrics(it.id, track, artist, it.durationMs)
                    } ?: false
                    isSearchingLyrics = false
                    if (found) {
                        showLyricsPicker = false
                        onShowLyrics()
                    } else {
                        lyricsSearchError = "No se encontró la letra. Prueba con otro nombre."
                    }
                }
            },
            onSelectLrc = { lrcPath ->
                song?.let { viewModel.setLrcForSong(it.id, lrcPath) }
                showLyricsPicker = false
            }
        )
    }
}

/**
 * Hoja única de compartir (v1.0.9): el menú del reproductor tiene una sola
 * opción "Compartir" y aquí salen las tres formas: archivo, Aural link y
 * Aural Card. El link y la card se pueden combinar: el link con su
 * interruptor abre la tarjeta (imagen + enlace en un solo envío), y la
 * tarjeta lleva su propio interruptor para añadir el link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    title: String,
    artist: String,
    coverUri: String?,
    hasLrc: Boolean,
    onShareFile: () -> Unit,
    onShareLink: (withCard: Boolean) -> Unit,
    onShareCard: () -> Unit,
    onShareNowPlaying: () -> Unit,
    onDismiss: () -> Unit
) {
    var linkWithCard by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Cabecera: portada + título + artista de la canción
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(uri = coverUri, size = 56.dp, cornerRadius = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            // Compartir el archivo de audio
            ShareRow(
                icon = Icons.Rounded.Audiotrack,
                title = "Compartir archivo",
                subtitle = "Envía el archivo de audio",
                onClick = onShareFile
            )
            // Compartir el Aural link, solo o con la tarjeta
            ShareRow(
                icon = Icons.Rounded.Link,
                title = "Compartir Aural link",
                subtitle = "Un enlace que abre la canción en AuralPlayer",
                onClick = { onShareLink(linkWithCard) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Con Aural Card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasLrc) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = linkWithCard,
                    onCheckedChange = { linkWithCard = it },
                    enabled = hasLrc
                )
            }
            // Compartir la Aural Card (tarjeta con la letra)
            ShareRow(
                icon = Icons.Rounded.Image,
                title = "Compartir Aural Card",
                subtitle = if (hasLrc) "Una imagen con la letra y la marca Aural"
                else "Añade una letra para crear la tarjeta",
                enabled = hasLrc,
                onClick = onShareCard
            )
            // Compartir "Escuchando ahora" (tarjeta con portada y canción)
            ShareRow(
                icon = Icons.Rounded.MusicNote,
                title = "Compartir escuchando ahora",
                subtitle = "Una imagen con la portada y la canción que suena",
                onClick = onShareNowPlaying
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Una fila del panel de compartir: icono + título + subtítulo. */
@Composable
private fun ShareRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Diálogo unificado para agregar letra (v1.0.9): en el menú del reproductor
 * queda una sola opción "Agregar letra" y aquí salen las dos vías juntas:
 * primero buscar en línea y después elegir un .lrc del teléfono.
 */
@Composable
private fun LyricsPickerDialog(
    initialTrack: String,
    initialArtist: String,
    isSearching: Boolean,
    errorMessage: String?,
    lrcFiles: List<Pair<String, String>>,
    lrcLoading: Boolean,
    onDismiss: () -> Unit,
    onSearch: (track: String, artist: String) -> Unit,
    onSelectLrc: (String) -> Unit
) {
    var track by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialTrack) }
    var artist by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(initialArtist.takeUnless { it == "Artista misterioso" } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar letra") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // 1) En línea
                Text(
                    text = "En línea",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = track,
                    onValueChange = { track = it },
                    label = { Text("Canción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching
                )
                Spacer(Modifier.height(8.dp))
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    Button(
                        onClick = { onSearch(track.trim(), artist.trim()) },
                        enabled = track.isNotBlank() && artist.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buscar en línea")
                    }
                }
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                // 2) En el teléfono
                Text(
                    text = "En el teléfono",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (lrcLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (lrcFiles.isEmpty()) {
                    Text(
                        "No se encontraron archivos .lrc en el dispositivo. " +
                        "Coloca el archivo .lrc en tu carpeta de música.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${lrcFiles.size} archivos encontrados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(lrcFiles) { (name, path) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectLrc(path) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSearching) { Text("Cancelar") }
        }
    )
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
// ---------------------------------------------------------------------------
// Glow de la carátula (código de Claude): halo de color difuminado hecho
// con capas concéntricas, sin blur ni shadow. Evita lo que hacía ver
// turbio al anterior: Modifier.shadow mezcla negro ambiental con el
// color; aquí todas las capas son del color del tema, sin negro.
// Va ANTES del clip: el glow se dibuja fuera de la carátula recortada y
// el centro queda tapado por la propia imagen. Extensión máxima 19dp
// (16 + 3 de offset) <= 20dp de margen de la página: el pager no lo
// puede recortar. Solo drawRoundRect: funciona desde API 26.
// ---------------------------------------------------------------------------
fun Modifier.artworkGlow(
    color: Color,
    cornerRadius: Dp = 16.dp,
    extent: Dp = 16.dp,
    yOffset: Dp = 3.dp,
    strength: Float = 0.45f, // opacidad acumulada junto al borde
    layers: Int = 14,
): Modifier = drawBehind {
    val ext = extent.toPx()
    val dy = yOffset.toPx()
    val r0 = cornerRadius.toPx()
    fun profile(x: Float) = strength * (1f - x).pow(1.5f) // caída suave hasta 0

    for (i in layers downTo 1) {
        val grow = ext * i / layers
        val alpha = (profile((i - 1f) / layers) - profile(i.toFloat() / layers)).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-grow, -grow + dy),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(r0 + grow),
        )
    }
}

// ---------------------------------------------------------------------------
// Fondo mesh del Now Playing (adaptación del diseño de Claude): se extraen
// hasta 3 colores dominantes de la portada (sin Palette) y se dibujan como
// manchas radiales con deriva lenta. Sin blur por hardware: funciona desde
// API 26. Sin portada, o portada en blanco y negro, entran los colores
// del tema. Los colores se animan 700 ms al cambiar de canción.
// ---------------------------------------------------------------------------

/** Fuerza un color a vivo: saturación 0.55-0.95 y brillo 0.65-0.95. */
private fun vivid(rgb: Int): Color {
    val hsv = FloatArray(3).also { AColor.colorToHSV(rgb, it) }
    hsv[1] = hsv[1].coerceIn(0.55f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.65f, 0.95f)
    return Color(AColor.HSVToColor(hsv))
}

/** Hasta 3 colores dominantes con tonos distintos. Lista vacía si la portada es gris/sin color. */
private fun extractGlassColors(bitmap: Bitmap): List<Color> {
    val n = 32
    val px = IntArray(n * n)
    Bitmap.createScaledBitmap(bitmap, n, n, true).getPixels(px, 0, n, 0, 0, n, n)

    val w = FloatArray(12); val r = FloatArray(12); val g = FloatArray(12); val b = FloatArray(12)
    val hsv = FloatArray(3)
    for (p in px) {
        AColor.colorToHSV(p, hsv)
        if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue // ignora grises y negros
        val k = (hsv[0] / 30f).toInt().coerceAtMost(11)
        val wt = hsv[1] * hsv[2]
        w[k] += wt; r[k] += AColor.red(p) * wt; g[k] += AColor.green(p) * wt; b[k] += AColor.blue(p) * wt
    }

    val chosen = mutableListOf<Int>()
    for (k in (0 until 12).filter { w[it] > 0.5f }.sortedByDescending { w[it] }) {
        val tooClose = chosen.any { val d = kotlin.math.abs(it - k); minOf(d, 12 - d) < 2 }
        if (!tooClose) chosen += k
        if (chosen.size == 3) break
    }
    return chosen.map { vivid(AColor.rgb((r[it] / w[it]).toInt(), (g[it] / w[it]).toInt(), (b[it] / w[it]).toInt())) }
}

/** Paleta del mesh: colores de la portada (si hay 2+) completados con el tema. */
@Composable
private fun rememberGlassPalette(artwork: Any?): List<Color> {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var fromArt by remember(artwork) { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(artwork) {
        fromArt = if (artwork == null) emptyList() else try {
            withContext(Dispatchers.Default) {
                val req = ImageRequest.Builder(ctx).data(artwork).size(64).allowHardware(false).build()
                val drawable = (Coil.imageLoader(ctx).execute(req) as? SuccessResult)?.drawable
                drawable?.let { d ->
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 64
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 64
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    d.setBounds(0, 0, w, h)
                    d.draw(canvas)
                    extractGlassColors(bmp)
                } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val theme = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    val target = if (fromArt.size >= 2) (fromArt + theme).take(3) else theme

    return target.map { animateColorAsState(it, tween(700), label = "glassColor").value }
}

/**
 * Fondo ambiental del Now Playing: mesh gradient con los colores de la
 * portada (o del tema) + velo del fondo del tema + grano anti-bandas.
 * Sin blur por hardware: igual en todos los dispositivos desde API 26.
 * La paleta viene de fuera (la calcula el Now Playing): el glow de la
 * portada usa estos mismos colores.
 */
@Composable
private fun NowPlayingAmbientBackground(
    palette: List<Color>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // luminance() de Compose no existe en esta versión: se usa el de Android.
    val isDark = android.graphics.Color.luminance(scheme.background.toArgb()) < 0.5f
    val blobAlpha = if (isDark) 0.85f else 0.65f
    val phase by rememberInfiniteTransition(label = "mesh").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(28_000, easing = LinearEasing)), label = "phase",
    )
    // Grano aleatorio 128x128 (semilla fija), en mosaico al 3%: más
    // suave que antes, suficiente para romper las bandas del degradado.
    val noiseBitmap = remember {
        val bmp = android.graphics.Bitmap.createBitmap(
            128, 128, android.graphics.Bitmap.Config.ARGB_8888
        )
        val rnd = kotlin.random.Random(42)
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val g = rnd.nextInt(256)
                bmp.setPixel(x, y, android.graphics.Color.argb(255, g, g, g))
            }
        }
        bmp.asImageBitmap()
    }
    val noisePaint = remember(noiseBitmap) {
        Paint().apply {
            shader = ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated)
            alpha = 0.03f
        }
    }
    Box(modifier) {
        // Base opaca: el fondo del tema.
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
        )
        // Mesh: 4 manchas de la paleta con deriva lenta. La fase solo
        // se lee en la fase de dibujo: no hay recomposición.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val d = size.minDimension * 0.06f
                    // (x, y, radio, índice de color, desfase)
                    val blobs = listOf(
                        floatArrayOf(0.25f, 0.20f, 0.75f, 0f, 0f),
                        floatArrayOf(0.90f, 0.45f, 0.70f, 2f, 1.7f),
                        floatArrayOf(0.20f, 0.80f, 0.70f, 1f, 3.1f),
                        floatArrayOf(0.85f, 0.95f, 0.60f, 0f, 4.4f),
                    )
                    blobs.forEach { (x, y, rad, ci, off) ->
                        val c = palette[ci.toInt()]
                        val center = Offset(
                            size.width * x + kotlin.math.sin(phase + off) * d,
                            size.height * y + kotlin.math.cos(phase + off) * d,
                        )
                        drawRect(
                            Brush.radialGradient(
                                listOf(
                                    c.copy(alpha = blobAlpha),
                                    c.copy(alpha = blobAlpha * 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.maxDimension * rad,
                            )
                        )
                    }
                }
        )
        // Velo suave del tema: protege la legibilidad del texto sin
        // apagar el mesh (más opaco arriba y abajo, ligero en el centro).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.background.copy(alpha = 0.55f),
                            scheme.background.copy(alpha = 0.30f),
                            scheme.background.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        // Grano sutil sobre el ambiente (debajo del contenido).
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawContext.canvas.drawRect(
                        0f, 0f, size.width, size.height, noisePaint
                    )
                }
        )
    }
}

@Composable
private fun CoverCarousel(
    coverPagerState: PagerState,
    coverItems: List<CoverItem?>,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .padding(top = 8.dp)
    ) {
        val pageWidth = maxWidth
        // El fondo ambiental ya vive en la raíz del Now Playing (mesh
        // con los colores de la portada): aquí solo queda el carrusel
        // con su glow.
        // Margen lateral de la portada.
        val coverSideMargin = 20.dp
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
            // La página ocupa todo el ancho; la portada va centrada.
            // El resplandor se dibuja A MANO detrás de la tarjeta y vive
            // DENTRO de la página: nada lo puede recortar jamás (la sombra
            // del sistema se salía de los bordes y el pager la cortaba).
            // Al estar dentro del graphicsLayer, escala y se atenúa junto
            // con la portada durante el arrastre.
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
                // Glow con el color dominante de la portada (el mismo
                // del mesh): nunca desentona con el fondo. Más sutil y
                // más pequeño (10dp, 0.30): se nota sin imponerse. El
                // pager no lo recorta porque cabe en el margen de 20dp.
                com.aural.player.ui.components.CoverArt(
                    uri = item?.uri,
                    size = pageWidth - coverSideMargin * 2,
                    cornerRadius = 16.dp,
                    showBorder = false,
                    modifier = Modifier.artworkGlow(
                        color = glowColor,
                        cornerRadius = 16.dp,
                        extent = 10.dp,
                        strength = 0.30f
                    )
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
