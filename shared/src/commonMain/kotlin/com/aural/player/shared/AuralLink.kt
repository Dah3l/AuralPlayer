// Aural link — comparte una canción con un solo enlace (versión compartida:
// mismo formato que la app Android, sin depender de APIs de plataforma).
package com.aural.player.shared

object AuralLink {
    /** Página de aterrizaje (Render): la abre quien no tiene la app. */
    const val WEB_BASE = "https://auralplayer.onrender.com/l/"

    /** Enlace web para compartir: lleva título y artista en la propia URL. */
    fun buildWebLink(title: String, artist: String): String {
        val t = percentEncode(title)
        val a = percentEncode(displayArtist(artist))
        return "$WEB_BASE?t=$t&a=$a"
    }

    /** Artista para el link: vacío si es desconocido. */
    fun displayArtist(artist: String): String {
        val a = artist.trim()
        return if (a.isEmpty() || a == "Artista misterioso" || a == "Artista desconocido") "" else a
    }

    /** Texto para compartir por WhatsApp y demás. */
    fun shareText(title: String, artist: String): String {
        val a = displayArtist(artist)
        val who = if (a.isEmpty()) "" else " de $a"
        return "🎵 \"$title\"$who — escúchala en AuralPlayer: ${buildWebLink(title, artist)}"
    }

    /** Codificación %XX UTF-8, sin APIs de plataforma (pares sustitutos incluidos). */
    private fun percentEncode(value: String): String {
        val out = StringBuilder()
        var i = 0
        val hex = "0123456789ABCDEF"
        fun appendByte(b: Int) {
            out.append('%').append(hex[(b shr 4) and 0xF]).append(hex[b and 0xF])
        }
        while (i < value.length) {
            val ch = value[i]
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch)
                i++
            } else if (ch == ' ') {
                out.append("%20")
                i++
            } else {
                val codePoint: Int
                if (ch.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) {
                    codePoint = 0x10000 + ((ch.code - 0xD800) shl 10) + (value[i + 1].code - 0xDC00)
                    i += 2
                } else {
                    codePoint = ch.code
                    i++
                }
                when {
                    codePoint < 0x80 -> appendByte(codePoint)
                    codePoint < 0x800 -> {
                        appendByte(0xC0 or (codePoint shr 6))
                        appendByte(0x80 or (codePoint and 0x3F))
                    }
                    codePoint < 0x10000 -> {
                        appendByte(0xE0 or (codePoint shr 12))
                        appendByte(0x80 or ((codePoint shr 6) and 0x3F))
                        appendByte(0x80 or (codePoint and 0x3F))
                    }
                    else -> {
                        appendByte(0xF0 or (codePoint shr 18))
                        appendByte(0x80 or ((codePoint shr 12) and 0x3F))
                        appendByte(0x80 or ((codePoint shr 6) and 0x3F))
                        appendByte(0x80 or (codePoint and 0x3F))
                    }
                }
            }
        }
        return out.toString()
    }
}
