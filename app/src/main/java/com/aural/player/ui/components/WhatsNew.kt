// Novedades y descubrimiento: modal de "qué hay de nuevo" al actualizar
// y puntitos pulsantes del color del tema sobre las funciones nuevas,
// que desaparecen al interactuar con cada una.
package com.aural.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Claves de las funciones nuevas (una por puntito). */
object FeatureKeys {
    const val TOP10 = "top10"
    const val TRANSLATE = "translate"
    const val ALARM = "alarm"
    const val BACKUP = "backup"
    const val STOP_MODE = "stop_mode"
    const val SHARE_NOW_PLAYING = "share_now_playing"
}

/** Puntito del color del tema con un pulso suave. Nunca se congela. */
@Composable
fun FeatureDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dotPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

/** Modal "Novedades": qué trae la versión recién instalada. */
@Composable
fun NewsModal(
    versionName: String,
    onDismiss: () -> Unit
) {
    val items = listOf(
        "Tu Top 10" to "Tus 10 canciones más escuchadas, con tarjeta para compartir y guardar.",
        "Traducción de letras" to "Lee la letra en español, sin conexión tras descargar los modelos una vez.",
        "Despertador" to "Despiértate con tu música, todos los días o solo una vez.",
        "Respaldo" to "Guarda tus playlists, favoritos y ajustes en un archivo y restáuralos cuando quieras.",
        "Apagar al terminar" to "La música se detiene sola al terminar la canción o la lista.",
        "Compartir Escuchando ahora" to "Una imagen con la portada y la canción, con Aural link opcional.",
        "Saludo personalizado" to "La app te saluda por tu nombre y te felicita en tu cumpleaños."
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Novedades",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Versión $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                items.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¡A explorar!")
                }
            }
        }
    }
}
