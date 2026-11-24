// Análisis de tempo (BPM) para la Mezcla DJ + datos de archivo para
// "Información de la canción". Todo offline, en el teléfono: se
// decodifica con MediaCodec (hardware) y se analiza con Realtime BPM
// Analyzer (sin red, sin dependencias). El análisis es una sola vez por
// canción y en segundo plano; la reproducción nunca lo espera.
package com.aural.player.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.dlepaux.realtimebpmanalyzer.BpmAnalyzer
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteOrder
import kotlin.math.abs

/** Datos de archivo que enseña "Información de la canción". */
data class AudioFileInfo(
    /** "MP3", "M4A", "FLAC", "OGG", "WAV", "OPUS"... */
    val format: String,
    val mime: String,
    /** kbps (0 = desconocido). */
    val bitrateKbps: Int,
    /** Hz (0 = desconocido). */
    val sampleRateHz: Int,
    /** Canales (0 = desconocido). */
    val channels: Int,
    /** Bytes (-1 = desconocido). */
    val sizeBytes: Long,
    /** Duración en ms (0 = desconocida). */
    val durationMs: Long
)

object BpmAnalysis {

    /** Segundos iniciales que se saltan (intro, a menudo sin ritmo). */
    private const val SKIP_SECONDS = 15
    /** Segundos que se analizan tras el salto. */
    private const val WINDOW_SECONDS = 60
    /** Mínimo de audio útil para que el análisis tenga sentido. */
    private const val MIN_ANALYZE_SECONDS = 20

