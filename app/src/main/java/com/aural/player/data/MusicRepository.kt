// Escaneo de la biblioteca de audio local mediante MediaStore
package com.aural.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio que lee todas las pistas de audio del dispositivo
 * usando MediaStore (sin copiar archivos, solo metadatos).
 */
class MusicRepository(private val context: Context) {

    /**
     * Escanea MediaStore y devuelve las canciones ordenadas por título.
     * Se ejecuta en un hilo de fondo para no bloquear la interfaz.
     */
    suspend fun scanSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Columnas necesarias: id, título, artista, álbum, duración, pista
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        // Solo pistas musicales reales, con duración mínima razonable
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                // RELATIVE_PATH solo existe en API 29+; en versiones anteriores vale ""
                val relPathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val duration = cursor.getLong(durationCol)
                    // Descartar clips muy cortos (tonos de notificación, etc.)
                    if (duration < 5_000) continue

                    val uri = ContentUris.withAppendedId(collection, id)
                    songs += Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Artista misterioso",
                        artist = cleanMediaMetadata(cursor.getString(artistCol)),
                        album = cleanMediaMetadata(cursor.getString(albumCol)),
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = duration,
                        uri = uri.toString(),
                        albumArtUri = albumArtUriFor(cursor.getLong(albumIdCol)),
                        trackNumber = cursor.getInt(trackCol),
                        folderPath = if (relPathCol >= 0)
                            cursor.getString(relPathCol) ?: "" else ""
                    )
                }
            }

        songs
    }

    /**
     * Construye la URI de la carátula del álbum a partir del ALBUM_ID.
     * Truco estándar: el art es el álbum + 16334 en el contenido de álbunes.
     */
    private fun albumArtUriFor(albumId: Long): String? = try {
        ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            albumId
        ).toString()
    } catch (e: Exception) {
        null
    }

    /**
     * Limpia los marcadores "<unknown>" que inserta MediaStore.
     */
    private fun cleanMediaMetadata(value: String?): String {
        if (value.isNullOrBlank() || value.equals("<unknown artist>", true)
            || value.equals("<unknown>", true)
        ) return "Artista misterioso"
        return value
    }

    /** Agrupa las canciones por artista, ordenado alfabéticamente. */
    fun groupByArtist(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list) }
            .sortedBy { it.name.lowercase() }

    /** Agrupa las canciones por carpeta del dispositivo, ordenado alfabéticamente. */
    fun groupByFolder(songs: List<Song>): List<Folder> =
        songs.groupBy { it.folderPath.ifBlank { "Desconocida" } }
            .map { (path, list) -> Folder(path, list.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.displayName.lowercase() }

    /** Agrupa las canciones por álbum, ordenado alfabéticamente. */
    fun groupByAlbum(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId to it.album }
            .map { (key, list) ->
                Album(
                    // ID único por grupo: el mismo albumId puede venir con
                    // distinto nombre en archivos mal etiquetados
                    id = (key.first to key.second).hashCode().toLong(),
                    title = key.second,
                    artist = list.first().artist,
                    songs = list.sortedBy { it.trackNumber }
                )
            }
            .sortedBy { it.title.lowercase() }
}
