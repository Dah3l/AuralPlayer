// Generador de firma acústica compatible con Shazam.
//
// Puerto a Kotlin del algoritmo de shazamio-core (Rust), a su vez basado en
// SongRec. Convierte audio PCM mono a 16 kHz en la firma binaria que acepta
// el endpoint de Shazam, sin necesidad de cuenta ni API key.
//
// Referencias:
// - https://github.com/shazamio/shazamio-core (src/fingerprinting/algorithm.rs)
// - https://github.com/shazamio/shazamio-core (src/fingerprinting/signature_format.rs)
package com.aural.player.data.shazam

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Banda de frecuencia de un pico detectado. */
enum class FrequencyBand(val id: Int) {
    BAND_250_520(0),
    BAND_520_1450(1),
    BAND_1450_3500(2),
    BAND_3500_5500(3);

    companion object {
        fun forFrequencyHz(hz: Float): FrequencyBand? = when (hz.toInt()) {
            in 250..519 -> BAND_250_520
            in 520..1449 -> BAND_520_1450
            in 1450..3499 -> BAND_1450_3500
            in 3500..5500 -> BAND_3500_5500
            else -> null
        }
    }
}

/** Pico espectral detectado. */
data class FrequencyPeak(
    val fftPassNumber: Int,
    val peakMagnitude: Int, // u16
    val correctedPeakFrequencyBin: Int // u16
)

/**
 * Genera la firma Shazam a partir de muestras PCM mono de 16 bits a 16 kHz.
 *
 * Uso:
 * ```
 * val gen = ShazamSignatureGenerator()
 * gen.feedSamples(pcmSamples) // ShortArray a 16 kHz
 * val uri = gen.encodeToUri() // "data:audio/vnd.shazam.sig;base64,..."
 * val sampleMs = gen.sampleMs()
 * ```
 */
class ShazamSignatureGenerator {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        private const val FFT_SIZE = 2048
        private const val FFT_BINS = 1025
        private const val CHUNK_SAMPLES = 128
        private const val RING_MASK = 2047
        private const val FFT_RING_MASK = 255

