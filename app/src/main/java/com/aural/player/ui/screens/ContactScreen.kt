// Pantalla de contacto con el desarrollador (v1.0.8).
// Se abre desde el menú ⋮ de la biblioteca, igual que Tema o Apoyar.
// Los datos de contacto viven en el código (constantes), no en memoria.
package com.aural.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** WhatsApp del desarrollador (código de país incluido). */
const val DEV_WHATSAPP_NUMBER = "5359648522"

/** Correo del desarrollador. */
const val DEV_EMAIL = "auralplayeroficial@gmail.com"

/** Instagram de Aural. */
const val AURAL_INSTAGRAM_URL = "https://instagram.com/auralplayer"

@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Surface: fondo y color de contenido (mismo patrón que SettingsScreen).
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Scrim hermano: bloquea los toques hacia la pantalla de debajo.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown().consume() }
                    }
            )
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack, "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Contacto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "¿Dudas, ideas o problemas con Aural? Escríbeme por donde prefieras.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    ContactRow(
                        icon = Icons.Rounded.Chat,
                        title = "WhatsApp",
                        subtitle = "+53 59648522",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/$DEV_WHATSAPP_NUMBER")
                                )
                            )
                        }
                    )
                    ContactRow(
                        icon = Icons.Rounded.Email,
                        title = "Correo",
                        subtitle = DEV_EMAIL,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:$DEV_EMAIL")
                                )
                            )
                        }
                    )
                    ContactRow(
                        icon = Icons.Rounded.CameraAlt,
                        title = "Instagram",
                        subtitle = "@auralplayer",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(AURAL_INSTAGRAM_URL)
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
