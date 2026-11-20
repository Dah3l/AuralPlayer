// Iconos de transporte dibujados a mano (vectores 24x24, mismo
// espíritu que el logo: geometría propia, sin depender del paquete
// de iconos de Material, cuya publicación multiplataforma quedó en
// 1.7.3). El tinte lo pone el llamador con Icon(tint = ...).
package com.aural.player.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

    /** Marca de verificación (tema activo). */
    val Check: ImageVector = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }.build()

    /** Lupa de búsqueda (pestaña Descargas). */
    val Search: ImageVector = ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17f, 10.5f)
            arcTo(6.5f, 6.5f, 0f, false, true, 4f, 10.5f)
            arcTo(6.5f, 6.5f, 0f, false, true, 17f, 10.5f)
            close()
            moveTo(15.5f, 15.5f)
            lineTo(20.5f, 20.5f)
        }
    }.build()

    /** Flecha de descarga (pestaña Descargas). */
    val Download: ImageVector = ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 15f)
            moveTo(7.5f, 10.5f)
            lineTo(12f, 15f)
            lineTo(16.5f, 10.5f)
            moveTo(5f, 20f)
            lineTo(19f, 20f)
        }
    }.build()

    /** Flecha hacia atrás (cabeceras de pantallas). */
    val ArrowBack: ImageVector = ImageVector.Builder("ArrowBack", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }.build()

    /** Corazón lleno (favorita marcada). */
    val Heart: ImageVector = ImageVector.Builder("Heart", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 21f)
            curveTo(6f, 16.5f, 2.5f, 13f, 2.5f, 9.5f)
            curveTo(2.5f, 6.5f, 4.8f, 4.5f, 7.5f, 4.5f)
            curveTo(9.3f, 4.5f, 11f, 5.6f, 12f, 7.2f)
            curveTo(13f, 5.6f, 14.7f, 4.5f, 16.5f, 4.5f)
            curveTo(19.2f, 4.5f, 21.5f, 6.5f, 21.5f, 9.5f)
            curveTo(21.5f, 13f, 18f, 16.5f, 12f, 21f)
            close()
        }
    }.build()

    /** Corazón solo contorno (favorita sin marcar). */
    val HeartOutline: ImageVector =
        ImageVector.Builder("HeartOutline", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21f)
                curveTo(6f, 16.5f, 2.5f, 13f, 2.5f, 9.5f)
                curveTo(2.5f, 6.5f, 4.8f, 4.5f, 7.5f, 4.5f)
                curveTo(9.3f, 4.5f, 11f, 5.6f, 12f, 7.2f)
                curveTo(13f, 5.6f, 14.7f, 4.5f, 16.5f, 4.5f)
                curveTo(19.2f, 4.5f, 21.5f, 6.5f, 21.5f, 9.5f)
                curveTo(21.5f, 13f, 18f, 16.5f, 12f, 21f)
                close()
            }
        }.build()

    /** Cola: líneas + nota musical. */
    val QueueMusic: ImageVector =
        ImageVector.Builder("QueueMusic", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5f)
                lineTo(12f, 5f)
                lineTo(12f, 7f)
                lineTo(3f, 7f)
                close()
                moveTo(3f, 10.5f)
                lineTo(12f, 10.5f)
                lineTo(12f, 12.5f)
                lineTo(3f, 12.5f)
                close()
                moveTo(3f, 16f)
                lineTo(9f, 16f)
                lineTo(9f, 18f)
                lineTo(3f, 18f)
                close()
                moveTo(15.5f, 4f)
                lineTo(17.5f, 4f)
                lineTo(17.5f, 15f)
                lineTo(15.5f, 15f)
                close()
                moveTo(17.5f, 4f)
                lineTo(21f, 6.5f)
                lineTo(17.5f, 8f)
                close()
                moveTo(11.5f, 16.5f)
                arcTo(3f, 2.2f, 0f, false, true, 17.5f, 16.5f)
                arcTo(3f, 2.2f, 0f, false, true, 11.5f, 16.5f)
                close()
            }
        }.build()

    /** Añadir a playlist: líneas + signo más. */
    val PlaylistAdd: ImageVector =
        ImageVector.Builder("PlaylistAdd", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 6f)
                lineTo(13f, 6f)
                lineTo(13f, 8f)
                lineTo(3f, 8f)
                close()
                moveTo(3f, 11f)
                lineTo(13f, 11f)
                lineTo(13f, 13f)
                lineTo(3f, 13f)
                close()
                moveTo(3f, 16f)
                lineTo(9f, 16f)
                lineTo(9f, 18f)
                lineTo(3f, 18f)
                close()
                moveTo(16f, 11f)
                lineTo(18f, 11f)
                lineTo(18f, 21f)
                lineTo(16f, 21f)
                close()
                moveTo(12f, 15f)
                lineTo(22f, 15f)
                lineTo(22f, 17f)
                lineTo(12f, 17f)
                close()
            }
        }.build()

    /** Papelera (eliminar). */
    val Trash: ImageVector = ImageVector.Builder("Trash", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 3.5f)
            lineTo(14f, 3.5f)
            lineTo(14f, 6f)
            lineTo(10f, 6f)
            close()
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            lineTo(20f, 8f)
            lineTo(4f, 8f)
            close()
            moveTo(6f, 8f)
            lineTo(18f, 8f)
            lineTo(17f, 20.5f)
            lineTo(7f, 20.5f)
            close()
        }
    }.build()

    /** Cronómetro (temporizador de suspensión). */
    val Timer: ImageVector = ImageVector.Builder("Timer", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.5f, 2.5f)
            lineTo(13.5f, 2.5f)
            lineTo(13.5f, 4.5f)
            lineTo(10.5f, 4.5f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 4.5f)
            lineTo(12f, 6.5f)
            moveTo(5f, 13.5f)
            arcTo(7f, 7f, 0f, false, true, 19f, 13.5f)
            arcTo(7f, 7f, 0f, false, true, 5f, 13.5f)
            close()
            moveTo(12f, 13.5f)
            lineTo(15.5f, 10f)
        }
    }.build()

    /** Velocímetro (velocidad de reproducción). */
    val Gauge: ImageVector = ImageVector.Builder("Gauge", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 15f)
            arcTo(8f, 8f, 0f, false, true, 20f, 15f)
            moveTo(12f, 15f)
            lineTo(16f, 9f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.5f, 15f)
            arcTo(1.5f, 1.5f, 0f, false, true, 13.5f, 15f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 15f)
            close()
        }
    }.build()

    /** Controles deslizantes (ecualizador). */
    val Tune: ImageVector = ImageVector.Builder("Tune", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 18f)
            lineTo(20f, 18f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.7f, 6f)
            arcTo(2.3f, 2.3f, 0f, false, true, 11.3f, 6f)
            arcTo(2.3f, 2.3f, 0f, false, true, 6.7f, 6f)
            close()
            moveTo(12.7f, 12f)
            arcTo(2.3f, 2.3f, 0f, false, true, 17.3f, 12f)
            arcTo(2.3f, 2.3f, 0f, false, true, 12.7f, 12f)
            close()
            moveTo(4.7f, 18f)
            arcTo(2.3f, 2.3f, 0f, false, true, 9.3f, 18f)
            arcTo(2.3f, 2.3f, 0f, false, true, 4.7f, 18f)
            close()
        }
    }.build()

    /** Imagen (cambiar portada). */
    val Image: ImageVector = ImageVector.Builder("Image", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 5f)
            lineTo(21f, 5f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.7f, 9.5f)
            arcTo(1.8f, 1.8f, 0f, false, true, 10.3f, 9.5f)
            arcTo(1.8f, 1.8f, 0f, false, true, 6.7f, 9.5f)
            close()
            moveTo(3f, 18f)
            lineTo(9f, 11f)
            lineTo(13f, 15f)
            lineTo(16f, 12f)
            lineTo(21f, 17f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }
    }.build()

    /** Lápiz (editar información). */
    val Pencil: ImageVector = ImageVector.Builder("Pencil", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 17.25f)
            lineTo(3f, 21f)
            lineTo(6.75f, 21f)
            lineTo(18.5f, 9.25f)
            lineTo(14.75f, 5.5f)
            close()
        }
    }.build()

    /** Nota musical. */
    val MusicNote: ImageVector =
        ImageVector.Builder("MusicNote", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.3f, 18f)
                arcTo(3.2f, 2.4f, 0f, false, true, 11.7f, 18f)
                arcTo(3.2f, 2.4f, 0f, false, true, 5.3f, 18f)
                close()
                moveTo(11.2f, 4f)
                lineTo(13.2f, 4f)
                lineTo(13.2f, 18f)
                lineTo(11.2f, 18f)
                close()
                moveTo(13.2f, 4f)
                curveTo(17f, 4.5f, 19f, 7f, 18f, 10f)
                lineTo(16.5f, 9f)
                curveTo(16.8f, 7f, 15.5f, 5.8f, 13.2f, 5.5f)
                close()
            }
        }.build()

    /** Corazón lleno (favorita). */
    val Favorite: ImageVector =
        ImageVector.Builder("Favorite", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                lineTo(12f, 21.35f)
                close()
            }
        }.build()

    /** Corazón vacío (no favorita). */
    val FavoriteBorder: ImageVector =
        ImageVector.Builder("FavoriteBorder", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                lineTo(12f, 21.35f)
                close()
            }
        }.build()

    /** Compartir: tres nodos unidos. */
    val Share: ImageVector = ImageVector.Builder("Share", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8.2f, 10.8f)
            lineTo(15.8f, 7.2f)
            moveTo(8.2f, 13.2f)
            lineTo(15.8f, 16.8f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(3.6f, 12f)
            arcTo(2.4f, 2.4f, 0f, false, true, 8.4f, 12f)
            arcTo(2.4f, 2.4f, 0f, false, true, 3.6f, 12f)
            close()
            moveTo(15.6f, 6f)
            arcTo(2.4f, 2.4f, 0f, false, true, 20.4f, 6f)
            arcTo(2.4f, 2.4f, 0f, false, true, 15.6f, 6f)
            close()
            moveTo(15.6f, 18f)
            arcTo(2.4f, 2.4f, 0f, false, true, 20.4f, 18f)
            arcTo(2.4f, 2.4f, 0f, false, true, 15.6f, 18f)
            close()
        }
    }.build()

    /** Círculo con menos (quitar de la cola). */
    val MinusCircle: ImageVector =
        ImageVector.Builder("MinusCircle", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, false, true, 20.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, false, true, 3.5f, 12f)
                close()
                moveTo(7.5f, 12f)
                lineTo(16.5f, 12f)
            }
        }.build()

    /** Repetir (dos flechas en circuito). */
    val Repeat: ImageVector = ImageVector.Builder("Repeat", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17f, 1f)
            lineTo(21f, 5f)
            lineTo(17f, 9f)
            moveTo(3f, 11f)
            lineTo(3f, 9f)
            arcTo(4f, 4f, 0f, false, true, 7f, 5f)
            lineTo(21f, 5f)
            moveTo(7f, 23f)
            lineTo(3f, 19f)
            lineTo(7f, 15f)
            moveTo(21f, 13f)
            lineTo(21f, 15f)
            arcTo(4f, 4f, 0f, false, true, 17f, 19f)
            lineTo(3f, 19f)
        }
    }.build()

    /** Repetir una (circuito + "1" en el centro). */
    val RepeatOne: ImageVector =
        ImageVector.Builder("RepeatOne", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 1f)
                lineTo(21f, 5f)
                lineTo(17f, 9f)
                moveTo(3f, 11f)
                lineTo(3f, 9f)
                arcTo(4f, 4f, 0f, false, true, 7f, 5f)
                lineTo(21f, 5f)
                moveTo(7f, 23f)
                lineTo(3f, 19f)
                lineTo(7f, 15f)
                moveTo(21f, 13f)
                lineTo(21f, 15f)
                arcTo(4f, 4f, 0f, false, true, 17f, 19f)
                lineTo(3f, 19f)
                moveTo(10.5f, 10.5f)
                lineTo(12.5f, 8.5f)
                lineTo(12.5f, 16f)
            }
        }.build()

    /** Aleatorio (flechas cruzadas). */
    val Shuffle: ImageVector = ImageVector.Builder("Shuffle", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 8f)
            moveTo(4f, 20f)
            lineTo(21f, 3f)
            moveTo(21f, 16f)
            lineTo(21f, 21f)
            lineTo(16f, 21f)
            moveTo(15f, 15f)
            lineTo(21f, 21f)
            moveTo(4f, 4f)
            lineTo(9f, 9f)
        }
    }.build()

    /** Flecha hacia abajo (cerrar el Now Playing). */
    val ChevronDown: ImageVector =
        ImageVector.Builder("ChevronDown", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()

    /** Flecha hacia arriba (subir en la cola). */
    val ArrowUp: ImageVector = ImageVector.Builder("ArrowUp", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 19f)
            lineTo(12f, 5f)
            moveTo(5f, 12f)
            lineTo(12f, 5f)
            lineTo(19f, 12f)
        }
    }.build()

    /** Flecha hacia abajo (bajar en la cola). */
    val ArrowDown: ImageVector =
        ImageVector.Builder("ArrowDown", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(19f, 12f)
                lineTo(12f, 19f)
                lineTo(5f, 12f)
            }
        }.build()

    /** Reloj (agregadas recientemente). */
    val Clock: ImageVector = ImageVector.Builder("Clock", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3.5f, 12f)
            arcTo(8.5f, 8.5f, 0f, false, true, 20.5f, 12f)
            arcTo(8.5f, 8.5f, 0f, false, true, 3.5f, 12f)
            close()
            moveTo(12f, 7f)
            lineTo(12f, 12f)
            lineTo(15.5f, 14f)
        }
    }.build()

    val Plus: ImageVector = ImageVector.Builder("Plus", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }.build()
}