        /** Ventana Hanning de 2048 (Hanning M=2050 sin los ceros extremos). */
        private val HANNING: FloatArray = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * (i + 1) / 2049.0))).toFloat()
        }
    }

    private val ringBuffer = ShortArray(FFT_SIZE)
    private var ringBufferIndex = 0
    private val reorderedBuffer = FloatArray(FFT_SIZE)
    private val fftOutputs = Array(256) { FloatArray(FFT_BINS) }
    private var fftOutputsIndex = 0
    private val spreadFftOutputs = Array(256) { FloatArray(FFT_BINS) }
    private var spreadFftOutputsIndex = 0
    private var numSpreadFftsDone = 0
    private val peaks = mutableMapOf<FrequencyBand, MutableList<FrequencyPeak>>()
    private var numberSamples = 0

    // Buffers reutilizables para la FFT
    private val fftRe = FloatArray(FFT_SIZE)
    private val fftIm = FloatArray(FFT_SIZE)

    /**
     * Alimenta muestras PCM. Se procesan en bloques de 128.
     * Para reconocimiento se recomiendan ~12 s (192000 muestras a 16 kHz).
     */
    fun feedSamples(samples: ShortArray) {
        numberSamples = samples.size
        var offset = 0
        while (offset + CHUNK_SAMPLES <= samples.size) {
            doFft(samples, offset)
            doPeakSpreading()
            numSpreadFftsDone++
            if (numSpreadFftsDone >= 46) {
                doPeakRecognition()
            }
            offset += CHUNK_SAMPLES
        }
    }

    /** Duración del audio procesado en milisegundos. */
    fun sampleMs(): Int = (numberSamples * 1000L / SAMPLE_RATE_HZ).toInt()

    // ------------------------- FFT -------------------------

    private fun doFft(samples: ShortArray, offset: Int) {
        // Copiar 128 muestras al ring buffer
        for (i in 0 until CHUNK_SAMPLES) {
            ringBuffer[(ringBufferIndex + i) and RING_MASK] = samples[offset + i]
        }
        ringBufferIndex = (ringBufferIndex + CHUNK_SAMPLES) and RING_MASK

        // Reordenar (lo más reciente al final) y aplicar ventana Hanning
        for (i in 0 until FFT_SIZE) {
            reorderedBuffer[i] =
                ringBuffer[(i + ringBufferIndex) and RING_MASK].toFloat() * HANNING[i]
        }

        // FFT radix-2 iterativa
        for (i in 0 until FFT_SIZE) {
            fftRe[i] = reorderedBuffer[i]
            fftIm[i] = 0f
        }
        fftIterative(fftRe, fftIm)

        // Magnitud: (re² + im²) / 2^17, mínimo 1e-10
        val out = fftOutputs[fftOutputsIndex]
        for (i in 0..1024) {
            val mag = (fftRe[i] * fftRe[i] + fftIm[i] * fftIm[i]) / 131072f
            out[i] = mag.coerceAtLeast(1e-10f)
        }

        fftOutputsIndex = (fftOutputsIndex + 1) and FFT_RING_MASK
    }

    /** FFT Cooley-Tukey radix-2 iterativa, in-place. */
    private fun fftIterative(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Bit-reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Mariposas
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val nwr = wr * wRe - wi * wIm
                    wi = wr * wIm + wi * wRe
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }

    // ------------------------- Peak spreading -------------------------

    private fun doPeakSpreading() {
        val realFft = fftOutputs[(fftOutputsIndex - 1) and FFT_RING_MASK]
        val spread = spreadFftOutputs[spreadFftOutputsIndex]

        // Copiar
        realFft.copyInto(spread)

        // Expansión en frecuencia: max(pos, pos+1, pos+2)
        for (pos in 0..1022) {
            val m = spread[pos]
            val m1 = spread[pos + 1]
            val m2 = spread[pos + 2]
            spread[pos] = maxOf(m, m1, m2)
        }

        val spreadCopy = spread.copyOf()

        // Expansión en tiempo: propagar a FFTs anteriores (1, 3, 6 atrás)
        for (pos in 0..1024) {
            val v = spreadCopy[pos]
            for (back in intArrayOf(1, 3, 6)) {
                val prev = spreadFftOutputs[(spreadFftOutputsIndex - back) and FFT_RING_MASK]
                if (v > prev[pos]) prev[pos] = v
            }
        }

        spreadFftOutputsIndex = (spreadFftOutputsIndex + 1) and FFT_RING_MASK
    }

    // ------------------------- Peak recognition -------------------------

    private fun doPeakRecognition() {
        val fftMinus46 = fftOutputs[(fftOutputsIndex - 46) and FFT_RING_MASK]
        val spreadMinus49 = spreadFftOutputs[(spreadFftOutputsIndex - 49) and FFT_RING_MASK]

        for (bin in 10..1014) {
            // El bin debe ser suficientemente grande para ser pico
            if (fftMinus46[bin] < 1f / 64f) continue
            if (fftMinus46[bin] < spreadMinus49[bin - 1]) continue

            // Debe ser máximo local en frecuencia
            var maxNeighbor = 0f
            for (off in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                val v = spreadMinus49[bin + off]
                if (v > maxNeighbor) maxNeighbor = v
            }
            if (fftMinus46[bin] <= maxNeighbor) continue

            // Debe ser máximo local en tiempo
            var maxTimeNeighbor = maxNeighbor
            for (off in intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)) {
                val other = spreadFftOutputs[(spreadFftOutputsIndex + off) and FFT_RING_MASK]
                val v = other[bin - 1]
                if (v > maxTimeNeighbor) maxTimeNeighbor = v
            }
            if (fftMinus46[bin] <= maxTimeNeighbor) continue

            // Es un pico: calcular magnitud y bin corregido
            val fftPassNumber = numSpreadFftsDone - 46

            // Orden exacto del original: ln(x).max(1/64), NO ln(max(x, 1/64))
            val mag = maxOf(ln(fftMinus46[bin]), 1f / 64f) * 1477.3f + 6144f
            val magBefore = maxOf(ln(fftMinus46[bin - 1]), 1f / 64f) * 1477.3f + 6144f
            val magAfter = maxOf(ln(fftMinus46[bin + 1]), 1f / 64f) * 1477.3f + 6144f

            val var1 = mag * 2f - magBefore - magAfter
            // Evitar división por cero (el assert del original)
            if (var1 < 0f) continue
            val var2 = (magAfter - magBefore) * 32f / var1

            val correctedBin = (bin * 64 + var2.toInt()).coerceIn(0, 65535)

            // Convertir bin a frecuencia (16 kHz, 1024 bins útiles, x64)
            val freqHz = correctedBin.toFloat() * (16000f / 2f / 1024f / 64f)

            val band = FrequencyBand.forFrequencyHz(freqHz) ?: continue

            peaks.getOrPut(band) { mutableListOf() }.add(
                FrequencyPeak(
                    fftPassNumber = fftPassNumber,
                    peakMagnitude = mag.toInt().coerceIn(0, 65535),
                    correctedPeakFrequencyBin = correctedBin
                )
            )
        }
    }

    // ------------------------- Codificación binaria -------------------------

    /**
     * Codifica la firma al formato binario de Shazam y la devuelve como
     * data URI en base64, lista para enviar al endpoint.
     */
    fun encodeToUri(): String {
        val binary = encodeToBinary()
        return "data:audio/vnd.shazam.sig;base64," +
            Base64.encodeToString(binary, Base64.NO_WRAP)
    }

    private fun encodeToBinary(): ByteArray {
        // Reservar espacio; el tamaño exacto se calcula al final
        val buf = ByteBuffer.allocate(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(0xcafe2580.toInt()) // magic1
        buf.putInt(0) // crc32 (se escribe después)
        buf.putInt(0) // size_minus_header (se escribe después)
        buf.putInt(0x94119c00.toInt()) // magic2
        buf.putInt(0) // void1
        buf.putInt(0)
        buf.putInt(0)

        val sampleRateId = when (SAMPLE_RATE_HZ) {
            8000 -> 1
            11025 -> 2
            16000 -> 3
            32000 -> 4
            44100 -> 5
            48000 -> 6
            else -> throw IllegalArgumentException("Sample rate no soportado")
        }
        buf.putInt(sampleRateId shl 27) // shifted_sample_rate_id

        buf.putInt(0) // void2
        buf.putInt(0)
        buf.putInt(numberSamples + (SAMPLE_RATE_HZ * 0.24f).toInt()) // number_samples_plus
        buf.putInt((15 shl 19) + 0x40000) // fixed_value
        buf.putInt(0x40000000)
        buf.putInt(0) // size_minus_header (se escribe después)

        // Bandas ordenadas por id
        val sortedBands = peaks.entries.sortedBy { it.key.id }
        for ((band, bandPeaks) in sortedBands) {
            val peaksBuf = ByteBuffer.allocate(bandPeaks.size * 8 + 8)
                .order(ByteOrder.LITTLE_ENDIAN)
            var lastPass = 0
            for (peak in bandPeaks) {
                // Los picos deben ir en orden de fft_pass_number
                if (peak.fftPassNumber - lastPass >= 255) {
                    peaksBuf.put(0xff.toByte())
                    peaksBuf.putInt(peak.fftPassNumber)
                    lastPass = peak.fftPassNumber
                }
                peaksBuf.put((peak.fftPassNumber - lastPass).toByte())
                peaksBuf.putShort(peak.peakMagnitude.toShort())
                peaksBuf.putShort(peak.correctedPeakFrequencyBin.toShort())
                lastPass = peak.fftPassNumber
            }
            val peaksBytes = peaksBuf.array().copyOf(peaksBuf.position())

            buf.putInt(0x60030040 + band.id)
            buf.putInt(peaksBytes.size)
            buf.put(peaksBytes)
            // Padding a múltiplo de 4
            val pad = (4 - peaksBytes.size % 4) % 4
            repeat(pad) { buf.put(0) }
        }

        val totalSize = buf.position()
        val bodySize = totalSize - 48

        // Escribir size_minus_header en offset 8 y 52
        buf.putInt(8, bodySize)
        buf.putInt(52, bodySize)

        // CRC32 de todo después del offset 8
        val bytes = buf.array().copyOf(totalSize)
        val crc = CRC32()
        crc.update(bytes, 8, bytes.size - 8)
        buf.putInt(4, crc.value.toInt())

        return buf.array().copyOf(totalSize)
    }
}
