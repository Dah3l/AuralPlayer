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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
            // Bloquea toques para que no lleguen a la lista detrás.
            // Solo consume lo que los hijos NO consumieron: los hijos
            // (botones, slider, letra clicable, carrusel) ven cada evento
            // primero y sin consumir, así sus gestos no se roban; lo que
            // nadie usó se consume aquí y no baja a la lista detrás.
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (!it.isConsumed) it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
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

            // ---- Carátula grande a ancho completo ----
            // Carrusel reconstruido (v1.0.6): pager de borde a borde, sin
            // franjas laterales. Cada página ocupa TODO el ancho del viewport:
            // en reposo se ve UNA portada limpia; al deslizar, la portada sale
            // por un borde y la otra entra por el otro, recorriendo el viewport
            // completo. La portada va centrada con margen lateral para que su
            // sombra nunca se recorte en los bordes del viewport.
            // - Ventana [vecinoAnterior?, actual, vecinoSiguiente?] con índices
            //   SIEMPRE del timeline (un solo espacio de índices en la app).
            // - Al SOLTAR pasando el umbral (80 dp o fling > 1000 px/s):
            //   PRIMERO se anima el pager hasta la página destino
            //   (deslizamiento visible a ancho completo) y recién al terminar
            //   la animación se cambia la canción. Izquierda = siguiente,
            //   derecha = anterior. Sin el umbral, el pager vuelve solo.
            // - Cambio directo al media item, sin la regla de 3 s del botón
            //   anterior. Con aleatorio, los vecinos siguen el orden aleatorio.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                androidx.compose.ui.graphics.Color.Transparent
                            ),
                            radius = 600f
                        )
                    )
            ) {
                val pageWidth = maxWidth
                // Margen lateral para que la sombra de la portada (24 dp)
                // nunca se recorte en los bordes del viewport.
                val coverSideMargin = 32.dp
                // Ventana del carrusel: índices del timeline. Un vecino
                // INDEX_UNSET o igual al actual se trata como inexistente
                // (el pager rebota en los extremos, sin wrap).
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
                // Estado fresco para los observadores (ninguna recomposición
                // los corta ni les deja valores viejos).
                val latestCurrentIndex by rememberUpdatedState(state.currentIndex)
                val latestNeighbors by rememberUpdatedState(state.prevNeighborIndex to state.nextNeighborIndex)
                val latestOnPlayQueueItem by rememberUpdatedState(onPlayQueueItem)
                val density = LocalDensity.current
                val swipeThresholdPx = remember(density) { with(density) { 80.dp.toPx() } }
                val flingThresholdPxPerSec = 1000f

                // Alcance para la animación programada del cambio + bandera
                // anti-doble-disparo: mientras un cambio está en vuelo se
                // ignoran nuevos triggers de release.
                val coverScope = rememberCoroutineScope()
                val swipeChangeInFlight = remember { mutableStateOf(false) }

                // Carrusel -> reproductor (SOLO vía release): al soltar pasando
                // el umbral o con fling, PRIMERO se anima el pager hasta la
                // página destino (deslizamiento visible a ancho completo) y
                // recién al terminar la animación se cambia la canción.
                // NO consume: el pager sigue el dedo en vivo durante el
                // arrastre; al soltar, nuestra animación toma el control.
                val releaseObserver = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var maxDx = 0f
                        val tracker = VelocityTracker()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val vx = tracker.calculateVelocity().x
                                val toNext: Boolean? = when {
                                    abs(maxDx) >= swipeThresholdPx -> maxDx < 0
                                    abs(vx) >= flingThresholdPxPerSec -> vx < 0
                                    else -> null
                                }
                                if (toNext != null && !swipeChangeInFlight.value) {
                                    val window = latestCoverWindow
                                    val (prevN, nextN) = latestNeighbors
                                    val cur = latestCurrentIndex
                                    val targetIndex = if (toNext) nextN else prevN
                                    val targetPage = window.indexOf(targetIndex)
                                    val pageCount = coverPagerState.pageCount
                                    if (targetIndex != C.INDEX_UNSET && targetIndex != cur
                                        && targetPage in 0 until pageCount
                                    ) {
                                        swipeChangeInFlight.value = true
                                        coverScope.launch {
                                            try {
                                                // 1. Deslizamiento visible a ancho completo.
                                                coverPagerState.animateScrollToPage(targetPage)
                                                // 2. Chequeo fresco: cambiar solo si sigue
                                                //    haciendo falta.
                                                if (targetIndex != latestCurrentIndex) {
                                                    latestOnPlayQueueItem(targetIndex)
                                                    // 3. Esperar el cambio real (timeout defensivo).
                                                    withTimeoutOrNull(2000) {
                                                        snapshotFlow { latestCurrentIndex }
                                                            .first { it == targetIndex }
                                                    }
                                                    // 4. Recentrado de seguridad sobre la misma
                                                    //    portada (sin salto visible).
                                                    val w = latestCoverWindow
                                                    val c = w.indexOf(latestCurrentIndex)
                                                    if (c in w.indices
                                                        && !coverPagerState.isScrollInProgress
                                                        && coverPagerState.currentPage != c
                                                    ) {
                                                        coverPagerState.scrollToPage(c)
                                                    }
                                                }
                                            } finally {
                                                swipeChangeInFlight.value = false
                                            }
                                        }
                                    }
                                }
                                break
                            }
                            val dx = change.position.x - down.position.x
                            if (abs(dx) > abs(maxDx)) maxDx = dx
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Sin consume(): el gesto sigue limpio para el pager.
                        }
                    }
                }

                // Asentamiento -> solo recentrado visual (NUNCA cambia la
                // canción): si el pager quedó en una página que no es la del
                // tema actual, vuelve al centro con animación. No pelea con
                // la animación programada del swipe (bandera anti-doble-disparo).
                LaunchedEffect(coverPagerState) {
                    snapshotFlow { coverPagerState.currentPage to coverPagerState.isScrollInProgress }
                        .collect { (page, scrolling) ->
                            if (!scrolling && !swipeChangeInFlight.value) {
                                val center = latestCenterPage
                                if (page != center && center < latestCoverWindow.size) {
                                    coverPagerState.animateScrollToPage(center)
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

                HorizontalPager(
                    state = coverPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(releaseObserver)
                ) { page ->
                    // La página es una posición de la ventana; se traduce a
                    // índice del timeline (un solo espacio de índices) y la
                    // portada se resuelve igual que en el reproductor.
                    val timelineIndex = latestCoverWindow.getOrNull(page)
                    val coverSong = timelineIndex?.let { viewModel.playback.songAtTimelineIndex(it) }
                    // La página ocupa todo el ancho; la portada va centrada
                    // con margen lateral para que su sombra NUNCA se recorte
                    // en los bordes del viewport (en reposo: una portada
                    // limpia con su sombra completa, sin franjas).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pageWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverArt(
                            uri = coverSong?.let { customCovers[it.id] ?: it.albumArtUri },
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = customMetadata[song.id]?.first?.ifBlank { song.title } ?: song.title,
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
                        text = customMetadata[song.id]?.second?.ifBlank { song.artist.ifBlank { "Artista desconocido" } }
                            ?: song.artist.ifBlank { "Artista desconocido" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    // Bloquea toques para que no lleguen al Now Playing
                    // detrás (el botón ⋮ está casi en el mismo sitio que
                    // el de compartir de la cabecera de letras)
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

