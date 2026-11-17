// Iconos de transporte dibujados a mano (vectores 24x24, mismo
// espíritu que el logo: geometría propia, sin depender del paquete
// de iconos de Material, cuya publicación multiplataforma quedó en
// 1.7.3). El tinte lo pone el llamador con Icon(tint = ...).
package com.aural.player.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AuralIcons {

    val Play: ImageVector = ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }.build()

    val Pause: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 5f)
            lineTo(10f, 5f)
            lineTo(10f, 19f)
            lineTo(6f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(18f, 5f)
            lineTo(18f, 19f)
            lineTo(14f, 19f)
            close()
        }
    }.build()

    val SkipPrevious: ImageVector =
        ImageVector.Builder("SkipPrevious", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f)
                lineTo(8f, 5f)
                lineTo(8f, 19f)
                lineTo(6f, 19f)
                close()
                moveTo(18f, 5f)
                lineTo(9f, 12f)
                lineTo(18f, 19f)
                close()
            }
        }.build()

    val SkipNext: ImageVector = ImageVector.Builder("SkipNext", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 5f)
            lineTo(15f, 12f)
            lineTo(6f, 19f)
            close()
            moveTo(16f, 5f)
            lineTo(18f, 5f)
            lineTo(18f, 19f)
            lineTo(16f, 19f)
            close()
        }
    }.build()

    // Bocina: cono + caja. La comparten Volumen y Silencio.
    private fun speakerPath(builder: ImageVector.Builder) {
        builder.path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f)
            lineTo(7f, 9f)
            lineTo(12f, 4.5f)
            lineTo(12f, 19.5f)
            lineTo(7f, 15f)
            lineTo(3f, 15f)
            close()
        }
    }

    val VolumeUp: ImageVector = ImageVector.Builder("VolumeUp", 24.dp, 24.dp, 24f, 24f).apply {
        speakerPath(this)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14.5f, 9.5f)
            curveTo(16.5f, 10.8f, 16.5f, 13.2f, 14.5f, 14.5f)
            moveTo(17f, 7.5f)
            curveTo(20f, 9.5f, 20f, 14.5f, 17f, 16.5f)
        }
    }.build()

    val VolumeOff: ImageVector = ImageVector.Builder("VolumeOff", 24.dp, 24.dp, 24f, 24f).apply {
        speakerPath(this)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(15f, 9f)
            lineTo(21f, 15f)
            moveTo(21f, 9f)
            lineTo(15f, 15f)
        }
    }.build()

    /** Tres puntos verticales (menú de la canción). Cada punto son dos semicírculos. */
    val MoreVert: ImageVector = ImageVector.Builder("MoreVert", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 5f)
            arcTo(2f, 2f, 0f, false, true, 14f, 5f)
            arcTo(2f, 2f, 0f, false, true, 10f, 5f)
            close()
            moveTo(10f, 12f)
            arcTo(2f, 2f, 0f, false, true, 14f, 12f)
            arcTo(2f, 2f, 0f, false, true, 10f, 12f)
            close()
            moveTo(10f, 19f)
            arcTo(2f, 2f, 0f, false, true, 14f, 19f)
            arcTo(2f, 2f, 0f, false, true, 10f, 19f)
            close()
        }
    }.build()

    /** X para quitar (de la playlist, del diálogo). */
    val Close: ImageVector = ImageVector.Builder("Close", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }.build()
}
