package com.aural.player.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aural.player.shared.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() = application {
    val windowState = rememberWindowState(width = 1000.dp, height = 650.dp)
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val player = remember { DesktopPlayer(appScope).also { it.start() } }
    val state by player.state.collectAsState()
    Window(
        onCloseRequest = { player.dispose(); exitApplication() },
        state = windowState,
        title = "AuralPlayer"
    ) {
        App(
            state = state,
            onSelectSong = player::select,
            onTogglePlay = player::toggle,
            onNext = { player.next() },
            onPrevious = player::previous,
            onSeek = player::seek
        )
    }
}
