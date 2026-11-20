// Pantallas del menú ⋮ de la biblioteca (PC): Apoyar, Contacto y
// Ajustes. Mismo contenido que en Android (mismos números y textos);
// en PC el copiar va por el portapapeles del sistema y los enlaces
// se abren en el navegador.
package com.aural.player.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Número de tarjeta CUP para donar por Transfermóvil (mismo que en Android). */
const val DONATION_CARD_CUP = "9227-9598-7391-3011"

/** Usuario de QvaPay para donaciones. */
const val DONATION_QVAPAY_USER = "@AuralPlayer"

/** Número de WhatsApp al que el donante confirma su donación. */
const val DONATION_CONFIRM_NUMBER = "59648522"

/** WhatsApp del desarrollador (código de país incluido). */
const val DEV_WHATSAPP_NUMBER = "5359648522"

/** Correo del desarrollador. */
const val DEV_EMAIL = "auralplayeroficial@gmail.com"

/** Instagram de Aural. */
const val AURAL_INSTAGRAM_URL = "https://instagram.com/auralplayer"

/** Página web oficial de Aural. */
const val AURAL_WEBSITE_URL = "https://auralplayer.onrender.com"

/** Releases de GitHub (versión Windows). */
const val AURAL_GITHUB_RELEASES_URL = "https://github.com/Dah3l/AuralPlayer/releases"

/** Cabecera común: flecha de volver + título. */
@Composable
private fun MenuHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                AuralIcons.ArrowBack, "Volver",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DonateScreen(
    onBack: () -> Unit,
    onCopy: (String) -> Unit
) {
    var copiedCard by remember { mutableStateOf(false) }
    var copiedQva by remember { mutableStateOf(false) }
    var copiedConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(copiedCard) {
        if (copiedCard) {
            delay(2000); copiedCard = false
        }
    }
    LaunchedEffect(copiedQva) {
        if (copiedQva) {
            delay(2000); copiedQva = false
        }
    }
    LaunchedEffect(copiedConfirm) {
        if (copiedConfirm) {
            delay(2000); copiedConfirm = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        MenuHeader(title = "Apoyar a Aural", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text(text = "❤", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "¿Te gusta Aural? ¡Dale cariño!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "¡Gracias por estar aquí! La mejor forma de apoyar a Aural es seguir usándola día a día. Mantenerla gratis y libre de anuncios es nuestro compromiso; si deseas contribuir a su crecimiento, puedes hacer tu donación por cualquiera de estas vías. ¡Cada apoyo cuenta! 🎵",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            // ---- Transfermóvil ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "Transfermóvil",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tarjeta CUP:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DONATION_CARD_CUP,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            onCopy(DONATION_CARD_CUP)
                            copiedCard = true
                        }) {
                            Text(if (copiedCard) "¡Copiado!" else "Copiar")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. Toca el botón para copiar el número\n" +
                            "2. Abre Transfermóvil → Transferencia\n" +
                            "3. Pega el número y dona lo que quieras",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Confirmación de la donación ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "¿Ya donaste? Confírmalo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Escríbeme por WhatsApp al $DONATION_CONFIRM_NUMBER para saber que tu apoyo llegó. ¡Gracias! ❤",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DONATION_CONFIRM_NUMBER,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            onCopy(DONATION_CONFIRM_NUMBER)
                            copiedConfirm = true
                        }) {
                            Text(if (copiedConfirm) "¡Copiado!" else "Copiar")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- QvaPay ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "QvaPay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Usuario:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DONATION_QVAPAY_USER,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            onCopy(DONATION_QVAPAY_USER)
                            copiedQva = true
                        }) {
                            Text(if (copiedQva) "¡Copiado!" else "Copiar")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. Toca el botón para copiar el usuario\n" +
                            "2. Ábrelo en QvaPay y dona el monto que desees",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "¡Gracias por apoyar a Aural!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ContactScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MenuHeader(title = "Contacto", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "¿Dudas, ideas o problemas con Aural? Escríbeme por donde prefieras.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ContactRow(
                title = "WhatsApp",
                subtitle = "+53 59648522",
                onClick = { onOpenUrl("https://wa.me/$DEV_WHATSAPP_NUMBER") }
            )
            ContactRow(
                title = "Correo",
                subtitle = DEV_EMAIL,
                onClick = { onOpenUrl("mailto:$DEV_EMAIL") }
            )
            ContactRow(
                title = "Instagram",
                subtitle = "@auralplayer",
                onClick = { onOpenUrl(AURAL_INSTAGRAM_URL) }
            )
            ContactRow(
                title = "Página web",
                subtitle = "auralplayer.onrender.com",
                onClick = { onOpenUrl(AURAL_WEBSITE_URL) }
            )
        }
    }
}

