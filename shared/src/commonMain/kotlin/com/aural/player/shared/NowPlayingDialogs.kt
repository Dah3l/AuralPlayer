// Diálogos del Now Playing (mismos que la app Android, adaptados a PC):
// temporizador, velocidad, letra, portada, información, compartir,
// ecualizador, eliminar, y la hoja de la cola.
package com.aural.player.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SleepTimerDialog(
    sleepMinutes: Int?,
    stopMode: StopMode?,
    onMinutes: (Int) -> Unit,
    onStopMode: (StopMode) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador de suspensión") },
        text = {
            Column {
                listOf(15, 30, 60, 90).forEach { mins ->
                    TextButton(
                        onClick = { onMinutes(mins); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$mins minutos", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(
                    onClick = { onStopMode(StopMode.SONG); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Al terminar la canción", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = { onStopMode(StopMode.QUEUE); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Al terminar la lista", modifier = Modifier.fillMaxWidth())
                }
                if (sleepMinutes != null || stopMode != null) {
                    TextButton(
                        onClick = { onCancelTimer(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Cancelar temporizador",
                            color = scheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun SpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidad de reproducción") },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    TextButton(
                        onClick = { onSelect(speed); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val label = if (speed == 1f) "1x (Normal)" else "${speed}x"
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (speed == currentSpeed) Text("✓")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun LyricsPickerDialog(
    initialTrack: String,
    initialArtist: String,
    isSearching: Boolean,
    errorMessage: String?,
    onSearch: (String, String) -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var track by remember { mutableStateOf(initialTrack) }
    var artist by remember { mutableStateOf(initialArtist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar letra") },
        text = {
            Column {
                Text(
                    "Busca la letra en línea o elige un archivo .lrc de tu PC.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = track,
                    onValueChange = { track = it },
                    label = { Text("Canción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = scheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    enabled = !isSearching && track.isNotBlank(),
                    onClick = { onSearch(track.trim(), artist.trim()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSearching) "Buscando…" else "Buscar en línea",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elegir archivo .lrc…", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun EditMetadataDialog(
    initialTitle: String,
    initialArtist: String,
    initialAlbum: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    var album by remember { mutableStateOf(initialAlbum) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar información") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), artist.trim(), album.trim()); onDismiss() }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Diálogo "Cambiar portada", igual que en Android: campos de canción
 * y artista editables, búsqueda en iTunes (gratis), galería local y
 * quitar la personalizada. Al elegir un resultado se descarga la
 * portada en 600 px y se guarda en local.
 */
@Composable
fun CoverPickerDialog(
    artist: String,
    title: String,
    hasCustom: Boolean,
    onSelect: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var searchTitle by remember { mutableStateOf(title) }
    var searchArtist by remember {
        mutableStateOf(
            artist.takeUnless {
                it == "Artista desconocido" || it == "Artista misterioso"
            } ?: ""
        )
    }
    var results by remember { mutableStateOf<List<CoverArtResult>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        scope.launch {
            isLoading = true
            error = false
            val found = CoverArtClient.search(searchArtist, searchTitle)
            results = found
            isLoading = false
            error = found.isEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar portada") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = searchTitle,
                    onValueChange = { searchTitle = it },
                    label = { Text("Canción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchArtist,
                    onValueChange = { searchArtist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { doSearch() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) { Text("Buscar") }
                    OutlinedButton(
                        onClick = { onPickFromGallery(); onDismiss() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Galería") }
                }
                if (hasCustom) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onRemove(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Quitar portada personalizada",
                            color = scheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Buscando portadas...")
                        }
                        error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌐", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No se encontraron portadas.\nPrueba usar VPN y reintenta.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        results != null -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(results!!) { result ->
                                    Column(
                                        modifier = Modifier.clickable {
                                            onSelect(result.artworkUrl)
                                            onDismiss()
                                        },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val thumb = rememberUrlImage(result.thumbnailUrl)
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb,
                                                contentDescription = result.collectionName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(100.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .background(scheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    AuralIcons.MusicNote,
                                                    contentDescription = null,
                                                    tint = scheme.primary.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = result.collectionName.ifBlank { result.trackName },
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun ShareDialog(
    song: Song,
    onCopyLink: () -> Unit,
    onReveal: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir") },
        text = {
            Column {
                TextButton(
                    onClick = { onCopyLink(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copiar Aural link", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = { onReveal(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mostrar en el explorador", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun DeleteSongDialog(
    song: Song,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar canción") },
        text = {
            Text("¿Eliminar \"${song.title}\"? Se borrará el archivo de tu PC.")
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("Eliminar", color = scheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ---- Ecualizador ----

/** Frecuencias de las 10 bandas de VLC (respaldo si el motor no las reporta). */
private val FALLBACK_BANDS = listOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)

private val EQ_PRESETS: List<Pair<String, List<Float>>> = listOf(
    "Plano" to List(10) { 0f },
    "Rock" to listOf(4f, 3f, 2f, 0f, -1f, 1f, 3f, 4f, 4f, 4f),
    "Pop" to listOf(-1f, 2f, 4f, 3f, 1f, 0f, -1f, -1f, 2f, 3f),
    "Jazz" to listOf(3f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 3f, 3f),
    "Clásica" to listOf(4f, 3f, 2f, 1f, 0f, 0f, 1f, 3f, 4f, 4f),
    "Graves" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
    "Agudos" to listOf(0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f, 6f)
)

private fun formatBandLabel(freqHz: Float): String =
    if (freqHz >= 1000f) "${(freqHz / 1000f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }} kHz"
    else "${freqHz.toInt()} Hz"

@Composable
fun EqualizerDialog(
    enabled: Boolean,
    preamp: Float,
    amps: List<Float>,
    bandFrequencies: List<Float>,
    onApply: (Boolean, Float, List<Float>) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bands = bandFrequencies.ifEmpty { FALLBACK_BANDS }
    var eqEnabled by remember { mutableStateOf(enabled) }
    var eqPreamp by remember { mutableStateOf(preamp) }
    var eqAmps by remember {
        mutableStateOf(
            List(bands.size) { index -> amps.getOrElse(index) { 0f } }
        )
    }
    fun apply() = onApply(eqEnabled, eqPreamp, eqAmps)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ecualizador") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ecualizador",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { eqEnabled = it; apply() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Preajustes",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Column {
                    EQ_PRESETS.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { (name, preset) ->
                                TextButton(
                                    enabled = eqEnabled,
                                    onClick = {
                                        eqAmps = List(bands.size) { i -> preset.getOrElse(i) { 0f } }
                                        apply()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(name, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EqSliderRow(
                    label = "Preamp",
                    value = eqPreamp,
                    enabled = eqEnabled,
                    onValueChange = { eqPreamp = it; apply() }
                )
                bands.forEachIndexed { index, freq ->
                    EqSliderRow(
                        label = formatBandLabel(freq),
                        value = eqAmps[index],
                        enabled = eqEnabled,
                        onValueChange = { value ->
                            eqAmps = eqAmps.toMutableList().also { it[index] = value }
                            apply()
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = eqEnabled,
                        onClick = {
                            eqPreamp = 0f
                            eqAmps = List(bands.size) { 0f }
                            apply()
                        }
                    ) { Text("Restablecer") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun EqSliderRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -12f..12f,
            steps = 23,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant
            )
        )
        Text(
            text = "${if (value > 0) "+" else ""}${value.toInt()} dB",
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
    }
}

// ---- Hoja de la cola ----

@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    loadCoverBytes: (Song) -> ByteArray?,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(scheme.surface)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.outline.copy(alpha = 0.6f))
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cola",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${queue.size} canciones",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(AuralIcons.Close, contentDescription = "Cerrar")
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    itemsIndexed(queue, key = { index, song -> "$index-${song.filePath}" }) { index, song ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) scheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onPlay(index) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArt(
                                bitmap = rememberCoverImage(song, loadCoverBytes),
                                modifier = Modifier.size(40.dp),
                                cornerRadius = 6.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = { onMove(index, index - 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(AuralIcons.ArrowUp, contentDescription = "Subir", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                enabled = index < queue.lastIndex,
                                onClick = { onMove(index, index + 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(AuralIcons.ArrowDown, contentDescription = "Bajar", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onRemove(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(AuralIcons.Close, contentDescription = "Quitar de la cola", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
