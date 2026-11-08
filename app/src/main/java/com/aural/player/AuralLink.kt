// Aural link — comparte una canción con un solo enlace (v1.0.9)
package com.aural.player

import android.net.Uri
import com.aural.player.data.Song

object AuralLink {
    /** Página de aterrizaje (Render): la abre quien no tiene la app. */
    const val WEB_BASE = "https://auralplayer.onrender.com/l/"
    const val SCHEME = "aural"
    const val HOST = "song"

    /** Título efectivo: el editado por el usuario si existe, si no el original. */
    fun effectiveTitle(song: Song, meta: Triple<String, String, String>?): String =
        meta?.first?.ifBlank { song.title } ?: song.title

    /** Artista efectivo: el editado por el usuario si existe, si no el original. */
    fun effectiveArtist(song: Song, meta: Triple<String, String, String>?): String =
        meta?.second?.ifBlank { song.artist } ?: song.artist

    /** Enlace web para compartir: lleva título y artista en la propia URL. */
    fun buildWebLink(song: Song, meta: Triple<String, String, String>? = null): String {
        val title = Uri.encode(effectiveTitle(song, meta))
        val artist = Uri.encode(displayArtist(effectiveArtist(song, meta)))
        return "$WEB_BASE?t=$title&a=$artist"
    }

    /** Enlace directo a la app (lo usa la página de aterrizaje). */
    fun buildDeepLink(song: Song, meta: Triple<String, String, String>? = null): String {
        val title = Uri.encode(effectiveTitle(song, meta))
        val artist = Uri.encode(displayArtist(effectiveArtist(song, meta)))
        return "$SCHEME://$HOST?t=$title&a=$artist"
    }

    /** Artista para el link: vacío si es desconocido. */
    fun displayArtist(artist: String): String {
        val a = artist.trim()
        return if (a.isEmpty() || a == "Artista misterioso") "" else a
    }

    /** Texto para compartir por WhatsApp y demás. */
    fun shareText(song: Song, meta: Triple<String, String, String>? = null): String {
        val artist = displayArtist(effectiveArtist(song, meta))
        val who = if (artist.isEmpty()) "" else " de $artist"
        return "🎵 \"${effectiveTitle(song, meta)}\"$who — escúchala en AuralPlayer: ${buildWebLink(song, meta)}"
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