@Composable
private fun ContactRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = "Abrir",
            color = scheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    versionName: String,
    onExportBackup: () -> Boolean,
    onImportBackup: () -> Boolean,
    onClearData: () -> Boolean,
    onNotice: (String) -> Unit,
    islandEnabled: Boolean,
    cometEnabled: Boolean,
    islandAppearance: String,
    onSetIslandEnabled: (Boolean) -> Unit,
    onSetCometEnabled: (Boolean) -> Unit,
    onSetIslandAppearance: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var showLicense by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        MenuHeader(title = "Ajustes", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Respaldo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Text(
                text = "Guarda tus preferencias, playlists, favoritas y Top 10 en un archivo, o restaura uno guardado antes.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onNotice(
                        if (onExportBackup()) "Respaldo exportado"
                        else "No se pudo exportar el respaldo"
                    )
                }) {
                    Text("Exportar")
                }
                Button(onClick = {
                    onNotice(
                        if (onImportBackup()) "Respaldo importado"
                        else "No se pudo importar el respaldo"
                    )
                }) {
                    Text("Importar")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Isla flotante",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "La píldora flotante con la canción que suena, siempre visible sobre las demás ventanas.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            IslandSettingRow(
                title = "Mostrar isla",
                checked = islandEnabled,
                onCheckedChange = onSetIslandEnabled
            )
            IslandSettingRow(
                title = "Aural Light (cometa)",
                checked = cometEnabled,
                onCheckedChange = onSetCometEnabled
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (islandAppearance == "auto") {
                    Button(onClick = { onSetIslandAppearance("auto") }) { Text("Auto") }
                    OutlinedButton(onClick = { onSetIslandAppearance("classic") }) { Text("Clásica") }
                } else {
                    OutlinedButton(onClick = { onSetIslandAppearance("auto") }) { Text("Auto") }
                    Button(onClick = { onSetIslandAppearance("classic") }) { Text("Clásica") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Acerca de",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "AuralPlayer $versionName\nEl sonido, sin ruido.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showLicense = true }) {
                Text("Licencia")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Borrar datos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Borra preferencias, carpetas, playlists, favoritas, historial, portadas y letras guardadas. Después hay que cerrar y volver a abrir la app.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showClearConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = scheme.onError
                )
            ) {
                Text("Borrar datos")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("Licencia") },
            text = {
                Text(
                    "AuralPlayer es software libre bajo la Licencia Pública General " +
                        "de GNU, versión 3. Puedes usarlo, estudiarlo, modificarlo y " +
                        "compartirlo bajo sus términos."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("Cerrar") }
            }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("¿Borrar todos los datos?") },
            text = {
                Text(
                    "Se borran preferencias, carpetas, playlists, favoritas, " +
                        "historial, portadas y letras. Después hay que cerrar y " +
                        "volver a abrir AuralPlayer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onNotice(
                        if (onClearData()) "Datos borrados. Cierra y vuelve a abrir AuralPlayer."
                        else "No se pudieron borrar los datos"
                    )
                }) {
                    Text("Borrar", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Fila con interruptor para un ajuste de la isla. */
@Composable
private fun IslandSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
