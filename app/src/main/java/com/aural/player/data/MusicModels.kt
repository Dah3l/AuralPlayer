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
 * @param dateAddedSec  Segundos desde epoch en que el archivo se agregó a
 *                      MediaStore (DATE_ADDED); ordena "Agregadas recientemente"
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
    val folderPath: String = "",
    val dateAddedSec: Long = 0,
    /** Video de YouTube si la pista es online; null en canciones locales. */
    val onlineVideoId: String? = null,
    /** True si la pista es un episodio de podcast. */
    val isPodcastEpisode: Boolean = false
) {
    /** True si la pista se reproduce en línea (streaming), no desde un archivo. */
    val isOnline: Boolean get() = onlineVideoId != null
}

/**
 * Registro persistente de una canción online. El [id] es negativo y
 * estable (los MediaStore locales son positivos), así favoritos,
 * historial y playlists pueden guardar la pista sin chocar con la
 * biblioteca local. La URL de audio NO se guarda: caduca; se resuelve
 * al reproducir desde [videoId].
 */
data class OnlineSongRecord(
    val id: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 0L,
        durationMs = durationMs,
        uri = onlineUri(videoId),
        albumArtUri = thumbnailUrl,
        onlineVideoId = videoId
    )

    companion object {
        fun onlineUri(videoId: String): String = "aural-online://youtube/$videoId"
    }
}

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

/** Emisora guardada por el usuario (Radio Browser). */
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val country: String = "",
    val genre: String = "",
    val bitrateKbps: Int = 0,
    val logoUrl: String? = null
) {
    /** Song reproducible con la URL directa del stream en vivo. */
    fun toSong(): Song = Song(
        id = songIdFor(id),
        title = name,
        artist = listOf(country, genre).filter { it.isNotBlank() }
            .joinToString(" · ").ifBlank { "Emisora" },
        album = "",
        albumId = 0L,
        durationMs = 0L,
        uri = streamUrl,
        albumArtUri = logoUrl
    )

    companion object {
        /** Id estable en un rango negativo propio, lejos de los
         *  ids online secuenciales y de los MediaStore locales. */
        fun songIdFor(stationId: String): Long =
            -2_000_000_000L - (stationId.hashCode().toLong() and 0x7fffffffL)
    }
}

/** Podcast guardado por el usuario (directorio RSS). */
data class PodcastShow(
    val id: String,
    val title: String,
    val feedUrl: String,
    val imageUrl: String? = null,
    val author: String = ""
)

/** Episodio de un feed RSS. */
data class PodcastEpisode(
    val guid: String,
    val title: String,
    val publishedMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioUrl: String,
    val imageUrl: String? = null
) {
    /** Song reproducible con la URL directa del episodio. */
    fun toSong(show: PodcastShow): Song = Song(
        id = songIdFor(show.feedUrl, guid),
        title = title,
        artist = show.title,
        album = show.author,
        albumId = 0L,
        durationMs = durationMs,
        uri = audioUrl,
        albumArtUri = imageUrl ?: show.imageUrl,
        isPodcastEpisode = true
    )

    companion object {
        fun songIdFor(feedUrl: String, guid: String): Long =
            -3_000_000_000L - ((feedUrl + "|" + guid).hashCode().toLong() and 0x7fffffffL)

        fun progressKey(feedUrl: String, guid: String): String = "$feedUrl|$guid"
    }
}

/** Progreso guardado de un episodio. */
data class PodcastProgress(
    val positionMs: Long = 0L,
    val listened: Boolean = false
)
