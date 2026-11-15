package com.aural.player.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AuralTeal = Color(0xFF2ED3B7)
private val AuralBg = Color(0xFF121212)
private val AuralSurface = Color(0xFF1C1C1C)
private val AuralLine = Color(0xFF2A2A2A)
private val AuralMuted = Color(0xFF9A9A9A)

@Composable
fun App(
    state: PlaybackState,
    onSelectSong: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AuralTeal,
            background = AuralBg,
            surface = AuralSurface,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(AuralBg)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aural",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Player",
                    color = AuralTeal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "El sonido, sin ruido.",
                    color = AuralMuted,
                    fontSize = 13.sp
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AuralLine))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.scanning -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AuralTeal, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Buscando tu música…", color = AuralMuted, fontSize = 14.sp)
                            }
                        }
                    }
                    state.songs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No se encontraron canciones en tu carpeta Música.",
                                color = AuralMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.songs) { index, song ->
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
                    color = Color(0xFFFF8A80),
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
                    onSeek = onSeek
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) AuralSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) AuralTeal else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuralMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (isPlaying) {
            Text(text = "♪", color = AuralTeal, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = formatTime(song.durationMs), color = AuralMuted, fontSize = 13.sp)
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
    onSeek: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(AuralSurface).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = AuralMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TransportButton(text = "⏮", onClick = onPrevious)
            TransportButton(text = if (isPlaying) "⏸" else "▶", onClick = onTogglePlay, prominent = true)
            TransportButton(text = "⏭", onClick = onNext)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatTime(positionMs), color = AuralMuted, fontSize = 12.sp)
            Slider(
                value = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = onSeek,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AuralTeal,
                    activeTrackColor = AuralTeal,
                    inactiveTrackColor = AuralLine
                )
            )
            Text(text = formatTime(durationMs), color = AuralMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransportButton(text: String, onClick: () -> Unit, prominent: Boolean = false) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = if (prominent) AuralTeal else Color.White,
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
