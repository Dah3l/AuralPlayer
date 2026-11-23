// Portadas personalizadas de canciones guardadas en el teléfono (v1.2.0).
// Antes la app guardaba solo el enlace (iTunes) o la referencia a la foto
// de la galería: lento o ausente sin internet, y frágil si la foto se
// borraba o se movía. Ahora la imagen se descarga/copia a
// filesDir/song_covers/ y el mapa de portadas guarda la ruta local:
// instantánea y sin internet. Las portadas viejas guardadas como enlace
// siguen funcionando igual, sin migración forzada.
package com.aural.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SongCoverStore {

    private const val TAG = "SongCoverStore"
    private const val DIR_NAME = "song_covers"
    private const val MAX_DIMENSION = 600
    private const val JPEG_QUALITY = 85
    private const val TIMEOUT_MS = 15_000

    fun coverFile(context: Context, songId: Long): File =
        File(File(context.filesDir, DIR_NAME), "$songId.jpg")

    /** Descarga la portada de [url] y la guarda local. Devuelve la URI file:// o null si falla. */
    suspend fun saveFromUrl(context: Context, songId: Long, url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeDownscaledStream {
                    openCoverConnection(url)?.inputStream
                } ?: return@withContext null
                saveBitmap(context, songId, bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar portada: ${e.message}")
                null
            }
        }

    /** Conexión GET a una portada, o null si no responde 200. */
    private fun openCoverConnection(url: String): HttpURLConnection? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "AuralPlayer/1.0")
            }
            if (conn.responseCode != 200) null else conn
        } catch (e: Exception) {
            null
        }
    }

    /** Copia la imagen de [uri] (galería) a la carpeta local. Devuelve la URI file:// o null si falla. */
    suspend fun saveFromContentUri(context: Context, songId: Long, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeDownscaledStream {
                    context.contentResolver.openInputStream(uri)
                } ?: return@withContext null
                saveBitmap(context, songId, bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo copiar portada: ${e.message}")
                null
            }
        }

    /** Borra el archivo local de la portada (al quitarla o borrar la canción). Sin error si no existe. */
    fun deleteCover(context: Context, songId: Long) {
        try {
            coverFile(context, songId).delete()
        } catch (e: Exception) {
            // Best-effort: un archivo huérfano no rompe nada.
        }
    }

    private fun saveBitmap(context: Context, songId: Long, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val out = File(dir, "$songId.jpg")
            FileOutputStream(out).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            Uri.fromFile(out).toString()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar portada: ${e.message}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodifica limitando el lado mayor a MAX_DIMENSION sin cargar el
     *  archivo entero en RAM: dos pasadas sobre el stream (medidas y
     *  decode con inSampleSize). [openStream] debe devolver un stream
     *  nuevo en cada llamada (una foto de galería de 10-20 MB ya no se
     *  carga entera como ByteArray antes de achicarla). */
    private fun decodeDownscaledStream(openStream: () -> java.io.InputStream?): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (maxOf(w, h) / 2 >= MAX_DIMENSION) {
                w /= 2
                h /= 2
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val longest = maxOf(decoded.width, decoded.height)
            if (longest <= MAX_DIMENSION) return decoded
            val scale = MAX_DIMENSION.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== decoded) decoded.recycle()
            scaled
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar portada: ${e.message}")
            null
        }
    }
}
