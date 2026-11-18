// Portadas: personalizadas (elegidas por el usuario, mandan sobre todo) e
// incrustadas (caché que llena el escaneo desde los tags del archivo).
// Una por canción, nombrada por su id estable.
package com.aural.player.shared

class CoverStore(private val directory: String) {

    private fun customPath(songId: Long) = "$directory/covers/custom/$songId.jpg"
    private fun embeddedPath(songId: Long) = "$directory/covers/embedded/$songId.jpg"

    /** Carpeta donde el escaneo deja el arte incrustado que encuentra. */
    fun embeddedDir(): String = "$directory/covers/embedded"

    /** La portada que toca mostrar: personalizada primero, incrustada después. */
    fun load(songId: Long): ByteArray? =
        LocalFiles.readBytes(customPath(songId)) ?: LocalFiles.readBytes(embeddedPath(songId))

    fun hasCustom(songId: Long): Boolean = LocalFiles.exists(customPath(songId))

    fun saveCustom(songId: Long, bytes: ByteArray) {
        LocalFiles.mkdirs("$directory/covers/custom")
        LocalFiles.writeBytes(customPath(songId), bytes)
    }

    fun removeCustom(songId: Long) {
        LocalFiles.deleteFile(customPath(songId))
    }
}
