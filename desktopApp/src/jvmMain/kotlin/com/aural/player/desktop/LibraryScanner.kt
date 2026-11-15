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

    /**
     * Escanea y va entregando canciones en tandas de 50 por [onBatch],
     * para que la UI se llene poco a poco en vez de esperar al final.
     * Devuelve la lista completa ordenada por título.
     */
    fun scan(root: File, onBatch: (List<Song>) -> Unit = {}): List<Song> {
        if (!root.isDirectory) return emptyList()
        val files = try {
            root.walkTopDown()
                .onFail { _, _ -> }
                .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
        val songs = mutableListOf<Song>()
        val batch = mutableListOf<Song>()
        for ((index, file) in files.withIndex()) {
            batch += readSong(index.toLong(), file)
            if (batch.size >= 50) {
                songs += batch
                onBatch(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            songs += batch
            onBatch(batch.toList())
        }
        return songs.sortedBy { it.title.lowercase() }
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
        } catch (_: Throwable) {
            // Archivo ilegible: se muestra igual con el nombre del archivo.
            Song(id, file.nameWithoutExtension, "Artista desconocido", "", 0L, file.absolutePath)
        }
    }
}
