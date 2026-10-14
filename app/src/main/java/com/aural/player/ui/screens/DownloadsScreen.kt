// Pantalla de descargas vía bot de Telegram
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aural.player.telegram.BotSong
import com.aural.player.telegram.TelegramAuthState
import com.aural.player.telegram.TelegramManager

/**
 * Sección de descargas: busca canciones vía el bot de Telegram y las descarga.
 */
@Composable
fun DownloadsScreen(
    telegramManager: TelegramManager,
    onAuthNeeded: () -> Unit,
    onDownloadComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val authState by telegramManager.authState.collectAsState()
    val results by telegramManager.botResponses.collectAsState()
    val buttons by telegramManager.botButtons.collectAsState()

    var query by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (authState !is TelegramAuthState.Ready) {
            // No autenticado: pedir login
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Vincula tu Telegram para descargar música",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onAuthNeeded) {
                    Text("Conectar Telegram")
                }
            }
            return@Column
        }

        // Buscador
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Nombre de la canción...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, "Borrar")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        TextButton(
            onClick = { telegramManager.searchSong(query) },
            enabled = query.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Buscar en Telegram")
        }

        Spacer(Modifier.height(8.dp))

        // Botones del bot (resultados para elegir)
        if (buttons.isNotEmpty()) {
            Text(
                "Toca una canción para descargarla:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(buttons, key = { it.label + it.messageId }) { button ->
                TextButton(
                    onClick = { telegramManager.pressButton(button) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        button.label,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        }

        // Resultados (audios recibidos)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results, key = { it.messageId }) { song ->
                BotSongRow(
                    song = song,
                    isDownloading = downloading.contains(song.messageId),
                    onDownload = {
                        downloading = downloading + song.messageId
                        telegramManager.downloadSong(song) { success ->
                            downloading = downloading - song.messageId
                            if (success) onDownloadComplete()
                        }
                    }
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Busca una canción para ver resultados del bot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BotSongRow(
    song: BotSong,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                "${song.performer} • ${song.durationSec / 60}:${String.format("%02d", song.durationSec % 60)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Descargar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
