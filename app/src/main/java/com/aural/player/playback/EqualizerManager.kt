// Ecualizador de 5 bandas + normalización de volumen (LoudnessEnhancer)
package com.aural.player.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

/**
 * Gestiona el ecualizador y la normalización atados a la sesión de audio
 * de ExoPlayer. Los ajustes se guardan en SharedPreferences.
 */
object EqualizerManager {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var prefs: android.content.SharedPreferences? = null

    var enabled: Boolean = false
        private set

    var loudnessEnabled: Boolean = false
        private set

    /** Debe llamarse al crear el reproductor, con su audioSessionId. */
    fun init(context: Context, audioSessionId: Int) {
        prefs = context.getSharedPreferences("aural_equalizer", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean("enabled", false) ?: false
        loudnessEnabled = prefs?.getBoolean("loudness_enabled", false) ?: false
        releaseEffects()
        if (audioSessionId == 0) return
        try {
            equalizer = Equalizer(0, audioSessionId)
            loudness = LoudnessEnhancer(audioSessionId)
            applySavedSettings()
            equalizer?.enabled = enabled
            loudness?.enabled = loudnessEnabled
        } catch (e: Exception) {
            android.util.Log.e("EqualizerManager", "No soportado: ${e.message}")
            releaseEffects()
        }
    }

    fun release() {
        releaseEffects()
        prefs = null
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
    }

    val isAvailable: Boolean get() = equalizer != null

    fun setEnabled(value: Boolean) {
        enabled = value
        equalizer?.enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }

    fun setLoudnessEnabled(value: Boolean) {
        loudnessEnabled = value
        loudness?.enabled = value
        prefs?.edit()?.putBoolean("loudness_enabled", value)?.apply()
    }

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** Frecuencia central de la banda en Hz (ej: 60, 230, 910...). */
    fun bandFrequencyHz(band: Int): Int = try {
        (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000
    } catch (e: Exception) { 0 }

    /** Rango de nivel en dB (ej: -15..15). */
    fun levelRangeDb(): IntRange {
        val range = try { equalizer?.bandLevelRange } catch (e: Exception) { null }
        return if (range != null && range.size == 2) {
            (range[0] / 100)..(range[1] / 100)
        } else -15..15
    }

    fun getBandLevelDb(band: Int): Int = try {
        (equalizer?.getBandLevel(band.toShort()) ?: 0) / 100
    } catch (e: Exception) { 0 }

    fun setBandLevelDb(band: Int, db: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), (db * 100).toShort())
            saveBandLevels()
        } catch (e: Exception) { /* ignorar */ }
    }

    val presets: List<String>
        get() {
            val eq = equalizer ?: return emptyList()
            return try {
                (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun usePreset(index: Int) {
        try {
            equalizer?.usePreset(index.toShort())
            saveBandLevels()
        } catch (e: Exception) { /* ignorar */ }
    }

    /** Ganancia objetivo de normalización en dB (0..10). */
    fun getLoudnessGainDb(): Int =
        prefs?.getInt("loudness_gain", 0) ?: 0

    fun setLoudnessGainDb(db: Int) {
        try {
            loudness?.setTargetGain(db * 100)
            prefs?.edit()?.putInt("loudness_gain", db)?.apply()
        } catch (e: Exception) { /* ignorar */ }
    }

    private fun saveBandLevels() {
        val eq = equalizer ?: return
        val levels = (0 until eq.numberOfBands).joinToString(",") {
            (eq.getBandLevel(it.toShort()) / 100).toString()
        }
        prefs?.edit()?.putString("bands", levels)?.apply()
    }

    private fun applySavedSettings() {
        val eq = equalizer ?: return
        prefs?.getString("bands", null)?.split(",")?.forEachIndexed { i, s ->
            if (i < eq.numberOfBands) {
                runCatching {
                    eq.setBandLevel(i.toShort(), (s.toInt() * 100).toShort())
                }
            }
        }
        runCatching { loudness?.setTargetGain(getLoudnessGainDb() * 100) }
    }
}
