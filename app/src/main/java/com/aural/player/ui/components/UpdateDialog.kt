// Diálogo de nueva versión disponible: tiles por tienda (solo icono).
package com.aural.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aural.player.R
import com.aural.player.data.UpdateInfo

/** URLs de descarga por tienda. */
const val APKLIS_APP_URL = "https://apklis.cu/application/com.aural.player"
const val GITHUB_RELEASES_URL = "https://github.com/Dah3l/AuralPlayer/releases/latest"

/**
 * Página de la app en Uptodown. Se muestra el tile cuando la app esté
 * publicada allí; si la URL cambia, actualizar esta constante.
 */
const val UPTODOWN_APP_URL = "https://aural-player.uptodown.com/android"

/** Tienda de descarga con su logo oficial (drawable). */
private enum class DownloadStore(val url: String, val logoRes: Int) {
    APKLIS(APKLIS_APP_URL, R.drawable.logo_apklis),
    GITHUB(GITHUB_RELEASES_URL, R.drawable.logo_github),
    UPTODOWN(UPTODOWN_APP_URL, R.drawable.logo_uptodown),
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

/** Tile de tienda: el logo oficial, sin texto de enlace. */
@Composable
private fun StoreTile(store: DownloadStore, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tileShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(if (isPressed) 0.92f else 1f)
            .clip(tileShape)
            // Borde sutil según el tema: en modo claro define el borde
            // del tile blanco de Uptodown; en oscuro apenas se nota.
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                tileShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(store.logoRes),
            contentDescription = store.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
