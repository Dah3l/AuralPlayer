// Parser de archivos .lrc (letras sincronizadas)
package com.aural.player.data

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    // [mm:ss.xx] o [mm:ss.xxx]
    private val TIME_REGEX = """\[(\d{1,3}):(\d{2})(?:\.(\d{2,3}))?]""".toRegex()

    /**
     * Parsea el contenido de un .lrc y devuelve las líneas ordenadas por tiempo.
     * Solo extrae el texto — los tiempos se usan internamente para sincronizar.
     */
    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Puede tener múltiples timestamps en una línea: [00:12.00][00:15.00]Texto
            val matches = TIME_REGEX.findAll(line).toList()
            if (matches.isEmpty()) continue

            val text = TIME_REGEX.replace(line, "").trim()
            if (text.isEmpty()) continue

            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val fracStr = m.groupValues[3]
                val ms = when (fracStr.length) {
                    2 -> fracStr.toLongOrNull()?.times(10) ?: 0
                    3 -> fracStr.toLongOrNull() ?: 0
                    else -> 0
                }
                val timeMs = min * 60_000 + sec * 1_000 + ms
                lines.add(LrcLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Dado el tiempo actual de reproducción, devuelve el índice de la línea activa.
     */
    fun currentIndex(lines: List<LrcLine>, positionMs: Long): Int {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i
            else break
        }
        return idx.coerceAtLeast(0)
    }
}
