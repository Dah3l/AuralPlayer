// Pantalla propia del ecualizador (v1.0.7): página dedicada en vez de modal.
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aural.player.playback.EqualizerManager

/**
 * v1.0.7: el ecualizador tiene página propia. Incluye las 5 bandas con
 * presets, normalización de volumen, refuerzo de bajos (BassBoost) y
 * audio inmersivo (Virtualizer) cuando el dispositivo los soporta.
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val manager = remember { EqualizerManager }
    var enabled by remember { mutableStateOf(manager.enabled) }
    var loudnessOn by remember { mutableStateOf(manager.loudnessEnabled) }
    var loudnessGain by remember { mutableIntStateOf(manager.getLoudnessGainDb()) }
    var bassOn by remember { mutableStateOf(manager.bassBoostEnabled) }
    var bassStrength by remember { mutableIntStateOf(manager.getBassBoostStrength()) }
    var virtualOn by remember { mutableStateOf(manager.virtualizerEnabled) }
    var virtualStrength by remember { mutableIntStateOf(manager.getVirtualizerStrength()) }
    var refreshTick by remember { mutableIntStateOf(0) }

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
                text = "Ecualizador",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    manager.setEnabled(it)
                },
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        if (!manager.isAvailable) {
            Text(
                text = "Tu dispositivo no soporta el ecualizador del sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (enabled) {
                // ---- Presets ----
                var selectedPreset by remember { mutableIntStateOf(manager.getCurrentPreset()) }
                val presets = manager.presets
                if (presets.isNotEmpty()) {
                    EffectCard(title = "Preset") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(presets) { i, name ->
                                FilterChip(
                                    selected = i == selectedPreset,
                                    onClick = {
                                        manager.usePreset(i)
                                        selectedPreset = i
                                        refreshTick++
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }

                // ---- Bandas ----
                @Suppress("UNUSED_EXPRESSION")
                refreshTick
                EffectCard(title = "Bandas de frecuencia") {
                    val bandCount = manager.bandCount
                    val range = manager.levelRangeDb()
                    for (band in 0 until bandCount) {
                        val freq = manager.bandFrequencyHz(band)
                        val label = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
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
                                    "${if (level > 0) "+" else ""}${level} dB",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = level.toFloat(),
                                onValueChange = {
                                    level = it.toInt()
                                    manager.setBandLevelDb(band, level)
                                    selectedPreset = -1
                                },
                                valueRange = range.first.toFloat()..range.last.toFloat(),
                                steps = (range.last - range.first) - 1
                            )
                        }
                    }
                }
            }

            // ---- Refuerzo de bajos ----
            if (manager.isBassBoostAvailable) {
                EffectCard(title = "Refuerzo de bajos") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Más pegada en los bajos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = bassOn,
                            onCheckedChange = {
                                bassOn = it
                                manager.setBassBoostEnabled(it)
                            }
                        )
                    }
                    if (bassOn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Intensidad",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${bassStrength / 10}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = bassStrength.toFloat(),
                            onValueChange = {
                                bassStrength = it.toInt()
                                manager.setBassBoostStrength(bassStrength)
                            },
                            valueRange = 0f..1000f
                        )
                    }
                }
            }

            // ---- Audio inmersivo ----
            if (manager.isVirtualizerAvailable) {
                EffectCard(title = "Audio inmersivo") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sonido envolvente virtual",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = virtualOn,
                            onCheckedChange = {
                                virtualOn = it
                                manager.setVirtualizerEnabled(it)
                            }
                        )
                    }
                    if (virtualOn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Intensidad",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${virtualStrength / 10}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = virtualStrength.toFloat(),
                            onValueChange = {
                                virtualStrength = it.toInt()
                                manager.setVirtualizerStrength(virtualStrength)
                            },
                            valueRange = 0f..1000f
                        )
                    }
                }
            }

            // ---- Normalización ----
            EffectCard(title = "Normalizar volumen") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Iguala el volumen entre canciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
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
                        Text(
                            "+${loudnessGain} dB",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
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

            Spacer(Modifier.height(32.dp))
        }
    }
    }
}
}

/** Tarjeta de un efecto con título. */
@Composable
private fun EffectCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
