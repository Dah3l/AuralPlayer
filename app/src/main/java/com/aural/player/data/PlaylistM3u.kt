// Importación y exportación de playlists en formato .m3u
package com.aural.player.data

/**
 * Construye el contenido .m3u de una playlist.
 * Formato estándar: cabecera #EXTM3U + líneas #EXTINF con duración y título.
 */
fun buildM3u(songs: List<Song>): String = buildString {
    appendLine("#EXTM3U")
    for (song in songs) {
        val seconds = (song.durationMs / 1000).coerceAtLeast(0)
        appendLine("#EXTINF:$seconds,${song.artist} - ${song.title}")
        appendLine(song.uri)
    }
}

/** Entrada extraída de un archivo .m3u. */
data class M3uEntry(
    val artist: String,
    val title: String
)

/**
 * Extrae las entradas de un contenido .m3u.
 * Soporta líneas #EXTINF:segundos,Artista - Título y líneas simples con el título.
 */
fun parseM3u(content: String): List<M3uEntry> {
    val entries = mutableListOf<M3uEntry>()
    for (rawLine in content.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#EXTM3U")) continue
        if (line.startsWith("#EXTINF:")) {
            // Formato: #EXTINF:210,Artista - Título
            val info = line.substringAfter(",", "")
            if (info.isBlank()) continue
            val parts = info.split(" - ", limit = 2)
            if (parts.size == 2) {
                entries += M3uEntry(artist = parts[0].trim(), title = parts[1].trim())
            } else {
                entries += M3uEntry(artist = "", title = info.trim())
            }
        } else if (!line.startsWith("#")) {
            // Línea simple: usar el nombre del archivo como título
            val name = line.substringAfterLast("/").substringBeforeLast(".")
            if (name.isNotBlank()) entries += M3uEntry(artist = "", title = name)
        }
    }
    return entries
}
