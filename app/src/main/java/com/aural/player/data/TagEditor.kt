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
//
// OJO 2: las descargas de YouTube son MP4 FRAGMENTADO (moof/mvex, sin
// stco): jaudiotagger no puede reescribir su moov. Esos archivos se
// detectan (isFragmentedMp4) y se normalizan primero con un remux sin
// pérdida vía MediaExtractor/MediaMuxer; el remux también es el respaldo
// si la escritura directa falla en un MP4.
//
// La portada también se incrusta en el archivo (APIC en MP3, covr en
// MP4) cuando la canción tiene una portada personalizada guardada.
package com.aural.player.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
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
     * [coverArt] = bytes de la portada + su MIME ("image/jpeg" o
     * "image/png"): si viene, la portada se incrusta en el archivo.
     */
    suspend fun writeTags(
        context: Context,
        songUri: String,
        title: String,
        artist: String,
        album: String,
        coverArt: Pair<ByteArray, String>? = null
    ): TagWriteResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(songUri)
        val resolver = context.contentResolver
        var workFile: File? = null
        var backupFile: File? = null
        var verifyFile: File? = null
        var remuxFile: File? = null
        try {
            // 0. Extensión real del archivo: sin ella jaudiotagger no abre
            // el temporal (elige el lector por la extensión). Formatos
            // sin soporte (p. ej. .webm) caen al guardado solo en la app.
            val extension = queryExtension(context, uri)
            if (extension == null || extension !in SUPPORTED_EXTENSIONS) {
                Log.w(TAG, "Formato sin soporte para editar: $extension")
                return@withContext TagWriteResult.Failed
            }
            val isMp4 = extension in MP4_EXTENSIONS
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
            var edited: File? = null
            val fragmented = isMp4 && isFragmentedMp4(work)
            if (!fragmented) {
                // Escritura directa (MP3, FLAC y MP4 progresivos).
                try {
                    writeTagsWithJaudiotagger(work, title, artist, album, coverArt)
                    edited = work
                } catch (e: Exception) {
                    Log.w(TAG, "Escritura directa falló: ${e.message}")
                }
            }
            if (edited == null && isMp4) {
                // Respaldo MP4: normalizar con un remux sin pérdida y
                // escribir los tags sobre el archivo ya progresivo. Si no
                // hay portada nueva, se intenta conservar la incrustada.
                val remuxed = File(context.cacheDir, "tag_remux.$extension")
                remuxFile = remuxed
                if (remuxed.exists()) remuxed.delete()
                val art = coverArt ?: readExistingArtwork(original)
                if (remuxMp4(original, remuxed)) {
                    try {
                        writeTagsWithJaudiotagger(remuxed, title, artist, album, art)
                        edited = remuxed
                    } catch (e: Exception) {
                        Log.w(TAG, "Escritura tras remux falló: ${e.message}")
                    }
                }
            }
            val editedFile = edited
                ?: return@withContext TagWriteResult.Failed
            // 3. Devolver el archivo editado al URI original.
            try {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    editedFile.inputStream().use { it.copyTo(out) }
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
            try {
                remuxFile?.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** Escribe título/artista/álbum (+ portada si viene) con jaudiotagger. */
    private fun writeTagsWithJaudiotagger(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverArt: Pair<ByteArray, String>?
    ) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ARTIST, artist)
        tag.setField(FieldKey.ALBUM, album)
        if (coverArt != null) {
            val artwork = Artwork()
            artwork.binaryData = coverArt.first
            artwork.mimeType = coverArt.second
            tag.setField(artwork)
        }
        audioFile.commit()
    }

    /** ¿El archivo ya trae una portada incrustada? (para conservarla). */
    private fun readExistingArtwork(file: File): Pair<ByteArray, String>? {
        return try {
            val art = AudioFileIO.read(file).tag?.firstArtwork ?: return null
            val data = art.binaryData
            if (data.isEmpty()) return null
            data to art.mimeType
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ¿Es un MP4 fragmentado (DASH de YouTube)? Se buscan cajas `moof` o
     * `mfra` en el nivel superior, o `mvex` dentro de `moov`. Ante la duda
     * devuelve false (el remux sigue siendo el respaldo si falla).
     */
    private fun isFragmentedMp4(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = raf.length()
                var pos = 0L
                var guard = 0
                val header = ByteArray(8)
                while (pos + 8 <= fileSize && guard++ < 1000) {
                    raf.seek(pos)
                    raf.readFully(header)
                    var size = ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (header[3].toLong() and 0xFF)
                    val type = String(header, 4, 4, Charsets.US_ASCII)
                    var headerLen = 8L
                    if (size == 1L) {
                        val ext = ByteArray(8)
                        raf.readFully(ext)
                        size = ByteBuffer.wrap(ext).long
                        headerLen = 16L
                    }
                    if (type == "moof" || type == "mfra") return true
                    if (type == "moov" && hasMvexChild(raf, pos + headerLen, size - headerLen)) {
                        return true
                    }
                    if (size < headerLen) return false
                    if (size == 0L) return false // caja hasta EOF: no hay más
                    pos += size
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** ¿El `moov` que empieza en [start] trae una caja `mvex` hija? */
    private fun hasMvexChild(raf: RandomAccessFile, start: Long, length: Long): Boolean {
        return try {
            val end = start + length
            var pos = start
            var guard = 0
            val header = ByteArray(8)
            while (pos + 8 <= end && guard++ < 100) {
                raf.seek(pos)
                raf.readFully(header)
                var size = ((header[0].toLong() and 0xFF) shl 24) or
                    ((header[1].toLong() and 0xFF) shl 16) or
                    ((header[2].toLong() and 0xFF) shl 8) or
                    (header[3].toLong() and 0xFF)
                val type = String(header, 4, 4, Charsets.US_ASCII)
                var headerLen = 8L
                if (size == 1L) {
                    val ext = ByteArray(8)
                    raf.readFully(ext)
                    size = ByteBuffer.wrap(ext).long
                    headerLen = 16L
                }
                if (type == "mvex") return true
                if (size < headerLen || size == 0L) return false
                pos += size
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Remux sin pérdida: copia las muestras de audio tal cual (sin
     * re-codificar) a un MP4 progresivo normal. Devuelve false si el
     * archivo no trae pista de audio legible.
     */
    private fun remuxMp4(input: File, output: File): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return false
            extractor.selectTrack(trackIndex)
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocate(512 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                    muxer.writeSampleData(outTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                try {
                    muxer.release()
                } catch (_: Exception) {
                }
            }
            return output.exists() && output.length() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Remux falló: ${e.message}")
            return false
        } finally {
            try {
                extractor.release()
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

    /** Familia MP4: la que puede venir fragmentada (DASH de YouTube). */
    private val MP4_EXTENSIONS = setOf("mp4", "m4a", "m4b", "m4p")

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
