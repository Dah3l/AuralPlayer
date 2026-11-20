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
    val eqBandFrequencies: List<Float> = emptyList(),
    /** Descargas de YouTube: resultados de la última búsqueda. */
    val youTubeResults: List<YoutubeResult> = emptyList(),
    /** Búsqueda de YouTube en curso. */
    val youTubeSearching: Boolean = false,
    /** Error de búsqueda o descarga de YouTube. */
    val youTubeError: String? = null,
    /** Descargas de YouTube en curso o terminadas. */
    val downloads: List<YoutubeDownload> = emptyList(),
    /** Isla flotante: visible. */
    val islandEnabled: Boolean = true,
    /** Cometa de Aural Light en la isla. */
    val cometEnabled: Boolean = true,
    /** Apariencia de la isla: "auto" (tema) o "classic" (negra). */
    val islandAppearance: String = "auto",
    /** Color de los botones de la isla ("#RRGGBB") o null = primario del tema. */
    val islandButtonColorHex: String? = null,
    /** Color del cometa Aural Light ("#RRGGBB") o null = primario del tema. */
    val islandCometColorHex: String? = null
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}
