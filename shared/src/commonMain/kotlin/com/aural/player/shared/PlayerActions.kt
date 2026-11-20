// Acciones del reproductor: el contrato entre la UI compartida y el motor
// de cada plataforma. La UI solo observa PlaybackState y manda
// intenciones por aquí; el motor (DesktopPlayer en PC) ejecuta.
package com.aural.player.shared

/** Resultado de editar la información de una canción. */
enum class EditResult {
    /** Los tags quedaron escritos en el archivo. */
    SAVED_FILE,
    /** El archivo no se pudo escribir: los cambios viven solo en AuralPlayer. */
    SAVED_OVERLAY,
    /** No se pudo guardar de ninguna forma. */
    FAILED
}

/** Un resultado de búsqueda de YouTube (solo audio). */
data class YoutubeResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnailUrl: String?
)

/** Estado de una descarga de YouTube. */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    DONE,
    FAILED
}

/** Una descarga de YouTube en curso o terminada. */
data class YoutubeDownload(
    val videoId: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float,
    val error: String? = null
)

interface PlayerActions {
    // ---- Transporte ----
    fun playFrom(list: List<Song>, index: Int)
    fun togglePlay()
    fun next()
    fun previous()
    fun seek(fraction: Float)
    fun setVolume(value: Int)
    fun toggleMute()
    fun setTheme(theme: AppTheme)

    // ---- Isla flotante ----
    /** Isla flotante: visible o no. */
    fun setIslandEnabled(enabled: Boolean)
    /** Cometa de Aural Light en la isla: encendido o no. */
    fun setCometEnabled(enabled: Boolean)
    /** Apariencia de la isla: "auto" (tema) o "classic" (negra). */
    fun setIslandAppearance(appearance: String)

    // ---- Cola ----
    fun enqueue(song: Song)
    fun enqueueAll(songs: List<Song>)
    fun removeFromQueue(index: Int)
    fun moveQueueItem(from: Int, to: Int)

    // ---- Modo de reproducción ----
    fun cyclePlaybackMode()
    fun setPlaybackSpeed(speed: Float)

    // ---- Temporizador y parada programada ----
    fun startSleepTimer(minutes: Int)
    fun setStopMode(mode: StopMode)
    fun cancelSleepTimer()

    // ---- Ecualizador ----
    fun applyEqualizer(enabled: Boolean, preamp: Float, amps: List<Float>)

    // ---- Carpetas ----
    fun addFolder()
    fun removeFolder(path: String)

    // ---- Playlists ----
    fun createPlaylist(name: String)
    fun createPlaylistAndAdd(name: String, song: Song)
    fun createPlaylistAndAddAll(name: String, songs: List<Song>)
    fun renamePlaylist(id: String, name: String)
    fun deletePlaylist(id: String)
    fun addToPlaylist(id: String, song: Song)
    fun addAllToPlaylist(id: String, songs: List<Song>)
    fun removeFromPlaylist(id: String, song: Song)

    // ---- Favoritas y borrado ----
    fun toggleFavorite(song: Song)
    fun addFavorites(songs: List<Song>)
    fun deleteSongs(songs: List<Song>)

    // ---- Portadas, información y letras ----
    fun loadCoverBytes(song: Song): ByteArray?
    fun hasCustomCover(song: Song): Boolean
    fun pickCustomCover(song: Song): Boolean
    fun removeCustomCover(song: Song)
    /** Descarga la portada de una URL y la guarda como personalizada. */
    suspend fun saveCoverFromUrl(song: Song, url: String): Boolean
    fun editMetadata(song: Song, title: String, artist: String, album: String): EditResult
    suspend fun loadLyrics(song: Song): String?
    suspend fun searchAndSaveLyrics(song: Song, track: String, artist: String): Boolean
    fun pickLrcFile(song: Song): Boolean

    // ---- Historial ----
    suspend fun loadTopSongs(limit: Int): List<TopSong>

    // ---- Descargas de YouTube ----
    suspend fun searchYouTube(query: String): List<YoutubeResult>
    fun downloadYouTube(result: YoutubeResult)
    fun cancelDownload(videoId: String)
    fun clearYouTubeError()

    // ---- Sistema ----
    fun copyText(text: String)
    fun revealInExplorer(path: String)
    fun openUrl(url: String)
    fun exportBackupToFile(): Boolean
    fun importBackupFromFile(): Boolean
    fun clearAllData(): Boolean
    fun playShuffled(list: List<Song>)
}
