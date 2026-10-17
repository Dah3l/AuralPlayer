// Generador de firma acústica de Shazam.
// Port del algoritmo de shazamio-core (Rust).
// https://github.com/shazamio/shazamio-core/blob/master/src/fingerprinting/algorithm.rs
package com.aural.player.data.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import java.util.zip.CRC32
import android.util.Base64

object ShazamSignature {

    fun generate(samples: ShortArray): String {
        val gen = SigGen()
        return gen.run(samples)
    }

    private class SigGen {
        private val ringBuf = ShortArray(2048)
        private var ringPos = 0
        private val reorderBuf = FloatArray(2048)
        private val fftOut = Array(256) { FloatArray(1025) }
        private var fftPos = 0
        private val spreadOut = Array(256) { FloatArray(1025) }
        private var spreadPos = 0
        private var doneCount = 0

        private val hanning = FloatArray(2048) { n ->
            (0.5 * (1.0 - cos(2.0 * PI * n / 2047.0))).toFloat()
        }

        // picos por banda: 0=250-520, 1=520-1450, 2=1450-3500, 3=3500-5500
        private val bandPeaks = Array(4) { mutableListOf<Peak>() }

        private class Peak(val pass: Int, val mag: Int, val bin: Int)

        private fun wrap256(i: Int): Int {
            var v = i % 256
            if (v < 0) v += 256
            return v
        }

        private fun wrap2048(i: Int): Int {
            var v = i % 2048
            if (v < 0) v += 2048
            return v
        }

        fun run(samples: ShortArray): String {
            var pos = 0
            while (pos + 128 <= samples.size) {
                doFft(samples, pos)
                doSpread()
                doneCount++
                if (doneCount >= 46) doRecognize()
                pos += 128
            }
            return encode(samples.size)
        }

        private fun doFft(samples: ShortArray, offset: Int) {
            for (i in 0 until 128) {
                ringBuf[wrap2048(ringPos + i)] = samples[offset + i]
            }
            ringPos = wrap2048(ringPos + 128)

            for (i in 0 until 2048) {
                reorderBuf[i] = ringBuf[wrap2048(i + ringPos)].toFloat() * hanning[i]
            }

            val spec = fft2048(reorderBuf)
            val out = fftOut[fftPos]
            for (i in 0 until 1025) {
                val re = spec[i * 2]
                val im = spec[i * 2 + 1]
                var m = (re * re + im * im) / 131072f
                if (m < 1e-10f) m = 1e-10f
                out[i] = m
            }
            fftPos = wrap256(fftPos + 1)
        }

        private fun doSpread() {
            val src = fftOut[wrap256(fftPos - 1)]
            val dst = spreadOut[spreadPos]
            for (i in 0 until 1025) dst[i] = src[i]
            for (p in 0..1022) {
                val m = maxOf(dst[p], dst[p + 1], dst[p + 2])
                dst[p] = m
            }
            val copy = dst.copyOf()
            for (p in 0 until 1025) {
                val v = copy[p]
                for (off in intArrayOf(1, 3, 6)) {
                    val f = spreadOut[wrap256(spreadPos - off)]
                    if (v > f[p]) f[p] = v
                }
            }
            spreadPos = wrap256(spreadPos + 1)
        }

        private fun doRecognize() {
            val f46 = fftOut[wrap256(fftPos - 46)]
            val s49 = spreadOut[wrap256(spreadPos - 49)]
            val freqOffs = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
            val timeOffs = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

            for (b in 10..1014) {
                val mag = f46[b]
                if (mag < 0.015625f) continue
                if (mag < s49[b - 1]) continue

                var maxF = 0f
                for (o in freqOffs) {
                    val nb = b + o
                    if (nb >= 0 && nb < 1025) {
                        val v = s49[nb]
                        if (v > maxF) maxF = v
                    }
                }
                if (mag <= maxF) continue

                var maxT = maxF
                for (o in timeOffs) {
                    val other = spreadOut[wrap256(spreadPos + o)]
                    val v = other[b - 1]
                    if (v > maxT) maxT = v
                }
                if (mag <= maxT) continue

                val pass = doneCount - 46
                val m0 = logMag(f46[b])
                val mBefore = logMag(f46[b - 1])
                val mAfter = logMag(f46[b + 1])
                val v1 = m0 * 2f - mBefore - mAfter
                if (v1 <= 0f) continue
                val v2 = (mAfter - mBefore) * 32f / v1
                val cbin = (b * 64 + v2.toInt()) and 0xFFFF
                val hz = cbin * 0.1220703125f

                val band = when {
                    hz >= 250f && hz <= 519f -> 0
                    hz >= 520f && hz <= 1449f -> 1
                    hz >= 1450f && hz <= 3499f -> 2
                    hz >= 3500f && hz <= 5500f -> 3
                    else -> -1
                }
                if (band < 0) continue

                bandPeaks[band].add(Peak(pass, m0.toInt(), cbin))
            }
        }

