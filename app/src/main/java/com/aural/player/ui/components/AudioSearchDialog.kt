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
import androidx.compose.foundation.layout.Arrangement
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
private val ASK_AT = intArrayOf(4, 7, 10, 13)
/** Mensajes warm mientras escucha (aprobados por Dahel). */
private val LISTENING_MESSAGES = listOf(
    "Escuchando... 🎵",
    "A ver qué está sonando por ahí...",
    "Captando la vibra... 🎧"
)
/** Mensajes chistosos si se acaba el tiempo sin resultados. */
private val NO_LUCK_MESSAGES = listOf(
    "Hmm... ¿seguro que eso es música? 😅",
    "Ni con café encuentro esto... ☕",
    "¿Estás cantando o es el vecino con el taladro? 🔨😂"
)
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
    var recordJob by remember { mutableStateOf<Job?>(null) }

    fun startListening() {
        recordJob = scope.launch {
            try {
                state = AudioSearchState.RECORDING
                // Búsqueda incremental: graba por chunks y pregunta a los 4, 7, 10, 13 seg
                val recognitionResult: ShazamResult? = withContext(Dispatchers.IO) {
                    incrementalRecognize { /* contador oculto */ }
                }
                if (recognitionResult == null) {
                    errorDetail = "No se pudo acceder al micrófono"
                    state = AudioSearchState.ERROR
                    return@launch
                }
                state = AudioSearchState.IDENTIFYING
                when (recognitionResult) {
                    is ShazamResult.Found -> {
                        result = recognitionResult.track
                        state = AudioSearchState.RESULT
                    }
                    is ShazamResult.NoMatch -> {
                        errorDetail = recognitionResult.detail
                        state = AudioSearchState.ERROR
                    }
                    is ShazamResult.Error -> {
                        errorDetail = recognitionResult.detail
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                        // Mensaje warm rotativo (sin contador visible)
                        val msgIndex = remember { (LISTENING_MESSAGES.indices).random() }
                        Text(
                            LISTENING_MESSAGES[msgIndex],
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
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
                        // Mensaje amigable según el tipo de error (sin diagnósticos técnicos)
                        val isHttpError = errorDetail.contains("HTTP 4") ||
                            errorDetail.contains("HTTP 5") ||
                            errorDetail.contains("403")
                        val funnyMsg = remember { NO_LUCK_MESSAGES.random() }
                        if (isHttpError) {
                            Text(
                                "🌐",
                                style = MaterialTheme.typography.displayMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No hay conexión con Shazam.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Prueba usar VPN y reintenta.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                funnyMsg,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { begin() }) { Text("Reintentar") }
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


/**
 * Reconocimiento incremental estilo opentune: graba audio por chunks de 0.1s,
 * alimenta la firma de Shazam incrementalmente y consulta a los 4, 7, 10 y 13
 * segundos. Devuelve el primer resultado exitoso, o el último intento.
 * Devuelve null si falla el micrófono.
 */
private suspend fun incrementalRecognize(
    onTick: (Int) -> Unit
): ShazamResult? = withContext(Dispatchers.IO) {
    val minBuffer = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuffer <= 0) return@withContext null

    val recorder = try {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE / 5)
        )
    } catch (e: Exception) {
        return@withContext null
    }
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return@withContext null
    }

    val gen = ShazamSignatureGenerator()
    val chunk = ShortArray(SAMPLE_RATE / 10) // 0.1 segundos
    var totalSamples = 0
    var askIdx = 0
    var lastResult: ShazamResult? = null

    try {
        recorder.startRecording()
        val deadline = System.currentTimeMillis() + RECORD_SECONDS * 1000L

        while (System.currentTimeMillis() < deadline) {
            val n = recorder.read(chunk, 0, chunk.size)
            if (n > 0) {
                gen.feedSamples(if (n == chunk.size) chunk else chunk.copyOf(n))
                totalSamples += n
            } else if (n < 0) {
                break
            }

            val elapsedSec = totalSamples / SAMPLE_RATE
            onTick((RECORD_SECONDS - elapsedSec).coerceAtLeast(0))

            if (askIdx < ASK_AT.size && elapsedSec >= ASK_AT[askIdx]) {
                askIdx++
                when (val r = ShazamClient.lookup(gen)) {
                    is ShazamResult.Found -> return@withContext r
                    else -> lastResult = r
                }
            }
            kotlinx.coroutines.delay(50)
        }

        // Consulta final si no se completaron todas
        if (askIdx < ASK_AT.size) {
            return@withContext ShazamClient.lookup(gen)
        }
        return@withContext lastResult ?: ShazamResult.NoMatch("Sin coincidencia")
    } catch (e: Exception) {
        return@withContext ShazamResult.Error("${e.javaClass.simpleName}: ${e.message}")
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}
