// Pantalla de Ajustes (v1.0.7): agrupa lo que antes vivía en el menú ⋮
// (ecualizador, Dynamic Aural, carpetas ocultas, acerca de, licencia,
// repositorio) para que el menú quede corto.
package com.aural.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.installedVersionName

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    islandEnabled: Boolean,
    onToggleIsland: () -> Unit,
    islandAppearance: String,
    onIslandAppearance: (String) -> Unit,
    viewModel: AuralViewModel
) {
    val context = LocalContext.current
    var showHiddenFolders by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // El botón atrás del sistema vuelve a la pantalla anterior.
    BackHandler(onBack = onBack)
    // Surface: da el fondo y el color de contenido para los textos
    // (sin Surface, LocalContentColor es negro y los títulos salían oscuros).
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Scrim hermano (detrás del contenido): bloquea los toques para que
        // no lleguen a la pantalla de debajo — mismo patrón que NowPlayingScreen.
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                        }
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
                text = "Ajustes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingsSection("Sonido")
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = "Ecualizador",
                subtitle = "5 bandas, presets, refuerzo de bajos",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onOpenEqualizer
            )
            SettingsSection("Interfaz")
            SettingsRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Dynamic Aural",
                subtitle = "Píldora flotante con la música fuera de la app",
                trailing = {
                    Switch(checked = islandEnabled, onCheckedChange = { onToggleIsland() })
                },
                onClick = onToggleIsland
            )
            // Aspecto de Dynamic Aural: auto (según el tema) o classic (negra).
            if (islandEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.width(40.dp))
                        FilterChip(
                            selected = islandAppearance == "auto",
                            onClick = { onIslandAppearance("auto") },
                            label = { Text("Automático") },
                            leadingIcon = if (islandAppearance == "auto") {
                                {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.primary,
                                selectedLabelColor =
                                    MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = islandAppearance == "classic",
                            onClick = { onIslandAppearance("classic") },
                            label = { Text("Clásico") },
                            leadingIcon = if (islandAppearance == "classic") {
                                {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.primary,
                                selectedLabelColor =
                                    MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    Text(
                        text = if (islandAppearance == "auto")
                            "Sigue los colores de tu tema en el fondo y los elementos."
                        else
                            "Fondo negro con los acentos en el color primario de tu tema.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, top = 4.dp)
                    )
                }
            }
            SettingsSection("Biblioteca")
            SettingsRow(
                icon = Icons.Rounded.Folder,
                title = "Carpetas ocultas",
                subtitle = "Muestra las carpetas que ocultaste",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showHiddenFolders = true }
            )
            SettingsSection("Aural")
            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "Acerca de AuralPlayer",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showAboutDialog = true }
            )
            SettingsRow(
                icon = Icons.Rounded.Description,
                title = "Licencia",
                subtitle = "GPL-3.0",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showLicenseDialog = true }
            )
            SettingsRow(
                icon = Icons.Rounded.Code,
                title = "Repositorio en GitHub",
                subtitle = "github.com/Dah3l/AuralPlayer",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Dah3l/AuralPlayer")
                        )
                    )
                }
            )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }

    // ---- Carpetas ocultas ----
    if (showHiddenFolders) {
        val hidden by viewModel.hiddenFolders.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showHiddenFolders = false },
            title = { Text("Carpetas ocultas") },
            text = {
                if (hidden.isEmpty()) {
                    Text("No hay carpetas ocultas. Mantén pulsada una carpeta en la pestaña Carpetas para ocultarla.")
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(hidden.toList(), key = { it }) { path ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = path.ifBlank { "Desconocida" },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = {
                                    viewModel.toggleHiddenFolder(path)
                                }) { Text("Mostrar") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenFolders = false }) { Text("Cerrar") }
            }
        )
    }

    // ---- Diálogo de licencia ----
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("Licencia") },
            text = {
                Text(
                    "AuralPlayer es software libre bajo la GNU General Public " +
                        "License v3.0.\n\nPuedes usar, modificar y compartir " +
                        "esta app libremente, siempre que cualquier versión " +
                        "distribuida mantenga esta misma licencia y el código " +
                        "fuente disponible.\n\nCopyright (C) 2026 Dahel"
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("Cerrar") }
            }
        )
    }

    // ---- Diálogo acerca de ----
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Acerca de AuralPlayer") },
            text = {
                Text(
                    "AuralPlayer — tu música, sin ruido.\n\n" +
                        "Reproductor minimalista con descargas de YouTube, " +
                        "letras sincronizadas, ecualizador, reconocimiento de " +
                        "canciones estilo Shazam y 16 temas para hacerlo tuyo.\n\n" +
                        "Versión ${installedVersionName(context)}\nHecho con ❤️ y Jetpack Compose"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}
