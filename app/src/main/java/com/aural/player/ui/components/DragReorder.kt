// Lógica de reordenamiento por arrastre, reutilizable en cola y playlists
package com.aural.player.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos

/**
 * Estado del arrastre para una lista. Se crea con [rememberDragReorderState]
 * y se comparte entre los ítems y sus agarraderas.
 */
class DragReorderState {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffsetPx by mutableFloatStateOf(0f)

    /**
     * Altura real en px del ítem arrastrado. La alimentan las propias filas
     * (onSizeChanged) y el auto-scroll (layoutInfo). Con ella el cruce de
     * ítems usa la medida exacta de la fila y no un umbral fijo.
     */
    var draggedItemSizePx by mutableFloatStateOf(0f)
}

@Composable
fun rememberDragReorderState(): DragReorderState = remember { DragReorderState() }

/**
 * Agarradera de arrastre (icono ⠿⠿). Al arrastrar verticalmente invoca
 * [onMove] cada vez que el ítem arrastrado cruza el centro de un vecino.
 *
 * @param index índice del ítem al que pertenece esta agarradera
 * @param dragState estado compartido de la lista
 * @param itemCount total de ítems (para no salir de los bordes)
 * @param onMove se invoca con (from, to) al cruzar un ítem
 */
@Composable
fun DragHandle(
    index: Int,
    dragState: DragReorderState,
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    Icon(
        imageVector = Icons.Rounded.DragHandle,
        contentDescription = "Arrastrar para reordenar",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .pointerInput(index) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragState.draggedIndex = index
                        dragState.dragOffsetPx = 0f
                    },
                    onDragEnd = {
                        dragState.draggedIndex = null
                        dragState.dragOffsetPx = 0f
                    },
                    onDragCancel = {
                        dragState.draggedIndex = null
                        dragState.dragOffsetPx = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragState.draggedIndex == null) return@detectVerticalDragGestures
                        val fallback = with(density) { 48.dp.toPx() }
                        val swaps = dragState.consumeDragDelta(
                            dragAmount, fallback, itemCount, onMove
                        )
                        if (swaps > 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            }
    )
}

/**
 * Efecto visual del ítem arrastrado: se desplaza y crece ligeramente.
 * Aplicar al Modifier del contenedor de cada ítem de la lista.
 */
fun Modifier.draggedItemVisual(dragState: DragReorderState, index: Int): Modifier =
    graphicsLayer {
        val isDragged = dragState.draggedIndex == index
        translationY = if (isDragged) dragState.dragOffsetPx else 0f
        scaleX = if (isDragged) 1.05f else 1f
        scaleY = if (isDragged) 1.05f else 1f
        shadowElevation = if (isDragged) 8f else 0f
    }

/**
 * Aplica un desplazamiento de arrastre al estado y cruza ítems cuando el
 * centro del ítem arrastrado supera el centro del vecino, usando la altura
 * REAL del ítem (o [fallbackItemSizePx] si aún no se conoce). Al cruzar se
 * resta la altura completa, así el offset queda continuo y el ítem sigue el
 * dedo 1:1, sin saltos. Devuelve cuántos intercambios hizo (para el háptico).
 * Lo comparten el gesto manual (DragHandle) y el auto-scroll.
 */
fun DragReorderState.consumeDragDelta(
    deltaPx: Float,
    fallbackItemSizePx: Float,
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit
): Int {
    var current = draggedIndex ?: return 0
    val size = if (draggedItemSizePx > 0f) draggedItemSizePx else fallbackItemSizePx
    if (size <= 0f) return 0
    val start = current
    dragOffsetPx += deltaPx
    while (dragOffsetPx > size / 2f && current < itemCount - 1) {
        onMove(current, current + 1)
        current += 1
        dragOffsetPx -= size
    }
    while (dragOffsetPx < -size / 2f && current > 0) {
        onMove(current, current - 1)
        current -= 1
        dragOffsetPx += size
    }
    draggedIndex = current
    return kotlin.math.abs(current - start)
}

/**
 * Auto-scroll mientras se arrastra: si el ítem arrastrado entra en la zona
 * de borde (arriba o abajo) de la lista visible, la lista se desplaza poco
 * a poco y el desplazamiento alimenta el mismo mecanismo de cruce de ítems
 * del arrastre manual, para poder llevar un ítem más allá de lo visible
 * sin soltarlo. El desplazamiento se calcula una vez por frame
 * (withFrameNanos), sincronizado con el refresco de pantalla.
 */
@Composable
fun DragAutoScroll(
    listState: LazyListState,
    dragState: DragReorderState,
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val edgePx = with(density) { 72.dp.toPx() }
    val dragging = enabled && dragState.draggedIndex != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val index = dragState.draggedIndex ?: break
            val layout = listState.layoutInfo
            val info = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: continue
            if (info.size > 0) dragState.draggedItemSizePx = info.size.toFloat()
            val top = info.offset + dragState.dragOffsetPx
            val bottom = top + info.size
            val overBottom = bottom - (layout.viewportEndOffset - edgePx)
            val overTop = (layout.viewportStartOffset + edgePx) - top
            var desired = 0f
            if (overBottom > 0f) {
                desired = (overBottom * 0.25f).coerceIn(1.5f, 12f)
            } else if (overTop > 0f) {
                desired = -(overTop * 0.25f).coerceIn(1.5f, 12f)
            }
            if (desired != 0f) {
                val scrolled = listState.scrollBy(desired)
                if (scrolled != 0f) {
                    val swaps = dragState.consumeDragDelta(
                        scrolled, info.size.toFloat(), itemCount, onMove
                    )
                    if (swaps > 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }
    }
}
