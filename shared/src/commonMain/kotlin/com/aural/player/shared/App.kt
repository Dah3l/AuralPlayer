package com.aural.player.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun App(
    state: PlaybackState,
    onSelectSong: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onThemeChange: (AppTheme) -> Unit
) {
    AuralTheme(theme = state.theme) {
        val scheme = MaterialTheme.colorScheme
        var themeMenuExpanded by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aural",
                    color = scheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Player",
                    color = scheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "El sonido, sin ruido.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (state.scanning) {
                    Spacer(modifier = Modifier.width(16.dp))
                    CircularProgressIndicator(
                        color = scheme.primary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Buscando música…",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { themeMenuExpanded = true }) {
                        Text(
                            text = state.theme.displayName,
                            color = scheme.primary,
                            fontSize = 13.sp
                        )
                    }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        AppTheme.entries.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(12.dp)
                                                .background(item.colorScheme().primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(item.displayName)
                                    }
                                },
                                onClick = {
                                    onThemeChange(item)
                                    themeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.scanning && state.songs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = scheme.primary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Buscando tu música…", color = scheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                    !state.scanning && state.songs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No se encontraron canciones en tu carpeta Música.",
                                color = scheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.songs, key = { _, song -> song.filePath }) { index, song ->
                                SongRow(
                                    song = song,
                                    isCurrent = index == state.currentIndex,
                                    isPlaying = index == state.currentIndex && state.isPlaying,
                                    onClick = { onSelectSong(index) }
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = scheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            state.currentSong?.let { song ->
                PlayerBar(
                    song = song,
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    volume = state.volume,
                    muted = state.muted,
                    onVolumeChange = onVolumeChange,
                    onToggleMute = onToggleMute
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) scheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (isPlaying) {
            Text(text = "♪", color = scheme.primary, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = formatTime(song.durationMs), color = scheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun PlayerBar(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    volume: Int,
    muted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Mientras se arrastra, la barra sigue el dedo y solo se busca
    // al soltar: buscar en cada tick entrecorta la reproducción.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    Column(
        modifier = Modifier.fillMaxWidth().background(scheme.surface).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TransportButton(text = "⏮", onClick = onPrevious)
            TransportButton(text = if (isPlaying) "⏸" else "▶", onClick = onTogglePlay, prominent = true)
            TransportButton(text = "⏭", onClick = onNext)
            Spacer(modifier = Modifier.width(8.dp))
            TransportButton(
                text = if (muted || volume == 0) "🔇" else "🔊",
                onClick = onToggleMute
            )
            Slider(
                value = volume / 100f,
                onValueChange = { onVolumeChange((it * 100).roundToInt().coerceIn(0, 100)) },
                modifier = Modifier.width(110.dp),
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.outlineVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatTime(positionMs), color = scheme.onSurfaceVariant, fontSize = 12.sp)
            Slider(
                value = if (dragging) dragValue else if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragValue = it; dragging = true },
                onValueChangeFinished = { dragging = false; onSeek(dragValue) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.outlineVariant
                )
            )
            Text(text = formatTime(durationMs), color = scheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransportButton(text: String, onClick: () -> Unit, prominent: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = if (prominent) scheme.primary else scheme.onSurface,
            fontSize = if (prominent) 26.sp else 20.sp
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
