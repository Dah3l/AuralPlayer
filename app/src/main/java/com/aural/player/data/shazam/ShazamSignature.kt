// Generador de firma acústica de Shazam.
//
// Port del algoritmo real de shazamio-core (Rust):
// https://github.com/shazamio/shazamio-core/blob/master/src/fingerprinting/algorithm.rs
//
// - FFT de 2048 puntos, ventana Hanning de 2048
// - Buffer circular de 2048 muestras, 128 muestras nuevas por iteración
// - Spreading en frecuencia y tiempo
// - Detección de picos con vecinos en frecuencia y tiempo
// - Magnitud logarítmica, interpolación parabólica de frecuencia
package com.aural.player.data.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import java.util.zip.CRC32
import android.util.Base64

/** Pico de frecuencia detectado. */
private data class FrequencyPeak(
    val fftPassNumber: Int,
    val peakMagnitude: Int,  // u16
    val correctedBin: Int    // u16
)

/** Banda de frecuencia (por Hz). */
private enum class FrequencyBand(val id: Int) {
    B250_520(0),
    B520_1450(1),
    B1450_3500(2),
    B3500_5500(3)
}

object ShazamSignature {

    // FFT de 2048 puntos
    private const val FFT_SIZE = 2048
    private const val USEFUL_BINS = 1025  // 0..1024
    private const val RING_SIZE = 256

    // Ventana Hanning de 2048 (fórmula estándar)
    private val HANNING: FloatArray = FloatArray(FFT_SIZE) { n ->
        (0.5 * (1.0 - cos(2.0 * PI * n / (FFT_SIZE - 1)))).toFloat()
    }

