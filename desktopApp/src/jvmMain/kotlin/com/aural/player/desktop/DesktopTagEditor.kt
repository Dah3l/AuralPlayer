package com.aural.player.desktop

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.StandardArtwork
import java.io.File

/**
 * Edición de tags REALES en el archivo (MP3, M4A, FLAC…), con
 * jaudiotagger, la misma librería y el mismo patrón que la app de
 * Android (TagEditor.kt): tagOrCreateAndSetDefault + setField +
 * commit. Si además hay una portada personalizada, se incrusta en
 * el archivo. Devuelve true solo si, releyendo el archivo, el
 * título quedó guardado: sin verificación no se promete nada.
 */
object DesktopTagEditor {

    fun writeMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, album)
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val artwork = StandardArtwork()
                artwork.binaryData = coverBytes
                artwork.mimeType = sniffMimeType(coverBytes)
                tag.setField(artwork)
            }
            audioFile.commit()
            // Verificación: releer y comprobar que el título quedó.
            val verify = AudioFileIO.read(file).tag?.getFirst(FieldKey.TITLE)
            verify == title
        } catch (_: Throwable) {
            false
        }
    }

    /** JPEG empieza en FF D8, PNG en 89 50; ante la duda, JPEG. */
    private fun sniffMimeType(bytes: ByteArray): String {
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ->
                "image/jpeg"
            bytes.size >= 2 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() ->
                "image/png"
            else -> "image/jpeg"
        }
    }
}
