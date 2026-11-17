package com.aural.player.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aural.player.shared.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Icono de la ventana dibujado en código: fondo oscuro redondeado
 * + el logo de Aural (punto y barras, geometría exacta del vector
 * del launcher, viewport 108). Sin archivos binarios en el repo.
 */
private fun auralWindowIcon(): Painter {
    val size = 64
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val bg = Paint().apply { color = Color(0xFF121212) }
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 14f, 14f, bg)
    val teal = Paint().apply { color = Color(0xFF2ED3B7) }
    canvas.save()
    canvas.scale(size / 108f, size / 108f)
    canvas.drawCircle(Offset(34f, 46f), 5f, teal)
    canvas.drawRoundRect(46f, 38f, 52f, 70f, 3f, 3f, teal)
    canvas.drawRoundRect(58f, 30f, 64f, 78f, 3f, 3f, teal)
    canvas.drawRoundRect(70f, 40f, 76f, 68f, 3f, 3f, teal)
    canvas.restore()
    return BitmapPainter(bitmap)
}

fun main() = application {
    val windowState = rememberWindowState(width = 1000.dp, height = 650.dp)
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val player = remember { DesktopPlayer(appScope).also { it.start() } }
    val state by player.state.collectAsState()
    Window(
        onCloseRequest = { player.dispose(); exitApplication() },
        state = windowState,
        title = "AuralPlayer",
        icon = remember { auralWindowIcon() }
    ) {
        App(
            state = state,
            onPlayFrom = player::playFrom,
            onTogglePlay = player::toggle,
            onNext = { player.next() },
            onPrevious = player::previous,
            onSeek = player::seek,
            onVolumeChange = player::setVolume,
            onToggleMute = player::toggleMute,
            onThemeChange = player::setTheme,
            onAddFolder = player::addFolder,
            onRemoveFolder = player::removeFolder,
            onCreatePlaylist = player::createPlaylist,
            onCreatePlaylistAndAdd = player::createPlaylistAndAdd,
            onRenamePlaylist = player::renamePlaylist,
            onDeletePlaylist = player::deletePlaylist,
            onAddToPlaylist = player::addToPlaylist,
            onRemoveFromPlaylist = player::removeFromPlaylist
        )
    }
}
