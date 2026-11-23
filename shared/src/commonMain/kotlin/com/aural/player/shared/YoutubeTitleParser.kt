// Título y artista reales de un video de YouTube: el título del video
// trae el canal, el artista y la canción mezclados ("Canal - Artista -
// Canción (Official Video)"). Esto los separa para que la biblioteca,
// el nombre del archivo, los tags incrustados y la búsqueda de letras
// usen la canción de verdad, no el título del video. Sin dependencias
// de plataforma: lo usan Android y PC con la misma lógica.
package com.aural.player.shared

object YoutubeTitleParser {

    /** Artista y título limpios de un video. */
    data class Parsed(val artist: String, val title: String)

    // Sufijos de video (al final, entre paréntesis o corchetes) que no
    // son parte de la canción: se quitan. "(Remix)", "(Acoustic)" o
    // "(Live)" NO están aquí: son parte de la canción.
    private val VIDEO_SUFFIXES = setOf(
        "official video", "official audio", "official music video",
        "official lyric video", "official lyrics video", "official lyrics",
        "lyric video", "lyrics video", "lyrics", "lyric",
        "music video", "video", "audio", "official",
        "visualizer", "official visualizer",
        "video oficial", "audio oficial", "letra oficial", "letra",
        "video con letra"
    )

    /**
     * Parte el título del video en artista y canción. Si el título trae
     * "Artista - Canción", el artista es lo de antes del primer guion y
     * la canción lo de después (con los sufijos de video quitados). Si no
     * trae guion, el artista es el canal (sin " - Topic") y la canción es
     * el título limpio.
     */
    fun parse(videoTitle: String, uploader: String): Parsed {
        val channel = uploader.trim().removeSuffix(" - Topic").trim()
        val title = stripVideoSuffix(videoTitle.trim())
        // Primer separador " - ", " – " o " — " (el del artista).
        val separators = listOf(" - ", " – ", " — ")
        var sepAt = -1
        var sepLen = 0
        for (s in separators) {
            val idx = title.indexOf(s)
            if (idx > 0 && (sepAt < 0 || idx < sepAt)) {
                sepAt = idx
                sepLen = s.length
            }
        }
        if (sepAt > 0) {
            val artist = title.substring(0, sepAt).trim()
            val song = stripVideoSuffix(title.substring(sepAt + sepLen).trim())
            if (artist.isNotBlank() && song.isNotBlank()) {
                return Parsed(artist = artist, title = song)
            }
        }
        return Parsed(artist = channel, title = title)
    }

    // Quita los sufijos de video del final ("(Official Video)",
    // "[Lyrics]"...), en bucle por si trae varios seguidos.
    private fun stripVideoSuffix(text: String): String {
        var s = text.trim()
        repeat(3) {
            val match = Regex("""[\(\[]([^\]\)]+)[\)\]]\s*$""").find(s) ?: return s
            val inner = match.groupValues[1].trim().lowercase()
            if (inner !in VIDEO_SUFFIXES) return s
            s = s.substring(0, match.range.first).trim()
        }
        return s
    }
}