    /**
     * BPM de una canción por su URI de MediaStore (null = no se pudo
     * detectar: muy corta, sin ritmo claro o formato no decodificable).
     * Llamar desde Dispatchers.IO: decodifica de forma bloqueante.
     * Nunca tira excepciones: cualquier fallo es null.
     */
    suspend fun analyzeBpm(context: Context, uri: Uri): Float? {
        val decoded = withTimeoutOrNull(60_000) {
            decodeMonoPcm(context, uri, SKIP_SECONDS + WINDOW_SECONDS)
        } ?: return null
        val (pcm, sampleRate) = decoded
        val skip = SKIP_SECONDS * sampleRate
        val minSamples = skip + MIN_ANALYZE_SECONDS * sampleRate
        if (pcm.size <= minSamples) return null
        val end = minOf(pcm.size, skip + WINDOW_SECONDS * sampleRate)
        val window = pcm.copyOfRange(skip, end)
        return try {
            val reading = BpmAnalyzer.analyzeChannelData(window, sampleRate.toDouble())
            val bpm = reading?.bpm()?.toFloat() ?: return null
            if (bpm.isFinite() && bpm in 30f..300f) bpm else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodifica hasta [maxSeconds] de audio a PCM mono flotante
     * (-1..1) + su sample rate. Null si no hay pista de audio o no se
     * pudo decodificar. El búfer crece según lo decodificado (sin
     * listas de Float que exploten la RAM en una tostadora).
     */
    private fun decodeMonoPcm(context: Context, uri: Uri, maxSeconds: Int): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME)
                if (m?.startsWith("audio/") == true) {
                    trackIndex = i
                    mime = m
                    break
                }
            }
            if (trackIndex < 0 || mime == null) return null
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 2
            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                return null
            }
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                val maxSamples = maxSeconds * sampleRate
                var samples = FloatArray(1 shl 20)
                var count = 0
                var isFloatOutput = false
                val bufferInfo = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                // Tope de iteraciones: un codec atascado no debe girar
                // para siempre en segundo plano.
                var guard = 0
                while (!outputEos && count < maxSamples && ++guard <= 50_000) {
                    if (!inputEos) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex) ?: continue
                            val read = extractor.readSampleData(inBuf, 0)
                            if (read < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                outBuf.order(ByteOrder.LITTLE_ENDIAN)
                                if (isFloatOutput) {
                                    val floats = bufferInfo.size / 4
                                    if (count + floats / channelCount > samples.size) {
                                        var ns = samples.size
                                        while (count + floats / channelCount > ns) ns *= 2
                                        samples = samples.copyOf(ns)
                                    }
                                    var s = 0
                                    while (s < floats) {
                                        var sum = 0f
                                        repeat(channelCount) {
                                            if (s < floats) {
                                                sum += outBuf.float
                                                s++
                                            }
                                        }
                                        samples[count++] = sum / channelCount
                                    }
                                } else {
                                    val shorts = bufferInfo.size / 2
                                    if (count + shorts / channelCount > samples.size) {
                                        var ns = samples.size
                                        while (count + shorts / channelCount > ns) ns *= 2
                                        samples = samples.copyOf(ns)
                                    }
                                    var s = 0
                                    while (s < shorts) {
                                        var sum = 0f
                                        repeat(channelCount) {
                                            if (s < shorts) {
                                                sum += outBuf.short / 32768f
                                                s++
                                            }
                                        }
                                        samples[count++] = sum / channelCount
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputEos = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = codec.outputFormat
                            val encoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else AudioFormat.ENCODING_PCM_16BIT
                            isFloatOutput = encoding == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // Sin más entrada y sin salida: no girar en vacío.
                            if (inputEos) outputEos = true
                        }
                    }
                }
                codec.stop()
                if (count == 0) return null
                return samples.copyOf(count) to sampleRate
            } finally {
                runCatching { codec.release() }
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Pliega un BPM a la rejilla de mezcla [70, 180]: 65 -> 130,
     * 200 -> 100. La rejilla es lo que importa al mezclar (los golpes
     * coinciden cada dos tiempos); el BPM guardado sigue siendo el real.
     */
    fun foldBpm(bpm: Float): Float {
        var b = bpm
        while (b < 70f) b *= 2f
        while (b > 180f) b /= 2f
        return b
    }

    /**
     * Ordena ids por tempo en cadena voraz desde un BPM ancla: en cada
     * paso toma la canción con el BPM (o su doble/mitad) más cercano al
     * actual. Las que no tienen BPM van al final, en su orden. Pura: no
     * toca ningún estado; la usan el servicio (reorden de la cola que
     * viene) y el ViewModel (cola inicial de la Sesión DJ).
     */
    fun orderByTempo(
        ids: List<Long>,
        bpmById: Map<Long, Float>,
        anchorBpm: Float
    ): List<Long> {
        val withBpm = ids.filter { bpmById.containsKey(it) }
        val withoutBpm = ids.filterNot { bpmById.containsKey(it) }
        val remaining = withBpm.toMutableList()
        val ordered = ArrayList<Long>(ids.size)
        var curBpm = anchorBpm
        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestDist = Float.MAX_VALUE
            for (i in remaining.indices) {
                val b = foldBpm(bpmById[remaining[i]] ?: 120f)
                val dist = minOf(
                    abs(b - curBpm),
                    abs(b * 2f - curBpm),
                    abs(b / 2f - curBpm)
                )
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            curBpm = foldBpm(bpmById[remaining[bestIdx]] ?: 120f)
            ordered += remaining.removeAt(bestIdx)
        }
        ordered += withoutBpm
        return ordered
    }

    /** Resultado del cálculo de tempo para una transición. */
    data class TempoMatch(
        /** Velocidad absoluta a la que debe sonar la que entra. */
        val rate: Float,
        /** Si además hay que hacer el cambio de bajos. */
        val bassSwap: Boolean
    )

    /**
     * Velocidad a la que debe sonar la canción que entra para igualar
     * el tempo efectivo de la que sale ([bpmOutRaw] plegado y por la
     * velocidad actual de la que sale, [speedOut]), volviendo después
     * a [baseSpeed] (la velocidad que el usuario tiene puesta).
     * Null = no se puede igualar (falta un BPM o la diferencia es
     * mayor del 8% y no hay relación doble/mitad): fundido normal.
     */
    fun matchTempo(
        bpmOutRaw: Float?,
        bpmInRaw: Float?,
        speedOut: Float,
        baseSpeed: Float
    ): TempoMatch? {
        if (bpmOutRaw == null || bpmInRaw == null) return null
        if (!bpmOutRaw.isFinite() || !bpmInRaw.isFinite()) return null
        if (bpmOutRaw <= 0f || bpmInRaw <= 0f) return null
        if (!speedOut.isFinite() || speedOut <= 0f) return null
        if (!baseSpeed.isFinite() || baseSpeed <= 0f) return null
        val effOut = foldBpm(bpmOutRaw) * speedOut
        val foldedIn = foldBpm(bpmInRaw)
        val rate = effOut / foldedIn
        if (!rate.isFinite() || rate <= 0f) return null
        val lo = baseSpeed * 0.92f
        val hi = baseSpeed * 1.08f
        // Directo: dentro del 8% de la velocidad del usuario.
        if (rate in lo..hi) return TempoMatch(rate, bassSwap = true)
        // Doble/mitad de tempo: los golpes coinciden cada dos tiempos,
        // la que entra suena a la velocidad del usuario.
        if (rate / 2f in lo..hi || rate * 2f in lo..hi) {
            return TempoMatch(baseSpeed, bassSwap = true)
        }
        return null
    }

    /**
     * Datos de archivo de una canción por su URI (null si no se pudo
     * leer). Llamar desde Dispatchers.IO.
     */
    fun readAudioFileInfo(context: Context, uri: Uri): AudioFileInfo? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var mime: String? = null
            var bitrate = 0
            var sampleRate = 0
            var channels = 0
            var durationMs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME)
                if (m?.startsWith("audio/") == true) {
                    mime = m
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                    }
                    break
                }
            }
            if (mime == null) return null
            val size = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (_: Exception) {
                -1L
            }
            return AudioFileInfo(
                format = formatLabel(mime),
                mime = mime,
                bitrateKbps = bitrate,
                sampleRateHz = sampleRate,
                channels = channels,
                sizeBytes = size,
                durationMs = durationMs
            )
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun formatLabel(mime: String): String = when (mime) {
        "audio/mpeg" -> "MP3"
        "audio/mp4" -> "M4A"
        "audio/flac" -> "FLAC"
        "audio/ogg" -> "OGG"
        "audio/opus" -> "OPUS"
        "audio/x-wav", "audio/wav" -> "WAV"
        "audio/3gpp" -> "3GP"
        "audio/aac" -> "AAC"
        else -> mime.substringAfter("/").uppercase()
    }
}
