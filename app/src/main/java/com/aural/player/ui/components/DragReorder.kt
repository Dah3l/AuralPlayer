// Lógica de reordenamiento por arrastre, reutilizable en cola y playlists
package com.aural.player.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Estado del arrastre para una lista. Se crea con [rememberDragReorderState]
 * y se comparte entre los ítems y sus agarraderas.
 */
class DragReorderState {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffsetPx by mutableFloatStateOf(0f)
}

@Composable
fun rememberDragReorderState(): DragReorderState = remember { DragReorderState() }

/**
 * Agarradera de arrastre (icono ⠿⠿). Al arrastrar verticalmente invoca
 * [onMove] cada vez que se cruza el umbral de un ítem.
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
                        val from = dragState.draggedIndex
                            ?: return@detectVerticalDragGestures
                        dragState.dragOffsetPx += dragAmount
                        val threshold = with(density) { 48.dp.toPx() }
                        if (dragState.dragOffsetPx > threshold && from < itemCount - 1) {
                            onMove(from, from + 1)
                            dragState.draggedIndex = from + 1
                            dragState.dragOffsetPx -= threshold
                        } else if (dragState.dragOffsetPx < -threshold && from > 0) {
                            onMove(from, from - 1)
                            dragState.draggedIndex = from - 1
                            dragState.dragOffsetPx += threshold
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
        scaleX = if (isDragged) 1.03f else 1f
        scaleY = if (isDragged) 1.03f else 1f
    }
