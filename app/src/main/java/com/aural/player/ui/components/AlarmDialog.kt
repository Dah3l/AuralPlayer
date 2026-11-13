// Diálogo del despertador: hora, canción o playlist, repetición diaria
// y permiso de alarmas exactas. La alarma suena aunque la app esté
// cerrada (AlarmManager + servicio de música).
package com.aural.player.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.alarm.AlarmConfig
import com.aural.player.alarm.AlarmScheduler
import com.aural.player.data.Playlist
import com.aural.player.data.Song

@Composable
fun AlarmDialog(
    viewModel: AuralViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val existing by viewModel.alarm.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val customMetadata by viewModel.customMetadata.collectAsStateWithLifecycle()

    // Los estados nacen de la alarma guardada (si llega un instante
    // después de abrir, `existing` cambia y se reinician con sus valores).
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    var hour by remember(existing) { mutableStateOf(existing?.hour ?: 7) }
    var minute by remember(existing) { mutableStateOf(existing?.minute ?: 0) }
    var repeatDaily by remember(existing) { mutableStateOf(existing?.repeatDaily ?: false) }
    var mode by remember(existing) { mutableStateOf(existing?.mode ?: "song") }
    var selectedSongId by remember(existing) {
        mutableStateOf(existing?.takeIf { it.mode == "song" }?.songIds?.firstOrNull())
    }
    var selectedPlaylistId by remember(existing) {
        mutableStateOf(existing?.takeIf { it.mode == "playlist" }?.playlistId)
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun displayTitle(song: Song): String {
        val meta = customMetadata[song.id]
        return meta?.first?.ifBlank { song.title } ?: song.title
    }

    val selectedSong = library.songs.firstOrNull { it.id == selectedSongId }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val hasSelection = if (mode == "song") selectedSong != null else selectedPlaylist != null
    val exactOk = AlarmScheduler.canScheduleExact(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Despertador") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Despertador activado",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Hora",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(
                            "%d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "¿Con qué te despierto?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "song",
                        onClick = { mode = "song" },
                        label = { Text("Una canción") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = mode == "playlist",
                        onClick = { mode = "playlist" },
                        label = { Text("Una playlist") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            mode == "song" && selectedSong != null ->
                                displayTitle(selectedSong)
                            mode == "playlist" && selectedPlaylist != null ->
                                selectedPlaylist.name
                            mode == "song" -> "Elegir canción…"
                            else -> "Elegir playlist…"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Repetir todos los días",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = repeatDaily, onCheckedChange = { repeatDaily = it })
                }
                if (!exactOk) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tu teléfono no deja programar la alarma a la " +
                            "hora exacta: puede sonar unos minutos tarde. " +
                            "Activa el permiso para que suene puntual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }
                    ) { Text("Dar permiso") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasSelection,
                onClick = {
                    val config = if (mode == "song") {
                        AlarmConfig(
                            hour = hour,
                            minute = minute,
                            enabled = enabled,
                            repeatDaily = repeatDaily,
                            mode = "song",
                            songIds = listOfNotNull(selectedSongId),
                            playlistId = null,
                            label = selectedSong?.let { displayTitle(it) } ?: ""
                        )
                    } else {
                        AlarmConfig(
                            hour = hour,
                            minute = minute,
                            enabled = enabled,
                            repeatDaily = repeatDaily,
                            mode = "playlist",
                            songIds = selectedPlaylist?.songIds ?: emptyList(),
                            playlistId = selectedPlaylistId,
                            label = selectedPlaylist?.name ?: ""
                        )
                    }
                    viewModel.saveAlarm(config)
                    onDismiss()
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    // ---- Selector de hora ----
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Hora del despertador") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hour = timeState.hour
                        minute = timeState.minute
                        showTimePicker = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        )
    }

    // ---- Selector de canción o playlist ----
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(if (mode == "song") "Elegir canción" else "Elegir playlist") },
            text = {
                if (mode == "song") {
                    if (library.songs.isEmpty()) {
                        Text("No hay canciones en la biblioteca.")
                    } else {
                        LazyColumn(modifier = Modifier.height(420.dp)) {
                            items(library.songs, key = { it.id }) { song ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSongId = song.id
                                            showPicker = false
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = song.id == selectedSongId,
                                        onClick = {
                                            selectedSongId = song.id
                                            showPicker = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            displayTitle(song),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            song.artist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text("No tienes playlists todavía.")
                    } else {
                        LazyColumn(modifier = Modifier.height(420.dp)) {
                            items(playlists, key = { it.id }) { pl: Playlist ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlaylistId = pl.id
                                            showPicker = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = pl.id == selectedPlaylistId,
                                        onClick = {
                                            selectedPlaylistId = pl.id
                                            showPicker = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        pl.name,
                                        Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${pl.songIds.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cerrar") }
            }
        )
    }
}
