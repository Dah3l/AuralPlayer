// Escritura de tags reales en el archivo de audio (v1.2.0): ID3 (MP3),
// Vorbis (FLAC/OGG) y MP4 (M4A) vía jaudiotagger. En Android 11+ la app
// no tiene acceso directo al archivo, así que el flujo es: copiar el
// contenido a un temporal → escribir los tags → devolverlo por el URI de
// MediaStore. Si el sistema niega la escritura, se devuelve
// NeedsPermission (pedir permiso con MediaStore.createWriteRequest y
// reintentar). El original nunca se toca hasta que la copia editada está
// lista, y si la devolución falla a medias se intenta restaurar.
package com.aural.player.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

sealed class TagWriteResult {
    object Success : TagWriteResult()

    /** Falta el permiso de escritura de MediaStore: pedirlo y reintentar. */
    object NeedsPermission : TagWriteResult()

    /** No se pudo escribir (formato no soportado, error de E/S...). */
    object Failed : TagWriteResult()
}

object TagEditor {

    private const val TAG = "TagEditor"

    /**
     * Escribe título/artista/álbum en el archivo de [songUri] y actualiza
     * la fila de MediaStore para que la biblioteca lo vea al instante.
     */
    suspend fun writeTags(
        context: Context,
        songUri: String,
        title: String,
        artist: String,
        album: String
    ): TagWriteResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(songUri)
        val resolver = context.contentResolver
        var workFile: File? = null
        var backupFile: File? = null
        try {
            // 1. Copiar el original a un temporal (respaldo por si algo falla).
            val original = File(context.cacheDir, "tag_edit_original.tmp")
            resolver.openInputStream(uri)?.use { input ->
                original.outputStream().use { input.copyTo(it) }
            } ?: return@withContext TagWriteResult.Failed
            backupFile = original
            // 2. Copia de trabajo: escribir los tags con jaudiotagger.
            val work = File(context.cacheDir, "tag_edit_work.tmp")
            original.copyTo(work, overwrite = true)
            workFile = work
            try {
                val audioFile = AudioFileIO.read(work)
                val tag = audioFile.tagOrCreateAndSetDefault
                tag.setField(FieldKey.TITLE, title)
                tag.setField(FieldKey.ARTIST, artist)
                tag.setField(FieldKey.ALBUM, album)
                audioFile.commit()
            } catch (e: Exception) {
                Log.w(TAG, "Formato no editable: ${e.message}")
                return@withContext TagWriteResult.Failed
            }
            // 3. Devolver el archivo editado al URI original.
            try {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    work.inputStream().use { it.copyTo(out) }
                } ?: return@withContext TagWriteResult.Failed
            } catch (e: SecurityException) {
                return@withContext TagWriteResult.NeedsPermission
            }
            // 4. Actualizar la fila de MediaStore (mismo permiso ya concedido).
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                }
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar MediaStore: ${e.message}")
                // No fatal: el re-escaneo de la biblioteca lo puede corregir.
            }
            TagWriteResult.Success
        } catch (e: SecurityException) {
            TagWriteResult.NeedsPermission
        } catch (e: Exception) {
            Log.w(TAG, "Error escribiendo tags: ${e.message}")
            tryRestore(context, uri, backupFile)
            TagWriteResult.Failed
        } finally {
            try {
                workFile?.delete()
            } catch (_: Exception) {
            }
            try {
                backupFile?.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** Best-effort: devuelve el original si la copia editada falló a medias. */
    private fun tryRestore(context: Context, uri: Uri, backup: File?) {
        if (backup == null || !backup.exists()) return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                backup.inputStream().use { it.copyTo(out) }
            }
        } catch (_: Exception) {
            // Ya no hay más que intentar; se reporta el fallo.
        }
    }
}
