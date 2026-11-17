package com.aural.player.shared

/** Estado completo de la pantalla principal de escritorio. */
data class PlaybackState(
    val songs: List<Song> = emptyList(),
    /** Cola actual: la lista desde la que se está reproduciendo. */
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val scanning: Boolean = true,
    val error: String? = null,
    val volume: Int = 100,
    val muted: Boolean = false,
    val theme: AppTheme = AppTheme.BOSQUE,
    val folders: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}
