// Editor de metadatos via MediaStore (forma nativa de Android).
// Para portadas se usa el sistema de portadas personalizadas de la app.
package com.aural.player.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Metadatos editables de una canción. */
data class SongMetadata(
    val title: String,
    val artist: String,
    val album: String
)

object MetadataEditor {

    private const val TAG = "MetadataEditor"

    /**
     * Actualiza título, artista y álbum via MediaStore.
     * @return true si se actualizó correctamente
     */
    suspend fun write(
        context: Context,
        songUri: String,
        metadata: SongMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(songUri)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, metadata.title)
                put(MediaStore.Audio.Media.ARTIST, metadata.artist)
                put(MediaStore.Audio.Media.ALBUM, metadata.album)
            }

            val rows = context.contentResolver.update(uri, values, null, null)

            if (rows > 0) {
                Log.i(TAG, "Metadatos actualizados via MediaStore")
                true
            } else {
                Log.w(TAG, "MediaStore no actualizó ninguna fila")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permiso de escritura: ${e.message}")
            throw e // El diálogo maneja esto pidiendo permiso
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando metadatos: ${e.message}", e)
            false
        }
    }

    /**
     * Lee los metadatos actuales via MediaStore.
     */
    suspend fun read(context: Context, songUri: String): SongMetadata? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(songUri)
                val projection = arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM
                )

                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        SongMetadata(
                            title = cursor.getString(0).orEmpty(),
                            artist = cursor.getString(1).orEmpty(),
                            album = cursor.getString(2).orEmpty()
                        )
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo metadatos: ${e.message}")
                null
            }
        }
}
