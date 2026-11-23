package com.aural.player.shared

import androidx.compose.ui.graphics.ImageBitmap

/** Decodifica bytes de imagen (JPEG/PNG) a un mapa de bits de Compose.
 *  [maxDimension] limita el lado mayor (px): UrlImageCache pide 300
 *  (solo miniaturas); sin tope se decodifica tal cual, como antes. */
expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int = Int.MAX_VALUE): ImageBitmap?
