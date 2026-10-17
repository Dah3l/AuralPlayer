// Editor de metadatos de archivos de audio usando jaudiotagger.
// Escribe los cambios de forma segura (archivo temporal + reemplazo atómico).
package com.aural.player.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/** Metadatos editables de una canción. */
data class SongMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val year: String = ""
)

object MetadataEditor {

    private const val TAG = "MetadataEditor"

    /**
     * Lee los metadatos actuales de un archivo.
     */
    suspend fun read(context: Context, songUri: String): SongMetadata? =
        withContext(Dispatchers.IO) {
            try {
                val file = uriToFile(context, songUri) ?: return@withContext null
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: return@withContext null

                SongMetadata(
                    title = tag.getFirst(FieldKey.TITLE).orEmpty(),
                    artist = tag.getFirst(FieldKey.ARTIST).orEmpty(),
                    album = tag.getFirst(FieldKey.ALBUM).orEmpty(),
                    genre = tag.getFirst(FieldKey.GENRE).orEmpty(),
                    year = tag.getFirst(FieldKey.YEAR).orEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo metadatos: ${e.message}")
                null
            }
        }

    /**
     * Escribe metadatos y portada en el archivo de forma segura.
     * @param coverUri URI de la imagen de portada (null = no cambiar, "" = quitar)
     * @return true si se escribió correctamente
     */
    suspend fun write(
        context: Context,
        songUri: String,
        metadata: SongMetadata,
        coverUri: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val original = uriToFile(context, songUri) ?: return@withContext false

            // 1. Copiar a temporal
            val temp = File.createTempFile("aural_meta_", ".tmp", context.cacheDir)
            original.copyTo(temp, overwrite = true)

            try {
                // 2. Modificar el temporal
                val audioFile = AudioFileIO.read(temp)
                val tag = audioFile.tagOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, metadata.title)
                tag.setField(FieldKey.ARTIST, metadata.artist)
                tag.setField(FieldKey.ALBUM, metadata.album)
                if (metadata.genre.isNotBlank()) tag.setField(FieldKey.GENRE, metadata.genre)
                if (metadata.year.isNotBlank()) tag.setField(FieldKey.YEAR, metadata.year)

                // Portada
                when {
                    coverUri == "" -> tag.deleteArtworkField()
                    coverUri != null -> {
                        val coverFile = uriToFile(context, coverUri)
                            ?: downloadToTemp(context, coverUri)
                        if (coverFile != null) {
                            val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                            tag.deleteArtworkField()
                            tag.addField(artwork)
                        }
                    }
                }

                audioFile.commit()

                // 3. Verificar que el temporal es válido
                AudioFileIO.read(temp)

                // 4. Reemplazar original (atómico)
                temp.copyTo(original, overwrite = true)
                temp.delete()

                // 5. Notificar a MediaStore
                refreshMediaStore(context, songUri)

                Log.i(TAG, "Metadatos escritos correctamente")
                true
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo metadatos: ${e.message}")
            false
        }
    }

    private fun uriToFile(context: Context, uriString: String): File? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> File(uri.path!!)
                "content" -> {
                    // Copiar contenido a temporal para jaudiotagger
                    val temp = File.createTempFile("aural_read_", ".tmp", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    temp
                }
                else -> File(uriString).takeIf { it.exists() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo URI: ${e.message}")
            null
        }
    }

    private suspend fun downloadToTemp(context: Context, url: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val temp = File.createTempFile("aural_cover_", ".jpg", context.cacheDir)
                val connection = java.net.URL(url).openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.getInputStream().use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                temp
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando portada: ${e.message}")
                null
            }
        }

    private fun refreshMediaStore(context: Context, songUri: String) {
        try {
            val uri = Uri.parse(songUri)
            // Notificar cambio para que MediaStore re-escanee
            context.contentResolver.notifyChange(uri, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescando MediaStore: ${e.message}")
        }
    }
}
