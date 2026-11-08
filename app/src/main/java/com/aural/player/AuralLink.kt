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
    fun buildWebLink(title: String, artist: String): String {
        val t = Uri.encode(title)
        val a = Uri.encode(displayArtist(artist))
        return "$WEB_BASE?t=$t&a=$a"
    }

    /** Enlace web para compartir: lleva título y artista en la propia URL. */
    fun buildWebLink(song: Song, meta: Triple<String, String, String>? = null): String =
        buildWebLink(effectiveTitle(song, meta), effectiveArtist(song, meta))

    /** Enlace directo a la app (lo usa la página de aterrizaje). */
    fun buildDeepLink(title: String, artist: String): String {
        val t = Uri.encode(title)
        val a = Uri.encode(displayArtist(artist))
        return "$SCHEME://$HOST?t=$t&a=$a"
    }

    /** Enlace directo a la app (lo usa la página de aterrizaje). */
    fun buildDeepLink(song: Song, meta: Triple<String, String, String>? = null): String =
        buildDeepLink(effectiveTitle(song, meta), effectiveArtist(song, meta))

    /** Artista para el link: vacío si es desconocido. */
    fun displayArtist(artist: String): String {
        val a = artist.trim()
        return if (a.isEmpty() || a == "Artista misterioso") "" else a
    }

    /** Texto para compartir por WhatsApp y demás. */
    fun shareText(title: String, artist: String): String {
        val a = displayArtist(artist)
        val who = if (a.isEmpty()) "" else " de $a"
        return "🎵 \"$title\"$who — escúchala en AuralPlayer: ${buildWebLink(title, artist)}"
    }

    /** Texto para compartir por WhatsApp y demás. */
    fun shareText(song: Song, meta: Triple<String, String, String>? = null): String =
        shareText(effectiveTitle(song, meta), effectiveArtist(song, meta))

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
