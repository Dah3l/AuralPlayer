// Diálogo de nueva versión disponible.
package com.aural.player.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.aural.player.data.UpdateInfo

/** Aviso de actualización: Descargar abre la página del release. */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Nueva versión disponible") },
        text = {
            Text("Aural ${info.version} ya está lista para descargar.")
        },
        confirmButton = {
            TextButton(onClick = onDownload) { Text("Descargar") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Más tarde") }
        }
    )
}
