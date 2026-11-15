package com.aural.player.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aural.player.shared.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AuralPlayer"
    ) {
        App()
    }
}
