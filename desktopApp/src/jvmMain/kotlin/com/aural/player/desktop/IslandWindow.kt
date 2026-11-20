// Ventana flotante de la isla (puerto de la isla de Android a Windows):
// un DialogWindow sin bordes, siempre visible y SIN botón en la barra
// de tareas (JDialog, no JFrame). No roba el foco (focusable = false:
// los clics y arrastres llegan igual, pero nunca interrumpe lo que
// el usuario esté haciendo en otra ventana). La píldora se arrastra
// para moverla; el morph lo sigue la ventana cuadro por cuadro.
package com.aural.player.desktop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.aural.player.shared.IslandContent
import com.aural.player.shared.PlaybackState
import com.aural.player.shared.PlayerActions
import java.awt.Toolkit

@Composable
fun IslandWindow(
    state: PlaybackState,
    actions: PlayerActions,
    onOpenApp: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    val song = state.currentSong
    // Ocultar temporal: vuelve sola al cambiar de canción o al
    // pausar/reanudar (igual que en el móvil).
    var hiddenForSongPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song?.filePath, state.isPlaying) {
        hiddenForSongPath = null
    }
    val visible = state.islandEnabled && song != null && hiddenForSongPath != song?.filePath
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.TopCenter),
        size = DpSize(135.dp + 40.dp, 44.dp + 40.dp)
    )
    DialogWindow(
        onCloseRequest = {
            song?.let { hiddenForSongPath = it.filePath }
        },
        state = dialogState,
        visible = visible,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true
    ) {
        val density = LocalDensity.current
        // Centrar arriba la primera vez (10 dp desde el borde superior).
        LaunchedEffect(Unit) {
            val screenW = Toolkit.getDefaultToolkit().screenSize.width
            val screenWDp = screenW / density.density
            val x = ((screenWDp - dialogState.size.width.value) / 2).dp
            dialogState.position = WindowPosition(x, 10.dp)
        }
        // Arrastrar la píldora mueve la ventana (los gestos propios
        // del contenido — slider, swipe arriba — se tragan los suyos).
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = (dragAmount.x / density.density).dp
                    val dy = (dragAmount.y / density.density).dp
                    val pos = dialogState.position
                    dialogState.position = WindowPosition(pos.x + dx, pos.y + dy)
                }
            }
        ) {
            IslandContent(
                state = state,
                actions = actions,
                loadCoverBytes = actions::loadCoverBytes,
                onOpenApp = onOpenApp,
                onOpenYouTube = onOpenYouTube,
                onHide = { song?.let { hiddenForSongPath = it.filePath } },
                onWindowSync = { wDp, hDp ->
                    // La ventana sigue al morph: tamaño + centrado.
                    // El +40 es el aire de la sombra (20dp por lado).
                    dialogState.size = DpSize((wDp + 40f).dp, (hDp + 40f).dp)
                    val screenW = Toolkit.getDefaultToolkit().screenSize.width
                    val screenWDp = screenW / density.density
                    val x = ((screenWDp - (wDp + 40f)) / 2).dp
                    dialogState.position = WindowPosition(x, dialogState.position.y)
                }
            )
        }
    }
}