    // Vecinos en frecuencia para detección de picos
    private val FREQ_NEIGHBORS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)

    // Vecinos en tiempo (offsets en el ring buffer de spread)
    private val TIME_NEIGHBORS = intArrayOf(
        -53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249
    )

    // Offsets para el spreading temporal
    private val SPREAD_TIME_OFFSETS = intArrayOf(1, 3, 6)

    /**
     * Genera la firma de Shazam para un buffer de audio.
     * @param samples muestras PCM 16-bit mono a 16 kHz
     * @return URI de la firma (data:audio/vnd.shazam.sig;base64,...)
     */
    fun generate(samples: ShortArray): String {
        val gen = Generator()
        return gen.makeSignature(samples)
    }

    private class Generator {
        // Buffer circular de 2048 muestras i16
        private val ringBuffer = ShortArray(FFT_SIZE)
        private var ringIndex = 0

        // Buffer reordenado para la FFT
        private val reordered = FloatArray(FFT_SIZE)

        // Salidas de FFT (ring buffer de 256 x 1025)
        private val fftOutputs = Array(RING_SIZE) { FloatArray(USEFUL_BINS) }
        private var fftIndex = 0

        // Salidas con spreading (ring buffer de 256 x 1025)
        private val spreadOutputs = Array(RING_SIZE) { FloatArray(USEFUL_BINS) }
        private var spreadIndex = 0

        private var numSpreadFftsDone = 0

        // Picos por banda
        private val peaks = mutableMapOf<FrequencyBand, MutableList<FrequencyPeak>>()

        fun makeSignature(samples: ShortArray): String {
            // Procesar en chunks de 128
            var pos = 0
            while (pos + 128 <= samples.size) {
                val chunk = samples.copyOfRange(pos, pos + 128)
                doFft(chunk)
                doPeakSpreading()
                numSpreadFftsDone++
                if (numSpreadFftsDone >= 46) {
                    doPeakRecognition()
                }
                pos += 128
            }

            return encodeToUri(samples.size)
        }

        private fun doFft(chunk: ShortArray) {
            // Copiar 128 muestras al ring buffer
            for (i in 0 until 128) {
                ringBuffer[ringIndex + i] = chunk[i]
            }
            ringIndex = (ringIndex + 128) and 2047

            // Reordenar (los más recientes al final) y aplicar Hanning
            for (i in 0 until FFT_SIZE) {
                val sample = ringBuffer[(i + ringIndex) and 2047]
                reordered[i] = sample.toFloat() * HANNING[i]
            }

            // FFT de 2048 puntos reales -> 1025 complejos
            val complex = fftForward2048(reordered)

            // Magnitudes: (re² + im²) / 131072, mínimo 1e-10
            val out = fftOutputs[fftIndex]
            for (i in 0 until USEFUL_BINS) {
                val re = complex[i * 2]
                val im = complex[i * 2 + 1]
                val mag = (re * re + im * im) / 131072f
                out[i] = max(mag, 1e-10f)
            }

            fftIndex = (fftIndex + 1) and 255
        }

        private fun doPeakSpreading() {
            val real = fftOutputs[(fftIndex - 1) and 255]
            val spread = spreadOutputs[spreadIndex]

            // Spreading en frecuencia: máximo de 3 adyacentes
            for (i in 0 until USEFUL_BINS) {
                spread[i] = real[i]
            }
            for (pos in 0..1022) {
                spread[pos] = max(spread[pos], max(spread[pos + 1], spread[pos + 2]))
            }

            // Spreading en tiempo: propagar a FFTs anteriores
            val spreadCopy = spread.copyOf()
            for (pos in 0 until USEFUL_BINS) {
                for (offset in SPREAD_TIME_OFFSETS) {
                    val former = spreadOutputs[(spreadIndex - offset) and 255]
                    if (spreadCopy[pos] > former[pos]) {
                        former[pos] = spreadCopy[pos]
                    }
                }
            }

            spreadIndex = (spreadIndex + 1) and 255
        }

        private fun doPeakRecognition() {
            val fftMinus46 = fftOutputs[(fftIndex - 46) and 255]
            val spreadMinus49 = spreadOutputs[(spreadIndex - 49) and 255]

            for (bin in 10..1014) {
                // El bin debe ser lo suficientemente grande
                if (fftMinus46[bin] < 1f / 64f) continue
                if (fftMinus46[bin] < spreadMinus49[bin - 1]) continue

                // Verificar que es mínimo local en frecuencia
                var maxFreqNeighbor = 0f
                for (offset in FREQ_NEIGHBORS) {
                    val neighborBin = bin + offset
                    if (neighborBin in 0 until USEFUL_BINS) {
                        maxFreqNeighbor = max(maxFreqNeighbor, spreadMinus49[neighborBin])
                    }
                }
                if (fftMinus46[bin] <= maxFreqNeighbor) continue

                // Verificar que es mínimo local en tiempo
                var maxTimeNeighbor = maxFreqNeighbor
                for (offset in TIME_NEIGHBORS) {
                    val other = spreadOutputs[(spreadIndex + offset) and 255]
                    // Nota: Rust usa bin_position - 1 para los vecinos temporales
                    val neighborBin = bin - 1
                    if (neighborBin in 0 until USEFUL_BINS) {
                        maxTimeNeighbor = max(maxTimeNeighbor, other[neighborBin])
                    }
                }
                if (fftMinus46[bin] <= maxTimeNeighbor) continue

                // ¡PICO ENCONTRADO!
                val fftPassNumber = numSpreadFftsDone - 46

                // Magnitud logarítmica
                val mag = max(ln(fftMinus46[bin].toDouble()), (1.0/64.0)).toFloat() * 1477.3f + 6144f
                val magBefore = max(ln(fftMinus46[bin-1].toDouble()), (1.0/64.0)).toFloat() * 1477.3f + 6144f
                val magAfter = max(ln(fftMinus46[bin+1].toDouble()), (1.0/64.0)).toFloat() * 1477.3f + 6144f

                // Interpolación parabólica
                val var1 = mag * 2f - magBefore - magAfter
                // Evitar división por cero
                if (var1 <= 0f) continue
                val var2 = (magAfter - magBefore) * 32f / var1
                val correctedBin = (bin * 64 + var2.toInt()).toUShort().toInt()

                // Convertir a Hz: corrected_bin * 16000 / 2 / 1024 / 64
                val freqHz = correctedBin * (16000f / 2f / 1024f / 64f)

                // Clasificar en banda
                val band = when (freqHz.toInt()) {
                    in 250..519 -> FrequencyBand.B250_520
                    in 520..1449 -> FrequencyBand.B520_1450
                    in 1450..3499 -> FrequencyBand.B1450_3500
                    in 3500..5500 -> FrequencyBand.B3500_5500
                    else -> continue
                }

                peaks.getOrPut(band) { mutableListOf() }.add(
                    FrequencyPeak(fftPassNumber, mag.toInt(), correctedBin)
                )
            }
        }

        /**
         * FFT radix-2 de 2048 puntos reales.
         * @return array de 2050 floats [re0, im0, re1, im1, ...]
         */
        private fun fftForward2048(input: FloatArray): FloatArray {
            val n = FFT_SIZE
            // Bit-reversal
            val re = FloatArray(n)
            val im = FloatArray(n)
            for (i in 0 until n) {
                re[i] = input[i]
            }

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

            // Cooley-Tukey
            var len = 2
            while (len <= n) {
                val angle = -2.0 * PI / len
                val wRe = cos(angle).toFloat()
                val wIm = kotlin.math.sin(angle).toFloat()
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

            // Empaquetar solo los primeros 1025 (0..1024)
            val out = FloatArray(USEFUL_BINS * 2)
            for (i in 0 until USEFUL_BINS) {
                out[i * 2] = re[i]
                out[i * 2 + 1] = im[i]
            }
            return out
        }

        private fun encodeToUri(numSamples: Int): String {
            val binary = encodeToBinary(numSamples)
            val b64 = Base64.encodeToString(binary, Base64.NO_WRAP)
            return "data:audio/vnd.shazam.sig;base64,$b64"
        }

        private fun encodeToBinary(numSamples: Int): ByteArray {
            val out = mutableListOf<Byte>()

            fun writeU32LE(v: Long) {
                out.add((v and 0xFF).toByte())
                out.add(((v shr 8) and 0xFF).toByte())
                out.add(((v shr 16) and 0xFF).toByte())
                out.add(((v shr 24) and 0xFF).toByte())
            }
            fun writeU16LE(v: Int) {
                out.add((v and 0xFF).toByte())
                out.add(((v shr 8) and 0xFF).toByte())
            }
            fun writeU8(v: Int) {
                out.add((v and 0xFF).toByte())
            }

            // Header de 48 bytes
            writeU32LE(0xcafe2580)  // magic1
            writeU32LE(0)  // crc32 (se calcula después)
            writeU32LE(0)  // size_minus_header (después)
            writeU32LE(0x94119c00)  // magic2
            writeU32LE(0)  // void1
            writeU32LE(0)
            writeU32LE(0)
            writeU32LE((3L shl 27))  // sample rate 16000 -> id 3
            writeU32LE(0)  // void2
            writeU32LE(0)
            // number_samples + sample_rate * 0.24
            writeU32LE(numSamples.toLong() + (16000 * 0.24).toLong())
            writeU32LE(((15 shl 19) + 0x40000).toLong())  // fixed

            writeU32LE(0x40000000)
            writeU32LE(0)  // size_minus_header (después)

            // Bandas ordenadas por id
            val sortedBands = peaks.keys.sortedBy { it.id }
            for (band in sortedBands) {
                val bandPeaks = peaks[band] ?: continue
                // Los picos ya están en orden de fftPassNumber por construcción

                val peakBytes = mutableListOf<Byte>()
                var fftPass = 0
                for (peak in bandPeaks) {
                    if (peak.fftPassNumber - fftPass >= 255) {
                        peakBytes.add(0xFF.toByte())
                        // write u32 LE
                        val v = peak.fftPassNumber.toLong()
                        peakBytes.add((v and 0xFF).toByte())
                        peakBytes.add(((v shr 8) and 0xFF).toByte())
                        peakBytes.add(((v shr 16) and 0xFF).toByte())
                        peakBytes.add(((v shr 24) and 0xFF).toByte())
                        fftPass = peak.fftPassNumber
                    }
                    peakBytes.add(((peak.fftPassNumber - fftPass) and 0xFF).toByte())
                    // magnitude u16 LE
                    peakBytes.add((peak.peakMagnitude and 0xFF).toByte())
                    peakBytes.add(((peak.peakMagnitude shr 8) and 0xFF).toByte())
                    // corrected bin u16 LE
                    peakBytes.add((peak.correctedBin and 0xFF).toByte())
                    peakBytes.add(((peak.correctedBin shr 8) and 0xFF).toByte())
                    fftPass = peak.fftPassNumber
                }

                writeU32LE(0x60030040L + band.id)
                writeU32LE(peakBytes.size.toLong())
                out.addAll(peakBytes)
                // Padding a múltiplo de 4
                val pad = (4 - peakBytes.size % 4) % 4
                repeat(pad) { out.add(0) }
            }

            val buffer = out.toByteArray()
            val sizeMinusHeader = buffer.size - 48

            // Escribir size_minus_header en offset 8 y 52
            fun writeU32At(offset: Int, v: Long) {
                buffer[offset] = (v and 0xFF).toByte()
                buffer[offset + 1] = ((v shr 8) and 0xFF).toByte()
                buffer[offset + 2] = ((v shr 16) and 0xFF).toByte()
                buffer[offset + 3] = ((v shr 24) and 0xFF).toByte()
            }
            writeU32At(8, sizeMinusHeader.toLong())
            writeU32At(52, sizeMinusHeader.toLong())

            // CRC32 de los bytes desde offset 8
            val crc = CRC32()
            crc.update(buffer, 8, buffer.size - 8)
            writeU32At(4, crc.value)

            return buffer
        }
    }
}
