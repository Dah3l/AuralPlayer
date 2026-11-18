package com.aural.player.shared

/** Modo de repetición: el botón de modo cicla OFF → ALL → ONE → aleatorio. */
enum class RepeatMode { OFF, ALL, ONE }

/** Parada programada: al terminar la canción o al terminar la lista. */
enum class StopMode { SONG, QUEUE }

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
    val playlists: List<Playlist> = emptyList(),
    /** Favoritas: rutas de archivo marcadas con el corazón. */
    val favorites: Set<String> = emptySet(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    /** Minutos del temporizador de suspensión, si hay uno activo. */
    val sleepMinutes: Int? = null,
    val stopMode: StopMode? = null,
    /** Ecualizador: activado, preamp y ganancia por banda (dB, -12..12). */
    val eqEnabled: Boolean = false,
    val eqPreamp: Float = 0f,
    val eqAmps: List<Float> = emptyList(),
    /** Frecuencias centrales de las bandas (Hz), las reporta VLC. */
    val eqBandFrequencies: List<Float> = emptyList()
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}
