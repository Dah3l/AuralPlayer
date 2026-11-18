package com.aural.player.shared

import androidx.compose.ui.graphics.ImageBitmap

/** Decodifica bytes de imagen (JPEG/PNG) a un mapa de bits de Compose. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
