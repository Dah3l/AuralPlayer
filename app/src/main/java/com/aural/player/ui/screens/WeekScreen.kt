// Pantalla "Tu Top 10": las 10 canciones más escuchadas en Aural,
// con tarjeta para compartir. Los datos empiezan a contarse desde que
// se instala esta versión. (El archivo conserva el nombre WeekScreen.kt
// porque la pantalla nació como "Tu semana en Aural".)
package com.aural.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aural.player.AuralViewModel
import com.aural.player.data.TopSong
import kotlinx.coroutines.launch

@Composable
fun Top10Screen(
    onBack: () -> Unit,
    viewModel: AuralViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshTopSongs()
    }

    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
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
                    text = "Tu Top 10",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (topSongs.isEmpty()) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = "Todavía no hay un Top 10.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Escucha música y tus 10 canciones más " +
                            "escuchadas aparecerán aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Las canciones que más has escuchado en Aural",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    topSongs.forEachIndexed { index, song ->
                        Top10SongRow(rank = index + 1, song = song)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val bitmap = captureCard(context) {
                                        Top10CardContent(topSongs = topSongs)
                                    }
                                    shareCardImage(
                                        context = context,
                                        bitmap = bitmap,
                                        fileName = "aural-top10.png",
                                        chooserTitle = "Compartir mi Top 10"
                                    )
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "No se pudo compartir",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir mi Top 10")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Top10SongRow(rank: Int, song: TopSong) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatListeningTime(song.seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
