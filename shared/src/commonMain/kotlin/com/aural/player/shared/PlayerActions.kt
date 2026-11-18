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
    fun editMetadata(song: Song, title: String, artist: String, album: String): EditResult
    suspend fun loadLyrics(song: Song): String?
    suspend fun searchAndSaveLyrics(song: Song, track: String, artist: String): Boolean
    fun pickLrcFile(song: Song): Boolean

    // ---- Historial ----
    suspend fun loadTopSongs(limit: Int): List<TopSong>

    // ---- Sistema ----
    fun copyText(text: String)
    fun revealInExplorer(path: String)
    fun playShuffled(list: List<Song>)
}
