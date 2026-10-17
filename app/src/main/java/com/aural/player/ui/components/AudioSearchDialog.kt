// Diálogo de reconocimiento de música por audio (vía Shazam)
package com.aural.player.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aural.player.data.shazam.ShazamClient
import com.aural.player.data.shazam.ShazamSignatureGenerator
import com.aural.player.data.shazam.ShazamResult

import com.aural.player.data.shazam.ShazamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECORD_SECONDS = 14
/** Momentos (en segundos) en los que se pregunta a Shazam, como opentune. */
private val ASK_AT_SECONDS = intArrayOf(4, 7, 10, 13)
private const val SAMPLE_RATE = 16000

private enum class AudioSearchState { IDLE, RECORDING, IDENTIFYING, RESULT, ERROR }

@Composable
fun AudioSearchDialog(
    onDismiss: () -> Unit,
    onSearchYouTube: (query: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AudioSearchState.IDLE) }
    var result by remember { mutableStateOf<ShazamTrack?>(null) }
    var errorDetail by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(RECORD_SECONDS) }
    var recordJob by remember { mutableStateOf<Job?>(null) }

    fun startListening() {
        recordJob = scope.launch {
            try {
                state = AudioSearchState.RECORDING
                // Búsqueda incremental como opentune: graba por chunks,
                // alimenta la firma y pregunta a Shazam a los 4, 7, 10 y 13 seg.
                val found = withContext(Dispatchers.IO) {
                    recordAndRecognizeIncremental { secondsLeft = it }
                }
                when (found) {
                    is ShazamResult.Found -> {
                        result = found.track
                        state = AudioSearchState.RESULT
                    }
                    is ShazamResult.NoMatch -> {
                        errorDetail = found.detail
                        state = AudioSearchState.ERROR
                    }
                    is ShazamResult.Error -> {
                        errorDetail = found.detail
                        state = AudioSearchState.ERROR
                    }
                    null -> {
                        errorDetail = "No se pudo acceder al micrófono"
                        state = AudioSearchState.ERROR
                    }
                }
            } catch (e: Exception) {
                errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                state = AudioSearchState.ERROR
            } finally {
                recordJob = null
            }
        }
    }

    /**
     * Graba audio por chunks, alimenta la firma incrementalmente y
     * pregunta a Shazam a los 4, 7, 10 y 13 segundos. Para en el
     * primer resultado exitoso. Devuelve null si falla el micrófono.
     */
    suspend fun recordAndRecognizeIncremental(
        onTick: (Int) -> Unit
    ): ShazamResult? = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
        if (minBuffer <= 0) return@withContext null

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                channelConfig,
                audioFormat,
                maxOf(minBuffer, SAMPLE_RATE / 10 * 2)
            )
        } catch (e: Exception) {
            return@withContext null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }

        val gen = ShazamSignatureGenerator()
        // Chunk de 0.1 segundos (1600 muestras), como opentune
        val chunkSize = SAMPLE_RATE / 10
        val chunk = ShortArray(chunkSize)
        var totalSamples = 0
        var askIndex = 0
        var lastResult: ShazamResult? = null

        try {
            recorder.startRecording()
            val endTime = System.currentTimeMillis() + RECORD_SECONDS * 1000L

            while (System.currentTimeMillis() < endTime) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    // Alimentar la firma incrementalmente
                    val toFeed = if (read == chunk.size) chunk else chunk.copyOf(read)
                    gen.feedSamples(toFeed)
                    totalSamples += read
                } else if (read < 0) {
                    break
                }

                val elapsedSec = totalSamples / SAMPLE_RATE
                onTick((RECORD_SECONDS - elapsedSec).coerceAtLeast(0))

                // ¿Toca preguntar a Shazam?
                if (askIndex < ASK_AT_SECONDS.size && elapsedSec >= ASK_AT_SECONDS[askIndex]) {
                    askIndex++
                    // Generar firma con lo grabado hasta ahora y consultar
                    when (val r = ShazamClient.lookup(gen)) {
                        is ShazamResult.Found -> {
                            // ¡Encontrado! Parar.
                            return@withContext r
                        }
                        is ShazamResult.NoMatch -> {
                            lastResult = r
                            // Seguir grabando, quizás con más audio sí matchea
                        }
                        is ShazamResult.Error -> {
                            lastResult = r
                            // Si es error de red, seguir intentando puede no servir,
                            // pero continuamos por si fue temporal
                        }
                    }
                }

                // Pequeña pausa para no saturar
                kotlinx.coroutines.delay(50)
            }

            // Si llegamos aquí sin éxito, devolver el último resultado
            // o hacer una última consulta con todo el audio
            if (askIndex < ASK_AT_SECONDS.size) {
                // No se llegó a preguntar todas las veces, hacer consulta final
                return@withContext ShazamClient.lookup(gen)
            }
            return@withContext lastResult
                ?: ShazamResult.NoMatch("No se encontró coincidencia")
        } catch (e: Exception) {
            return@withContext ShazamResult.Error("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else {
            errorDetail = "Permiso de micrófono denegado"
            state = AudioSearchState.ERROR
        }
    }

    DisposableEffect(Unit) {
        onDispose { recordJob?.cancel() }
    }

    fun begin() {
        errorDetail = ""
        result = null
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    AlertDialog(
        onDismissRequest = {
            recordJob?.cancel()
            onDismiss()
        },
        title = { Text("Identificar canción") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    AudioSearchState.IDLE -> {
                        Text(
                            "Toca el micrófono y acerca el teléfono a la música",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        IconButton(
                            onClick = { begin() },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic, "Escuchar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    AudioSearchState.RECORDING -> {
                        PulsingMic()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Escuchando… $secondsLeft",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AudioSearchState.IDENTIFYING -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Identificando la canción…")
                    }
                    AudioSearchState.RESULT -> {
                        val track = result
                        if (track != null) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                track.artist.ifBlank { "Artista misterioso" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                val q = listOf(track.artist, track.title)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" - ")
                                onSearchYouTube(q)
                            }) {
                                Text("Descargar de YouTube")
                            }
                        }
                    }
                    AudioSearchState.ERROR -> {
                        val clipboard = LocalClipboardManager.current
                        var showDiag by remember { mutableStateOf(false) }
                        Text(
                            "No pude identificar la canción.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (errorDetail.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Detalle: $errorDetail",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { begin() }) { Text("Reintentar") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showDiag = true }) {
                            Text("Ver diagnóstico")
                        }
                        if (showDiag) {
                            AlertDialog(
                                onDismissRequest = { showDiag = false },
                                title = { Text("Diagnóstico") },
                                text = {
                                    Text(
                                        ShazamClient.lastDiagnostic.ifBlank { "Sin datos" },
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        clipboard.setText(AnnotatedString(ShazamClient.lastDiagnostic))
                                        showDiag = false
                                    }) { Text("Copiar") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDiag = false }) { Text("Cerrar") }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                recordJob?.cancel()
                onDismiss()
            }) { Text("Cerrar") }
        }
    )
}

@Composable
private fun PulsingMic() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )
    Icon(
        Icons.Rounded.Mic, "Escuchando",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(64.dp)
            .scale(scale)
    )
}

