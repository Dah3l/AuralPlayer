// La marca de Aural (punto + 3 barras) dibujada en código con la
// geometría exacta del vector del launcher (viewport 108), en el
// color que se pida. La usan la cabecera y las tarjetas de temas.
package com.aural.player.shared

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun LogoMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width / 108f
        drawCircle(color, radius = 5f * s, center = Offset(34f * s, 46f * s))
        fun bar(x: Float, y: Float, h: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x * s, y * s),
                size = Size(6f * s, h * s),
                cornerRadius = CornerRadius(3f * s)
            )
        }
        bar(46f, 38f, 32f)
        bar(58f, 30f, 48f)
        bar(70f, 40f, 28f)
    }
}
