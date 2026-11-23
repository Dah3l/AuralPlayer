// Ecualizador de 5 bandas + normalización de volumen (LoudnessEnhancer)
// v1.0.7: + refuerzo de bajos (BassBoost) + audio inmersivo (Virtualizer)
package com.aural.player.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

/**
 * Gestiona el ecualizador y los efectos atados a la sesión de audio
 * de ExoPlayer. Los ajustes se guardan en SharedPreferences.
 */
object EqualizerManager {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    // Segundo juego de efectos, para la sesión del segundo reproductor
    // del fundido (crossfade): cada reproductor tiene su propio
    // audioSessionId y los efectos van atados a la sesión, no a la app.
    // Los ajustes guardados son los mismos para los dos juegos.
    private var equalizer2: Equalizer? = null
    private var loudness2: LoudnessEnhancer? = null
    private var bassBoost2: BassBoost? = null
    private var virtualizer2: Virtualizer? = null
    private var prefs: android.content.SharedPreferences? = null

    var enabled: Boolean = false
        private set

    var loudnessEnabled: Boolean = false
        private set

    var bassBoostEnabled: Boolean = false
        private set

    var virtualizerEnabled: Boolean = false
        private set

    /** Debe llamarse al crear el reproductor, con su audioSessionId. */
    fun init(context: Context, audioSessionId: Int) {
        prefs = context.getSharedPreferences("aural_equalizer", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean("enabled", false) ?: false
        loudnessEnabled = prefs?.getBoolean("loudness_enabled", false) ?: false
        bassBoostEnabled = prefs?.getBoolean("bass_enabled", false) ?: false
        virtualizerEnabled = prefs?.getBoolean("virtualizer_enabled", false) ?: false
        releaseEffects()
        if (audioSessionId == 0) return
        try {
            equalizer = Equalizer(0, audioSessionId)
            loudness = LoudnessEnhancer(audioSessionId)
            applySavedSettings(equalizer, loudness)
            equalizer?.enabled = enabled
            loudness?.enabled = loudnessEnabled
        } catch (e: Exception) {
            android.util.Log.e("EqualizerManager", "No soportado: ${e.message}")
        }
        // BassBoost y Virtualizer son opcionales: si el dispositivo no los
        // soporta, se ignora sin romper el resto.
        try {
            bassBoost = BassBoost(0, audioSessionId)
            bassBoost?.setStrength(getBassBoostStrength().toShort())
            bassBoost?.enabled = bassBoostEnabled
        } catch (e: Exception) {
            android.util.Log.w("EqualizerManager", "Sin BassBoost: ${e.message}")
            bassBoost = null
        }
        try {
            virtualizer = Virtualizer(0, audioSessionId)
            virtualizer?.setStrength(getVirtualizerStrength().toShort())
            virtualizer?.enabled = virtualizerEnabled
        } catch (e: Exception) {
            android.util.Log.w("EqualizerManager", "Sin Virtualizer: ${e.message}")
            virtualizer = null
        }
    }

    /** Segundo juego de efectos para la sesión del reproductor del
     *  fundido. NO toca el juego principal (init sí lo libera, este no). */
    fun initSecondSession(context: Context, audioSessionId: Int) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("aural_equalizer", Context.MODE_PRIVATE)
            enabled = prefs?.getBoolean("enabled", false) ?: false
            loudnessEnabled = prefs?.getBoolean("loudness_enabled", false) ?: false
            bassBoostEnabled = prefs?.getBoolean("bass_enabled", false) ?: false
            virtualizerEnabled = prefs?.getBoolean("virtualizer_enabled", false) ?: false
        }
        releaseSecondEffects()
        if (audioSessionId == 0) return
        try {
            equalizer2 = Equalizer(0, audioSessionId)
            loudness2 = LoudnessEnhancer(audioSessionId)
            applySavedSettings(equalizer2, loudness2)
            equalizer2?.enabled = enabled
            loudness2?.enabled = loudnessEnabled
        } catch (e: Exception) {
            android.util.Log.e("EqualizerManager", "No soportado (2): ${e.message}")
        }
        try {
            bassBoost2 = BassBoost(0, audioSessionId)
            bassBoost2?.setStrength(getBassBoostStrength().toShort())
            bassBoost2?.enabled = bassBoostEnabled
        } catch (e: Exception) {
            android.util.Log.w("EqualizerManager", "Sin BassBoost (2): ${e.message}")
            bassBoost2 = null
        }
        try {
            virtualizer2 = Virtualizer(0, audioSessionId)
            virtualizer2?.setStrength(getVirtualizerStrength().toShort())
            virtualizer2?.enabled = virtualizerEnabled
        } catch (e: Exception) {
            android.util.Log.w("EqualizerManager", "Sin Virtualizer (2): ${e.message}")
            virtualizer2 = null
        }
    }

    fun release() {
        releaseEffects()
        releaseSecondEffects()
        prefs = null
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        loudness = null
        bassBoost = null
        virtualizer = null
    }

    private fun releaseSecondEffects() {
        runCatching { equalizer2?.release() }
        runCatching { loudness2?.release() }
        runCatching { bassBoost2?.release() }
        runCatching { virtualizer2?.release() }
        equalizer2 = null
        loudness2 = null
        bassBoost2 = null
        virtualizer2 = null
    }

    val isAvailable: Boolean get() = equalizer != null
    val isBassBoostAvailable: Boolean get() = bassBoost != null
    val isVirtualizerAvailable: Boolean get() = virtualizer != null

    /**
     * Intercambio de papeles tras un fundido: el reproductor que era
     * la segunda voz pasa a ser el principal y viceversa. Los efectos
     * van atados a la sesión de audio, así que se intercambian las
     * referencias: si se re-creara el juego principal con init() (como
     * hacía el fundido), el nuevo principal quedaba con DOS
     * ecualizadores (el viejo segundo juego + el nuevo, el doble de
     * ganancia) y la reserva sin ninguno. Con el intercambio, cada
     * sesión keeps su juego y el "principal" siempre es el de la
     * sesión que suena.
     */
    fun swapRoles() {
        val eq = equalizer; equalizer = equalizer2; equalizer2 = eq
        val ld = loudness; loudness = loudness2; loudness2 = ld
        val bb = bassBoost; bassBoost = bassBoost2; bassBoost2 = bb
        val vz = virtualizer; virtualizer = virtualizer2; virtualizer2 = vz
    }

    /** La mezcla DJ está moviendo los bajos (el fundido la pone y la quita). */
    var bassSwapActive: Boolean = false

    /**
     * Nivel de una banda en UN juego de efectos, sin guardar: lo usa
     * la rampa de bajos de la mezcla DJ (el nivel guardado del usuario
     * no se toca; al terminar el fundido se restaura).
     */
    fun setBandLevelTransient(second: Boolean, band: Int, db: Int) {
        try {
            val eq = if (second) equalizer2 else equalizer
            eq?.setBandLevel(band.toShort(), (db * 100).toShort())
        } catch (e: Exception) { /* ignorar */ }
    }

    /** Nivel guardado de una banda en dB (el del usuario, no el que la
     *  mezcla está moviendo ahora mismo). */
    fun getSavedBandLevelDb(band: Int): Int {
        return try {
            prefs?.getString("bands", null)?.split(",")?.getOrNull(band)?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        equalizer?.enabled = value
        equalizer2?.enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }

    fun setLoudnessEnabled(value: Boolean) {
        loudnessEnabled = value
        loudness?.enabled = value
        loudness2?.enabled = value
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
            equalizer2?.setBandLevel(band.toShort(), (db * 100).toShort())
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

    /** Índice del preset activo (-1 si es personalizado o no disponible). */
    fun getCurrentPreset(): Int = try {
        equalizer?.currentPreset?.toInt() ?: -1
    } catch (e: Exception) { -1 }

    fun usePreset(index: Int) {
        try {
            equalizer?.usePreset(index.toShort())
            equalizer2?.usePreset(index.toShort())
            saveBandLevels()
        } catch (e: Exception) { /* ignorar */ }
    }

    /** Ganancia objetivo de normalización en dB (0..10). */
    fun getLoudnessGainDb(): Int =
        prefs?.getInt("loudness_gain", 0) ?: 0

    fun setLoudnessGainDb(db: Int) {
        try {
            loudness?.setTargetGain(db * 100)
            loudness2?.setTargetGain(db * 100)
            prefs?.edit()?.putInt("loudness_gain", db)?.apply()
        } catch (e: Exception) { /* ignorar */ }
    }

    // ------------------------- Refuerzo de bajos (v1.0.7) -------------------------

    fun setBassBoostEnabled(value: Boolean) {
        bassBoostEnabled = value
        try { bassBoost?.enabled = value } catch (e: Exception) { /* ignorar */ }
        try { bassBoost2?.enabled = value } catch (e: Exception) { /* ignorar */ }
        prefs?.edit()?.putBoolean("bass_enabled", value)?.apply()
    }

    /** Intensidad del refuerzo de bajos (0..1000). */
    fun getBassBoostStrength(): Int =
        prefs?.getInt("bass_strength", 500) ?: 500

    fun setBassBoostStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        try { bassBoost?.setStrength(s.toShort()) } catch (e: Exception) { /* ignorar */ }
        try { bassBoost2?.setStrength(s.toShort()) } catch (e: Exception) { /* ignorar */ }
        prefs?.edit()?.putInt("bass_strength", s)?.apply()
    }

    // ------------------------- Audio inmersivo / Virtualizer (v1.0.7) -------------------------

    fun setVirtualizerEnabled(value: Boolean) {
        virtualizerEnabled = value
        try { virtualizer?.enabled = value } catch (e: Exception) { /* ignorar */ }
        try { virtualizer2?.enabled = value } catch (e: Exception) { /* ignorar */ }
        prefs?.edit()?.putBoolean("virtualizer_enabled", value)?.apply()
    }

    /** Intensidad del efecto envolvente (0..1000). */
    fun getVirtualizerStrength(): Int =
        prefs?.getInt("virtualizer_strength", 500) ?: 500

    fun setVirtualizerStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        try { virtualizer?.setStrength(s.toShort()) } catch (e: Exception) { /* ignorar */ }
        try { virtualizer2?.setStrength(s.toShort()) } catch (e: Exception) { /* ignorar */ }
        prefs?.edit()?.putInt("virtualizer_strength", s)?.apply()
    }

    private fun saveBandLevels() {
        val eq = equalizer ?: return
        // Durante el cambio de bajos de la mezcla, la banda 0 del juego
        // principal está bajada por la rampa: si el usuario guarda el EQ
        // en ese momento, la banda 0 conserva su valor guardado (si no,
        // el nivel bajado se grabaría como ajuste del usuario).
        val levels = (0 until eq.numberOfBands).joinToString(",") {
            if (it == 0 && bassSwapActive) getSavedBandLevelDb(0).toString()
            else (eq.getBandLevel(it.toShort()) / 100).toString()
        }
        prefs?.edit()?.putString("bands", levels)?.apply()
    }

    private fun applySavedSettings(eq: Equalizer?, loudnessFx: LoudnessEnhancer?) {
        val eqFx = eq ?: return
        prefs?.getString("bands", null)?.split(",")?.forEachIndexed { i, s ->
            if (i < eqFx.numberOfBands) {
                runCatching {
                    eqFx.setBandLevel(i.toShort(), (s.toInt() * 100).toShort())
                }
            }
        }
        runCatching { loudnessFx?.setTargetGain(getLoudnessGainDb() * 100) }
    }
}
