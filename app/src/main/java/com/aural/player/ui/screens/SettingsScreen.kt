// Pantalla de Ajustes (v1.0.7): agrupa lo que antes vivía en el menú ⋮
// (ecualizador, Dynamic Aural, carpetas ocultas, acerca de, licencia,
// repositorio) para que el menú quede corto.
package com.aural.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aural.player.ui.components.AlarmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    islandEnabled: Boolean,
    onToggleIsland: () -> Unit,
    islandAppearance: String,
    onIslandAppearance: (String) -> Unit,
    cometEnabled: Boolean,
    onToggleComet: () -> Unit,
    auralLightColor: String,
    onLightColorChange: (String) -> Unit,
    viewModel: AuralViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHiddenFolders by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    // Respaldo: exportar (crear archivo) e importar (elegir archivo).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Toast.makeText(context, "Respaldo guardado", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context, "No se pudo guardar el respaldo", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                    if (json.isNullOrBlank()) {
                        Toast.makeText(context, "El archivo está vacío", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingImportJson = json
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context, "No se pudo leer el archivo", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Subtítulo del despertador según su estado actual.
    val alarmState by viewModel.alarm.collectAsStateWithLifecycle()
    val alarmSubtitle = run {
        val a = alarmState
        when {
            a == null || !a.enabled -> "Desactivado"
            a.repeatDaily -> "%d:%02d · Todos los días".format(a.hour, a.minute)
            else -> "%d:%02d · Solo una vez".format(a.hour, a.minute)
        }
    }

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
                // Aural Light: al mismo nivel que Dynamic Aural para que
                // el toggle quede alineado al final del tile.
                SettingsRow(
                    icon = Icons.Rounded.FlashOn,
                    title = "Aural Light",
                    subtitle = "Luz que recorre el borde de la píldora",
                    trailing = {
                        Switch(checked = cometEnabled, onCheckedChange = { onToggleComet() })
                    },
                    onClick = onToggleComet
                )
                // Color de Aural Light: auto (primario del tema) o el
                // color de otro tema (p. ej. Sakura con Neon Prime).
                if (cometEnabled) {
                    Text(
                        text = "Color de Aural Light",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 8.dp)
                    )
                    FlowRow(
                        modifier = Modifier.padding(start = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = auralLightColor == "auto",
                            onClick = { onLightColorChange("auto") },
                            label = { Text("Auto") },
                            leadingIcon = if (auralLightColor == "auto") {
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
                        for ((_, hex) in AuralLightColors) {
                            AuralLightColorDot(
                                hex = hex,
                                selected = auralLightColor == hex,
                                onClick = { onLightColorChange(hex) }
                            )
                        }
                    }
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
            SettingsSection("Respaldo")
            SettingsRow(
                icon = Icons.Rounded.Backup,
                title = "Exportar respaldo",
                subtitle = "Playlists, favoritos, metadatos y portadas en un archivo",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    val date = java.text.SimpleDateFormat(
                        "yyyy-MM-dd", java.util.Locale.US
                    ).format(java.util.Date())
                    exportLauncher.launch("aural-respaldo-$date.json")
                }
            )
            SettingsRow(
                icon = Icons.Rounded.Restore,
                title = "Importar respaldo",
                subtitle = "Restaura tus datos desde un archivo guardado",
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            SettingsSection("Despertador")
            SettingsRow(
                icon = Icons.Rounded.Alarm,
                title = "Despertador",
                subtitle = alarmSubtitle,
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showAlarmDialog = true }
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

    // ---- Diálogo del despertador ----
    if (showAlarmDialog) {
        AlarmDialog(
            viewModel = viewModel,
            onDismiss = { showAlarmDialog = false }
        )
    }

    // ---- Confirmación de importar respaldo ----
    if (pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("Importar respaldo") },
            text = {
                Text(
                    "Esto reemplaza tus playlists, favoritos, metadatos y " +
                        "ajustes actuales con los del respaldo. ¿Continuar?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImportJson
                    pendingImportJson = null
                    scope.launch {
                        val ok = viewModel.importBackupJson(json ?: "")
                        Toast.makeText(
                            context,
                            if (ok) "Respaldo importado" else "Archivo no válido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text("Importar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text("Cancelar") }
            }
        )
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
private fun SettingsSection(title: String) {    Text(
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
            // Altura mínima, no fija: si el subtítulo ocupa dos líneas
            // (como el de Exportar respaldo), la fila crece en vez de
            // cortar el texto.
            .heightIn(min = 72.dp)
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

/** Paleta de Aural Light: los primarios más vivos de los temas. */
private val AuralLightColors = listOf(
    "Sakura" to "#FF4D8D",
    "Neón" to "#00F0FF",
    "Bosque" to "#2ED3B7",
    "Océano" to "#4DA6FF",
    "Atardecer" to "#FFFF8C42",
    "Navidad" to "#E63946",
    "Medianoche" to "#9D7BFF",
    "Sakura noche" to "#FF6FA5",
    "Azul royal" to "#014BAA",
    "Naranja" to "#EC5E27"
)

/** Punto de color de Aural Light: borde + marca cuando está elegido. */
@Composable
private fun AuralLightColorDot(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorInt = android.graphics.Color.parseColor(hex)
    val color = Color(colorInt)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                2.dp,
                if (selected) MaterialTheme.colorScheme.onSurface
                else Color.Transparent,
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                // Marca oscura sobre colores claros y blanca sobre oscuros.
                tint = if (android.graphics.Color.luminance(colorInt) > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