        private fun logMag(v: Float): Float {
            var l = ln(v.toDouble()).toFloat()
            val min = (1.0 / 64.0).toFloat()
            // Rust: ln(v).max(1/64) — pero ln(1/64) es negativo, así que max con 1/64 no tiene sentido
            // En realidad es: max(ln(v), ln(1/64))? No, el código dice .max(1.0/64.0)
            // ln(v) donde v>=1e-10, ln(1e-10)=-23. Si ln(v) < 0.015625, usa 0.015625
            if (l < min) l = min
            return l * 1477.3f + 6144f
        }

        private fun fft2048(input: FloatArray): FloatArray {
            val n = 2048
            val re = FloatArray(n)
            val im = FloatArray(n)
            for (i in 0 until n) re[i] = input[i]

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

            var len = 2
            while (len <= n) {
                val ang = -2.0 * PI / len
                val wRe = cos(ang).toFloat()
                val wIm = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var wr = 1f
                    var wi = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val bIdx = i + k + len / 2
                        val ur = re[a]
                        val ui = im[a]
                        val vr = re[bIdx] * wr - im[bIdx] * wi
                        val vi = re[bIdx] * wi + im[bIdx] * wr
                        re[a] = ur + vr
                        im[a] = ui + vi
                        re[bIdx] = ur - vr
                        im[bIdx] = ui - vi
                        val nwr = wr * wRe - wi * wIm
                        wi = wr * wIm + wi * wRe
                        wr = nwr
                    }
                    i += len
                }
                len = len shl 1
            }

            val out = FloatArray(2050)
            for (i in 0 until 1025) {
                out[i * 2] = re[i]
                out[i * 2 + 1] = im[i]
            }
            return out
        }

        private fun encode(numSamples: Int): String {
            val bytes = mutableListOf<Byte>()
            fun w32(v: Long) {
                bytes.add((v and 0xFF).toByte())
                bytes.add(((v shr 8) and 0xFF).toByte())
                bytes.add(((v shr 16) and 0xFF).toByte())
                bytes.add(((v shr 24) and 0xFF).toByte())
            }
            fun w16(v: Int) {
                bytes.add((v and 0xFF).toByte())
                bytes.add(((v shr 8) and 0xFF).toByte())
            }

            w32(0xcafe2580L)
            w32(0L)
            w32(0L)
            w32(0x94119c00L)
            w32(0L); w32(0L); w32(0L)
            w32(3L shl 27)
            w32(0L); w32(0L)
            w32(numSamples.toLong() + 3840L)
            w32(((15 shl 19) + 0x40000).toLong())
            w32(0x40000000L)
            w32(0L)

            for (b in 0 until 4) {
                val peaks = bandPeaks[b]
                if (peaks.isEmpty()) continue
                val pb = mutableListOf<Byte>()
                var lastPass = 0
                for (p in peaks) {
                    if (p.pass - lastPass >= 255) {
                        pb.add(0xFF.toByte())
                        val v = p.pass.toLong()
                        pb.add((v and 0xFF).toByte())
                        pb.add(((v shr 8) and 0xFF).toByte())
                        pb.add(((v shr 16) and 0xFF).toByte())
                        pb.add(((v shr 24) and 0xFF).toByte())
                        lastPass = p.pass
                    }
                    pb.add(((p.pass - lastPass) and 0xFF).toByte())
                    pb.add((p.mag and 0xFF).toByte())
                    pb.add(((p.mag shr 8) and 0xFF).toByte())
                    pb.add((p.bin and 0xFF).toByte())
                    pb.add(((p.bin shr 8) and 0xFF).toByte())
                    lastPass = p.pass
                }
                w32(0x60030040L + b)
                w32(pb.size.toLong())
                bytes.addAll(pb)
                val pad = (4 - pb.size % 4) % 4
                repeat(pad) { bytes.add(0) }
            }

            val arr = bytes.toByteArray()
            val sz = arr.size - 48
            fun wAt(off: Int, v: Long) {
                arr[off] = (v and 0xFF).toByte()
                arr[off + 1] = ((v shr 8) and 0xFF).toByte()
                arr[off + 2] = ((v shr 16) and 0xFF).toByte()
                arr[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            wAt(8, sz.toLong())
            wAt(52, sz.toLong())
            val crc = CRC32()
            crc.update(arr, 8, arr.size - 8)
            wAt(4, crc.value)

            val b64 = Base64.encodeToString(arr, Base64.NO_WRAP)
            return "data:audio/vnd.shazam.sig;base64,$b64"
        }
    }
}
