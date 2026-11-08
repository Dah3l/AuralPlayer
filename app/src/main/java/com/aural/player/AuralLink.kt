// Aural link — comparte una canción con un solo enlace (v1.0.9)
package com.aural.player

import android.net.Uri
import com.aural.player.data.Song

object AuralLink {
    /** Página de aterrizaje (Render): la abre quien no tiene la app. */
    const val WEB_BASE = "https://auralplayer.onrender.com/l/"
    const val SCHEME = "aural"
    const val HOST = "song"

    /** Enlace web para compartir: lleva título y artista en la propia URL. */
    fun buildWebLink(song: Song): String {
        val title = Uri.encode(song.title)
        val artist = Uri.encode(displayArtist(song))
        return "$WEB_BASE?t=$title&a=$artist"
    }

    /** Enlace directo a la app (lo usa la página de aterrizaje). */
    fun buildDeepLink(song: Song): String {
        val title = Uri.encode(song.title)
        val artist = Uri.encode(displayArtist(song))
        return "$SCHEME://$HOST?t=$title&a=$artist"
    }

    /** Artista para el link: vacío si es desconocido. */
    fun displayArtist(song: Song): String {
        val a = song.artist.trim()
        return if (a.isEmpty() || a == "Artista misterioso") "" else a
    }

    /** Texto para compartir por WhatsApp y demás. */
    fun shareText(song: Song): String {
        val artist = displayArtist(song)
        val who = if (artist.isEmpty()) "" else " de $artist"
        return "🎵 \"${song.title}\"$who — escúchala en AuralPlayer: ${buildWebLink(song)}"
    }

    /**
     * Lee un link entrante (aural:// o el https de la página) y devuelve
     * la búsqueda para YouTube ("título artista"), o null si no es un Aural link.
     */
    fun parseToQuery(uri: Uri?): String? {
        if (uri == null) return null
        val isDeep = uri.scheme == SCHEME && uri.host == HOST
        val isWeb = uri.scheme == "https" && (
            (uri.host == "auralplayer.onrender.com" && (uri.path ?: "").startsWith("/l")) ||
            (uri.host == "dah3l.github.io" && (uri.path ?: "").startsWith("/AuralPlayer/l"))
        )
        if (!isDeep && !isWeb) return null
        val title = uri.getQueryParameter("t")?.trim().orEmpty()
        val artist = uri.getQueryParameter("a")?.trim().orEmpty()
        return listOf(title, artist).filter { it.isNotEmpty() }
            .joinToString(" ").ifBlank { null }
    }
}
