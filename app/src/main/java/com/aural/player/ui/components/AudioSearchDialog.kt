// Diálogo de reconocimiento de música por audio (estilo Shazam)
package com.aural.player.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aural.player.data.AcrCloudClient
import com.aural.player.data.AcrCloudConfig
import com.aural.player.data.RecognizedTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val RECORD_SECONDS = 8

private enum class AudioSearchState { IDLE, RECORDING, IDENTIFYING, RESULT, ERROR, NOT_CONFIGURED }

/**
 * Diálogo para identificar la canción que suena alrededor.
 * Graba [RECORD_SECONDS] segundos y los envía a ACRCloud.
 */
@Composable
fun AudioSearchDialog(
    onDismiss: () -> Unit,
    onSearchYouTube: (query: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AudioSearchState.IDLE) }
    var result by remember { mutableStateOf<RecognizedTrack?>(null) }
    var secondsLeft by remember { mutableIntStateOf(RECORD_SECONDS) }
    var recordJob by remember { mutableStateOf<Job?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening(
            context, scope,
            onState = { state = it },
            onSeconds = { secondsLeft = it },
            onResult = { result = it },
            onJob = { recordJob = it }
        )
    }

    // Detener la grabación si se cierra el diálogo
    DisposableEffect(Unit) {
        onDispose { recordJob?.cancel() }
    }

    fun begin() {
        if (!AcrCloudConfig.isConfigured) {
            state = AudioSearchState.NOT_CONFIGURED
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening(
                context, scope,
                onState = { state = it },
                onSeconds = { secondsLeft = it },
                onResult = { result = it },
                onJob = { recordJob = it }
            )
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
                            if (track.album.isNotBlank()) {
                                Text(
                                    track.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                        Text(
                            "No pude identificar la canción. Inténtalo de nuevo " +
                                "con menos ruido de fondo.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { begin() }) { Text("Reintentar") }
                    }
                    AudioSearchState.NOT_CONFIGURED -> {
                        Text(
                            "Falta configurar ACRCloud. Crea una cuenta gratuita en " +
                                "acrcloud.com y pega tus credenciales en " +
                                "AcrCloudConfig (data/AcrCloudClient.kt).",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
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

/** Micrófono con animación de pulso mientras graba. */
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

private fun startListening(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onState: (AudioSearchState) -> Unit,
    onSeconds: (Int) -> Unit,
    onResult: (RecognizedTrack?) -> Unit,
    onJob: (Job?) -> Unit
) {
    val job = scope.launch {
        var recorder: MediaRecorder? = null
        try {
            val file = File(context.cacheDir, "acr_sample.m4a")
            if (file.exists()) file.delete()

            @Suppress("DEPRECATION")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            onState(AudioSearchState.RECORDING)
            for (s in RECORD_SECONDS downTo 1) {
                onSeconds(s)
                delay(1000)
            }
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null

            onState(AudioSearchState.IDENTIFYING)
            val track = AcrCloudClient.identify(file)
            file.delete()
            if (track != null) {
                onResult(track)
                onState(AudioSearchState.RESULT)
            } else {
                onState(AudioSearchState.ERROR)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioSearch", "Error grabando: ${e.message}")
            runCatching { recorder?.release() }
            onState(AudioSearchState.ERROR)
        } finally {
            onJob(null)
        }
    }
    onJob(job)
}
