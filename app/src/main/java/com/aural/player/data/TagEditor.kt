// Escritura de tags reales en el archivo de audio (v1.2.0): ID3 (MP3),
// Vorbis (FLAC/OGG) y MP4 (M4A) vía jaudiotagger. En Android 11+ la app
// no tiene acceso directo al archivo, así que el flujo es: copiar el
// contenido a un temporal → escribir los tags → devolverlo por el URI de
// MediaStore. Si el sistema niega la escritura, se devuelve
// NeedsPermission (pedir permiso con MediaStore.createWriteRequest y
// reintentar). El original nunca se toca hasta que la copia editada está
// lista, y si la devolución falla a medias se intenta restaurar.
//
// OJO: jaudiotagger elige el lector por la EXTENSIÓN del archivo. Los
// temporales DEBEN llevar la extensión real (.mp3, .m4a...): con ".tmp"
// el lector no abre nada y toda edición falla en silencio.
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
import java.util.Locale

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
        var verifyFile: File? = null
        try {
            // 0. Extensión real del archivo: sin ella jaudiotagger no abre
            // el temporal (elige el lector por la extensión). Formatos
            // sin soporte (p. ej. .webm) caen al guardado solo en la app.
            val extension = queryExtension(context, uri)
            if (extension == null || extension !in SUPPORTED_EXTENSIONS) {
                Log.w(TAG, "Formato sin soporte para editar: $extension")
                return@withContext TagWriteResult.Failed
            }
            // 1. Copiar el original a un temporal (respaldo por si algo falla).
            val original = File(context.cacheDir, "tag_edit_original.$extension")
            resolver.openInputStream(uri)?.use { input ->
                original.outputStream().use { input.copyTo(it) }
            } ?: return@withContext TagWriteResult.Failed
            backupFile = original
            // 2. Copia de trabajo: escribir los tags con jaudiotagger.
            val work = File(context.cacheDir, "tag_edit_work.$extension")
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
            // 4. Verificar: releer el archivo desde el URI y comprobar que
            // los tags quedaron escritos de verdad. Sin esta comprobación,
            // un fallo silencioso de la copia se reportaría como éxito.
            // La fila de MediaStore solo se toca si la verificación pasa.
            val verify = File(context.cacheDir, "tag_verify.$extension")
            verifyFile = verify
            val verified = try {
                resolver.openInputStream(uri)?.use { input ->
                    verify.outputStream().use { input.copyTo(it) }
                } != null && tagsMatch(verify, title, artist, album)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo verificar la escritura: ${e.message}")
                false
            }
            if (!verified) {
                Log.w(TAG, "La verificación falló: los tags no quedaron en el archivo")
                tryRestore(context, uri, backupFile)
                return@withContext TagWriteResult.Failed
            }
            // 5. Actualizar la fila de MediaStore (mismo permiso ya concedido).
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
            try {
                verifyFile?.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** ¿El archivo [file] lleva estos tags escritos? */
    private fun tagsMatch(file: File, title: String, artist: String, album: String): Boolean {
        return try {
            val tag = AudioFileIO.read(file).tag ?: return false
            tag.getFirst(FieldKey.TITLE) == title &&
                tag.getFirst(FieldKey.ARTIST) == artist &&
                tag.getFirst(FieldKey.ALBUM) == album
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo releer el archivo: ${e.message}")
            false
        }
    }

    /** Extensiones que jaudiotagger sabe leer y escribir. */
    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "mp4", "m4a", "m4b", "m4p",
        "ogg", "oga", "opus", "flac", "wma", "wav",
        "aif", "aiff", "aifc"
    )

    /**
     * Extensión real del archivo: primero del nombre en MediaStore y, si
     * no trae, del tipo MIME. Null si no se pudo averiguar.
     */
    private fun queryExtension(context: Context, uri: Uri): String? {
        return try {
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val displayName = cursor.getString(0).orEmpty()
                val fromName = displayName.substringAfterLast('.', "")
                    .lowercase(Locale.ROOT)
                    .takeIf { it.isNotEmpty() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
                if (fromName != null) return fromName
                mimeToExtension(cursor.getString(1).orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la extensión: ${e.message}")
            null
        }
    }

    private fun mimeToExtension(mime: String): String? = when (mime.lowercase(Locale.ROOT)) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg", "audio/vorbis" -> "ogg"
        "audio/opus" -> "opus"
        "audio/x-ms-wma" -> "wma"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/aiff", "audio/x-aiff" -> "aiff"
        else -> null
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
