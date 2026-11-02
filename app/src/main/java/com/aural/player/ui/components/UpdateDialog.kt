// Diálogo de nueva versión disponible: tiles por tienda (solo icono).
package com.aural.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aural.player.data.UpdateInfo

/** URLs de descarga por tienda. */
const val APKLIS_APP_URL = "https://apklis.cu/application/com.aural.player"
const val GITHUB_RELEASES_URL = "https://github.com/Dah3l/AuralPlayer/releases/latest"

/**
 * Página de la app en Uptodown. Se muestra el tile cuando la app esté
 * publicada allí; si la URL cambia, actualizar esta constante.
 */
const val UPTODOWN_APP_URL = "https://aural-player.uptodown.com/android"

/** Tienda de descarga con su icono dibujado. */
private enum class DownloadStore(val url: String) {
    APKLIS(APKLIS_APP_URL),
    GITHUB(GITHUB_RELEASES_URL),
    UPTODOWN(UPTODOWN_APP_URL),
}

/**
 * v1.0.7: aviso de actualización renovado. En vez de un botón genérico,
 * muestra un tile por tienda (Apklis, GitHub, Uptodown), cada uno con
 * SOLO el icono del sitio. Al tocar un tile se abre su página de descarga.
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDownload: (String) -> Unit,
    onLater: () -> Unit
) {
    Dialog(onDismissRequest = onLater) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nueva versión disponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Aural ${info.version} ya está lista. Elige dónde descargarla:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DownloadStore.entries.forEach { store ->
                        StoreTile(
                            store = store,
                            onClick = { onDownload(store.url) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onLater) {
                    Text("Más tarde")
                }
            }
        }
    }
}

/** Tile de tienda: solo el icono, sin texto de enlace. */
@Composable
private fun StoreTile(store: DownloadStore, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(if (isPressed) 0.92f else 1f)
            .clip(RoundedCornerShape(20.dp))
            .background(storeBackground(store))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        when (store) {
            DownloadStore.APKLIS -> ApklisIcon(Modifier.size(48.dp))
            DownloadStore.GITHUB -> GitHubIcon(Modifier.size(48.dp))
            DownloadStore.UPTODOWN -> UptodownIcon(Modifier.size(48.dp))
        }
    }
}

private fun storeBackground(store: DownloadStore): Color = when (store) {
    DownloadStore.APKLIS -> Color(0xFF1B7BD4)
    DownloadStore.GITHUB -> Color(0xFF24292F)
    DownloadStore.UPTODOWN -> Color(0xFF00A0E6)
}

/** Icono Apklis: "A" blanca sobre azul. */
@Composable
private fun ApklisIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.92f)
            lineTo(w * 0.68f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.48f)
            lineTo(w * 0.32f, h * 0.92f)
            lineTo(w * 0.08f, h * 0.92f)
            close()
        }
        drawPath(path, Color.White)
        // Travesaño de la A
        drawLine(
            Color.White,
            Offset(w * 0.36f, h * 0.72f),
            Offset(w * 0.64f, h * 0.72f),
            strokeWidth = h * 0.12f
        )
    }
}

/** Icono GitHub: silueta simplificada de cabeza de gato sobre oscuro. */
@Composable
private fun GitHubIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Orejas
        val earH = h * 0.30f
        val earW = w * 0.22f
        drawPath(
            Path().apply {
                moveTo(cx - w * 0.28f, h * 0.38f)
                lineTo(cx - w * 0.34f, h * 0.38f - earH)
                lineTo(cx - w * 0.10f, h * 0.30f)
                close()
            },
            Color.White
        )
        drawPath(
            Path().apply {
                moveTo(cx + w * 0.28f, h * 0.38f)
                lineTo(cx + w * 0.34f, h * 0.38f - earH)
                lineTo(cx + w * 0.10f, h * 0.30f)
                close()
            },
            Color.White
        )
        // Cabeza
        drawCircle(Color.White, radius = w * 0.30f, center = Offset(cx, h * 0.58f))
        // Cuerpo (arco inferior)
        drawArc(
            Color.White,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.30f, h * 0.52f),
            size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.44f),
            style = Stroke(width = h * 0.10f)
        )
    }
}

/** Icono Uptodown: flecha de descarga blanca sobre azul. */
@Composable
private fun UptodownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val sw = w * 0.14f
        // Vástago
        drawLine(
            Color.White,
            Offset(cx, h * 0.12f),
            Offset(cx, h * 0.62f),
            strokeWidth = sw
        )
        // Punta
        drawPath(
            Path().apply {
                moveTo(cx - w * 0.26f, h * 0.44f)
                lineTo(cx, h * 0.72f)
                lineTo(cx + w * 0.26f, h * 0.44f)
                lineTo(cx + w * 0.26f - sw * 0.7f, h * 0.44f)
                lineTo(cx, h * 0.62f)
                lineTo(cx - w * 0.26f + sw * 0.7f, h * 0.44f)
                close()
            },
            Color.White
        )
        // Base
        drawLine(
            Color.White,
            Offset(w * 0.16f, h * 0.86f),
            Offset(w * 0.84f, h * 0.86f),
            strokeWidth = sw
        )
    }
}
