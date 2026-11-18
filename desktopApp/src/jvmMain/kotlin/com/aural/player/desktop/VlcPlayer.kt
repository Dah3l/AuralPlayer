package com.aural.player.desktop

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File

/**
 * Motor de reproducción con VLCJ (LibVLC). Sin video: solo audio.
 *
 * Las librerías de VLC viajan DENTRO del instalador (carpeta `vlc`
 * junto a los recursos de la app); el usuario no necesita VLC
 * instalado. Si no se encuentran las incluidas, se intenta la
 * instalación de VLC del sistema como respaldo.
 */
class VlcPlayer(
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        /** Ruta del registro de VLC (diagnóstico de fallos de reproducción). */
        val LOG_PATH: String =
            File(System.getProperty("java.io.tmpdir"), "aural-vlc.log").absolutePath
    }

    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null

    /** Velocidad elegida: VLC la pierde al cambiar de pista, se reaplica al sonar. */
    private var currentRate: Float = 1f

    /** Ecualizador reutilizado entre aplicaciones (VLC lo toma por referencia). */
    private var equalizer: Equalizer? = null

    /** Mensaje del último fallo de inicialización, para mostrarlo en la UI. */
    var initError: String? = null
        private set

    /** Dónde se buscó VLC y qué se encontró: diagnóstico para la UI. */
    var diagnostics: String? = null
        private set

    val isPlaying: Boolean get() = player?.status()?.isPlaying() == true
    val positionMs: Long get() = player?.status()?.time() ?: 0L
    val durationMs: Long get() = player?.status()?.length()?.takeIf { it > 0 } ?: 0L

    /**
     * Localiza las librerías de VLC y crea el reproductor.
     * Devuelve false si no hay VLC disponible de ninguna forma.
     */
    fun init(): Boolean {
        return try {
            val vlcDir = findVlcDir()
            if (vlcDir != null) {
                // "libvlc" es el nombre que JNA resuelve a libvlc.dll
                // en Windows (RuntimeUtil ya no existe en vlcj 4.12).
                NativeLibrary.addSearchPath("libvlc", vlcDir.absolutePath)
            } else if (!NativeDiscovery().discover()) {
                return false
            }
            val args = mutableListOf(
                // Registro detallado en un archivo: sin esto, VLC
                // falla en silencio y no hay forma de saber si fue
                // el acceso al archivo, el demuxer, el decodificador
                // o la salida de audio.
                "--verbose=2",
                "--file-logging",
                "--logfile=$LOG_PATH"
            )
            if (vlcDir != null) {
                args += "--plugin-path=${File(vlcDir, "plugins").absolutePath}"
            }
            factory = MediaPlayerFactory(*args.toTypedArray())
            player = factory!!.mediaPlayers().newMediaPlayer().apply {
                events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun finished(mediaPlayer: MediaPlayer) {
                        onFinished()
                    }

                    override fun playing(mediaPlayer: MediaPlayer) {
                        // La velocidad se pierde al cambiar de pista:
                        // se reaplica cada vez que una empieza a sonar.
                        if (currentRate != 1f) {
                            try {
                                mediaPlayer.controls().setRate(currentRate)
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        onError(lastPath ?: "?")
                    }
                })
            }
            true
        } catch (t: Throwable) {
            // Los fallos de carga nativa (UnsatisfiedLinkError y
            // compañía) son Error, no Exception: si no se atrapan
            // aquí, matan la corrutina y la UI se queda en el
            // spinner para siempre.
            initError = t.message
            t.printStackTrace()
            false
        }
    }

    /** Ruta del último archivo que se intentó reproducir (diagnóstico). */
    private var lastPath: String? = null

    fun play(filePath: String): Boolean {
        lastPath = filePath
        val mediaPlayer = player ?: return false
        return try {
            // MRL 100% ASCII: toASCIIString() codifica como %XX
            // cualquier carácter no ASCII (ñ, tildes). Si la ñ
            // llega cruda en el String, JNA la codifica en
            // windows-1252 (un byte 0xF1) y VLC no abre el
            // archivo. vlcj solo codifica MRLs SIN esquema, así
            // que el MRL con esquema "file" debe ir ya codificado.
            // Además VLC exige file:/// (tres barras): toURI()
            // da file:/ (una barra).
            val ascii = File(filePath).toURI().toASCIIString()
            val mrl = if (ascii.startsWith("file://")) ascii
                      else ascii.replaceFirst("file:/", "file:///")
            mediaPlayer.media().play(mrl)
        } catch (_: Throwable) {
            false
        }
    }

    fun toggle() {
        val mediaPlayer = player ?: return
        try {
            if (mediaPlayer.status().isPlaying()) {
                mediaPlayer.controls().pause()
            } else {
                mediaPlayer.controls().play()
            }
        } catch (_: Throwable) {
        }
    }

    fun seek(fraction: Float) {
        try {
            player?.controls()?.setPosition(fraction.coerceIn(0f, 1f))
        } catch (_: Throwable) {
        }
    }

    /** Velocidad de reproducción (0.5–2x). Se reaplica sola en cada pista. */
    fun setRate(rate: Float) {
        currentRate = rate
        try {
            player?.controls()?.setRate(rate)
        } catch (_: Throwable) {
        }
    }

    fun pause() {
        try {
            player?.controls()?.pause()
        } catch (_: Throwable) {
        }
    }

    /**
     * Ecualizador de 10 bandas de VLC. Apagado = plano (ceros): nunca
     * se pasa null a VLC. El estado lo guarda quien llama; aquí solo
     * se aplica (Equalizer no expone lectura de bandas).
     */
    fun setEqualizer(enabled: Boolean, preamp: Float, amps: List<Float>) {
        try {
            val mediaPlayer = player ?: return
            val factoryRef = factory ?: return
            val eq = equalizer ?: factoryRef.equalizer().newEqualizer().also { equalizer = it }
            val bandCount = try {
                factoryRef.equalizer().bands().size
            } catch (_: Throwable) {
                amps.size
            }
            eq.setPreamp(if (enabled) preamp else 0f)
            val values = FloatArray(bandCount.coerceAtLeast(1)) { i ->
                if (enabled) amps.getOrElse(i) { 0f } else 0f
            }
            eq.setAmps(values)
            mediaPlayer.audio().setEqualizer(eq)
        } catch (_: Throwable) {
        }
    }

    /** Frecuencias centrales de las bandas del ecualizador (Hz), según VLC. */
    fun equalizerBands(): List<Float> {
        return try {
            factory?.equalizer()?.bands() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Volumen 0-100 (100 = nivel nominal de VLC). */
    fun setVolume(value: Int) {
        try {
            player?.audio()?.setVolume(value.coerceIn(0, 100))
        } catch (_: Throwable) {
        }
    }

    /** Silencio on/off; devuelve el estado nuevo. */
    fun toggleMute(): Boolean {
        return try {
            val audio = player?.audio() ?: return false
            audio.setMute(!audio.isMute())
            audio.isMute()
        } catch (_: Throwable) {
            false
        }
    }

    fun stop() {
        try {
            player?.controls()?.stop()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        try {
            factory?.release()
        } catch (_: Throwable) {
        }
        player = null
        factory = null
        equalizer = null
    }

    /**
     * Busca la carpeta con libvlc.dll: primero la incluida en el
     * instalador (varias rutas posibles según dónde deje los
     * recursos el empaquetado), luego la del build local y por
     * último una instalación de VLC del sistema.
     */
    private fun findVlcDir(): File? {
        val checked = mutableListOf<String>()
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val jarDir = try {
            File(VlcPlayer::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        } catch (_: Throwable) {
            null
        }
        val exeDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile }
        val programFiles = System.getenv("ProgramFiles")
        val programFilesX86 = System.getenv("ProgramFiles(x86)")
        val candidates = listOfNotNull(
            System.getProperty("aural.vlc.path")?.let { File(it) },
            resourcesDir?.let { File(it, "vlc") },
            resourcesDir?.let { File(it, "windows-x64/vlc") },
            jarDir?.let { File(it, "vlc") },
            jarDir?.let { File(it, "windows-x64/vlc") },
            exeDir?.let { File(it, "app/vlc") },
            File(System.getProperty("user.dir"), "build/appResources/windows-x64/vlc"),
            File(System.getProperty("user.dir"), "desktopApp/build/appResources/windows-x64/vlc"),
            programFiles?.let { File(it, "VideoLAN/VLC") },
            programFilesX86?.let { File(it, "VideoLAN/VLC") }
        )
        for (candidate in candidates) {
            checked += candidate.absolutePath
            if (File(candidate, "libvlc.dll").exists()) {
                diagnostics = "VLC en: ${candidate.absolutePath}"
                return candidate
            }
        }
        diagnostics = "resources.dir=$resourcesDir | revisado: ${checked.joinToString("; ")}"
        return null
    }
}
