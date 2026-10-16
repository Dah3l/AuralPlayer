// Modelo de datos central de Aural
package com.aural.player.data

/**
 * Canción almacenada localmente en el dispositivo.
 *
 * @param id            Identificador único (MediaStore _ID)
 * @param title         Título de la pista
 * @param artist        Nombre del artista (o "Artista misterioso")
 * @param album         Nombre del álbum (o "Artista misterioso")
 * @param albumId       Identificador del álbum en MediaStore
 * @param durationMs    Duración en milisegundos
 * @param uri           URI del archivo de audio
 * @param albumArtUri   URI de la carátula obtenida del sistema
 * @param trackNumber   Número de pista dentro del álbum
 * @param folderPath    Ruta relativa de la carpeta (MediaStore RELATIVE_PATH)
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String?,
    val trackNumber: Int = 0,
    val folderPath: String = ""
)

/**
 * Agrupación de canciones por carpeta del dispositivo.
 */
data class Folder(
    val path: String,
    val songs: List<Song>
) {
    /** Nombre visible: última parte de la ruta. */
    val displayName: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { "Raíz" }
}

/**
 * Agrupación de canciones por artista.
 */
data class Artist(
    val name: String,
    val songs: List<Song>
) {
    /** Pista representativa para la portada del artista. */
    val representativeSong: Song get() = songs.first()
}

/**
 * Agrupación de canciones por álbum.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songs: List<Song>
) {
    /** Duración total del álbum en milisegundos. */
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}

/**
 * Playlist creada por el usuario, persistida como JSON en DataStore.
 *
 * @param songIds IDs de las canciones que contiene, en orden
 */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)
