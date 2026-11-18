// Letras .lrc guardadas por canción: búsqueda en línea o archivo local
// elegido por el usuario. Una por canción, nombrada por su id estable.
package com.aural.player.shared

class LyricsStore(private val directory: String) {

    private fun pathFor(songId: Long) = "$directory/lyrics/$songId.lrc"

    fun load(songId: Long): String? =
        LocalFiles.readText(pathFor(songId))?.takeIf { it.isNotBlank() }

    fun save(songId: Long, content: String) {
        try {
            LocalFiles.mkdirs("$directory/lyrics")
            LocalFiles.writeText(pathFor(songId), content)
        } catch (e: Exception) {
            // Guardar la letra nunca debe romper la app
        }
    }

    fun has(songId: Long): Boolean = LocalFiles.exists(pathFor(songId))
}
