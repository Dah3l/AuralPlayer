// Diálogo del ecualizador: 5 bandas, presets y normalización de volumen
package com.aural.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aural.player.playback.EqualizerManager

/**
 * Ecualizador gráfico con presets y normalización (LoudnessEnhancer).
 * Los cambios se aplican en vivo y se guardan automáticamente.
 */
@Composable
fun EqualizerDialog(onDismiss: () -> Unit) {
    val manager = remember { EqualizerManager }
    var enabled by remember { mutableStateOf(manager.enabled) }
    var loudnessOn by remember { mutableStateOf(manager.loudnessEnabled) }
    var loudnessGain by remember { mutableIntStateOf(manager.getLoudnessGainDb()) }
    // Niveles por banda, para refrescar la UI al cambiar preset
    var refreshTick by remember { mutableIntStateOf(0) }

    if (!manager.isAvailable) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ecualizador") },
            text = { Text("Tu dispositivo no soporta el ecualizador del sistema.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        )
        return
    }

    val bandCount = manager.bandCount
    val range = manager.levelRangeDb()
    val presets = manager.presets

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ecualizador") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Interruptor principal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Activar ecualizador")
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            manager.setEnabled(it)
                        }
                    )
                }

                if (enabled) {
                    Spacer(Modifier.height(8.dp))
                    // Presets
                    if (presets.isNotEmpty()) {
                        Text(
                            "Preset",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            itemsIndexed(presets) { i, name ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        manager.usePreset(i)
                                        refreshTick++
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    // Bandas (refreshTick fuerza recomposición tras preset)
                    @Suppress("UNUSED_EXPRESSION")
                    refreshTick
                    for (band in 0 until bandCount) {
                        val freq = manager.bandFrequencyHz(band)
                        val label = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
                        var level by remember(refreshTick) {
                            mutableIntStateOf(manager.getBandLevelDb(band))
                        }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${if (level > 0) "+" else ""}${level}dB",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Slider(
                                value = level.toFloat(),
                                onValueChange = {
                                    level = it.toInt()
                                    manager.setBandLevelDb(band, level)
                                },
                                valueRange = range.first.toFloat()..range.last.toFloat(),
                                steps = (range.last - range.first) - 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Normalización de volumen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Normalizar volumen")
                        Text(
                            "Iguala el volumen entre canciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = loudnessOn,
                        onCheckedChange = {
                            loudnessOn = it
                            manager.setLoudnessEnabled(it)
                        }
                    )
                }
                if (loudnessOn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Ganancia",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("+${loudnessGain}dB", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = loudnessGain.toFloat(),
                        onValueChange = {
                            loudnessGain = it.toInt()
                            manager.setLoudnessGainDb(loudnessGain)
                        },
                        valueRange = 0f..10f,
                        steps = 9
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
