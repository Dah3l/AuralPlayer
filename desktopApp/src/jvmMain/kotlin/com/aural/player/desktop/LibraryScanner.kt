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
     * Si [artworkDir] no es null, el arte incrustado que se encuentre
     * se guarda ahí como `<id>.jpg` (caché de portadas). Devuelve la
     * lista completa ordenada por título.
     */
    fun scan(
        root: File,
        artworkDir: String? = null,
        onBatch: (List<Song>) -> Unit = {}
    ): List<Song> {
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
        for (file in files) {
            // El id sale de la ruta: estable entre arranques y tras
            // ordenar por título (el orden del escaneo no lo es).
            batch += readSong(file.absolutePath.hashCode().toLong(), file, artworkDir)
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

    /** Relee UN archivo (tras editar sus tags) con los tags ya nuevos. */
    fun readSingle(file: File): Song =
        readSong(file.absolutePath.hashCode().toLong(), file, artworkDir = null)

    private fun readSong(id: Long, file: File, artworkDir: String?): Song {
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: "Artista desconocido"
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: ""
            val durationMs = (audio.audioHeader?.trackLength?.toLong() ?: 0L) * 1000L
            // Arte incrustado → caché de portadas (si aún no está).
            // La extensión no importa: el decodificador huele el
            // contenido (un PNG guardado como .jpg abre igual).
            try {
                val data = tag?.firstArtwork?.binaryData
                if (artworkDir != null && data != null && data.isNotEmpty()) {
                    val out = File(artworkDir, "$id.jpg")
                    if (!out.exists()) {
                        out.parentFile?.mkdirs()
                        out.writeBytes(data)
                    }
                }
            } catch (_: Throwable) {
                // Sin caché de arte: la canción se muestra igual
            }
            Song(id, title, artist, album, durationMs, file.absolutePath, file.lastModified())
        } catch (_: Throwable) {
            // Archivo ilegible: se muestra igual con el nombre del archivo.
            Song(
                id,
                file.nameWithoutExtension,
                "Artista desconocido",
                "",
                0L,
                file.absolutePath,
                file.lastModified()
            )
        }
    }
}
