package com.aural.player.shared

/** Estado completo de la pantalla principal de escritorio. */
data class PlaybackState(
    val songs: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val scanning: Boolean = true,
    val error: String? = null,
    val volume: Int = 100,
    val muted: Boolean = false
) {
    val currentSong: Song? get() = songs.getOrNull(currentIndex)
}
