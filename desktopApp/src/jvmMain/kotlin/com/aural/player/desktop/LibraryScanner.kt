package com.aural.player.desktop

import com.aural.player.shared.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Escanea una carpeta (y sus subcarpetas) buscando archivos de audio
 * y leyendo sus tags con jaudiotagger, la misma librería que usa la
 * app de Android para editar tags.
 */
object LibraryScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "mp4", "m4b", "flac", "ogg", "oga",
        "opus", "wav", "wma", "aif", "aiff"
    )

    fun defaultMusicDir(): File = File(System.getProperty("user.home"), "Music")

    fun scan(root: File): List<Song> {
        if (!root.isDirectory) return emptyList()
        val files = try {
            root.walkTopDown()
                .onFail { _, _ -> }
                .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
        return files.mapIndexed { index, file -> readSong(index.toLong(), file) }
            .sortedBy { it.title.lowercase() }
    }

    private fun readSong(id: Long, file: File): Song {
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: "Artista desconocido"
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: ""
            val durationMs = (audio.audioHeader?.trackLength?.toLong() ?: 0L) * 1000L
            Song(id, title, artist, album, durationMs, file.absolutePath)
        } catch (_: Exception) {
            // Archivo ilegible: se muestra igual con el nombre del archivo.
            Song(id, file.nameWithoutExtension, "Artista desconocido", "", 0L, file.absolutePath)
        }
    }
}
