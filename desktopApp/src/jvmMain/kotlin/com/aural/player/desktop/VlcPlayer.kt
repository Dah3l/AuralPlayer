package com.aural.player.desktop

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.runtime.RuntimeUtil
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
    private val onError: () -> Unit
) {
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null

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
                NativeLibrary.addSearchPath(
                    RuntimeUtil.getLibVlcLibraryName(),
                    vlcDir.absolutePath
                )
            } else if (!NativeDiscovery().discover()) {
                return false
            }
            val args = mutableListOf("--quiet")
            if (vlcDir != null) {
                args += "--plugin-path=${File(vlcDir, "plugins").absolutePath}"
            }
            factory = MediaPlayerFactory(*args.toTypedArray())
            player = factory!!.mediaPlayers().newMediaPlayer().apply {
                events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun finished(mediaPlayer: MediaPlayer) {
                        onFinished()
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        onError()
                    }
                })
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun play(filePath: String): Boolean {
        val mediaPlayer = player ?: return false
        return try {
            mediaPlayer.media().play(File(filePath).toURI().toString())
        } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }

    fun seek(fraction: Float) {
        try {
            player?.controls()?.setPosition(fraction.coerceIn(0f, 1f))
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            player?.controls()?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        try {
            factory?.release()
        } catch (_: Exception) {
        }
        player = null
        factory = null
    }

    /**
     * Busca la carpeta con libvlc.dll: primero la incluida en el
     * instalador (recursos de la app), luego la del build local.
     */
    private fun findVlcDir(): File? {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val candidates = listOfNotNull(
            System.getProperty("aural.vlc.path")?.let { File(it) },
            resourcesDir?.let { File(it, "vlc") },
            resourcesDir?.let { File(it, "windows-x64/vlc") },
            File(System.getProperty("user.dir"), "build/appResources/windows-x64/vlc"),
            File(System.getProperty("user.dir"), "desktopApp/build/appResources/windows-x64/vlc")
        )
        return candidates.firstOrNull { File(it, "libvlc.dll").exists() }
    }
}
