// Clave estable de una canción para el respaldo v2 (v1.2.0): los IDs de
// MediaStore cambian de un equipo a otro, así que el respaldo casa por
// título + artista (+ duración y carpeta como desempate), usando los
// tags ORIGINALES del archivo, nunca los metadatos editados en la app.
package com.aural.player.data

import org.json.JSONObject
import java.util.Locale

data class SongKey(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folderPath: String = ""
) {
    fun normalizedTitle(): String = normalize(title)
    fun normalizedArtist(): String = normalize(artist)

    fun toJson(): JSONObject = JSONObject().apply {
        put("t", title)
        put("a", artist)
        put("d", durationMs)
        put("f", folderPath)
    }

    companion object {
        fun normalize(raw: String): String =
            raw.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

        fun fromSong(song: Song): SongKey = SongKey(
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
            folderPath = song.folderPath
        )

        fun fromJson(json: JSONObject?): SongKey? {
            if (json == null) return null
            return try {
                SongKey(
                    title = json.optString("t"),
                    artist = json.optString("a"),
                    durationMs = json.optLong("d"),
                    folderPath = json.optString("f")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Resuelve IDs viejos → IDs locales. Dos pasadas: exacta (título + artista
 * + duración) y luego título + artista. Si hay varios candidatos, gana el
 * de la misma carpeta; si no, el primero. Sin candidato, el ID queda fuera
 * (la entrada se descarta en silencio, sin error).
 */
fun resolveSongKeys(
    stored: Map<Long, SongKey>,
    localSongs: List<Song>
): Map<Long, Long> {
    if (stored.isEmpty() || localSongs.isEmpty()) return emptyMap()
    val byExact = mutableMapOf<String, MutableList<Song>>()
    val byName = mutableMapOf<String, MutableList<Song>>()
    for (song in localSongs) {
        val key = SongKey.fromSong(song)
        val namePart = "${key.normalizedTitle()}\u0001${key.normalizedArtist()}"
        byExact.getOrPut("$namePart\u0001${song.durationMs}") { mutableListOf() }.add(song)
        byName.getOrPut(namePart) { mutableListOf() }.add(song)
    }
    val result = mutableMapOf<Long, Long>()
    for ((oldId, storedKey) in stored) {
        val namePart = "${storedKey.normalizedTitle()}\u0001${storedKey.normalizedArtist()}"
        val candidates = byExact["$namePart\u0001${storedKey.durationMs}"]
            ?: byName[namePart]
            ?: continue
        val match = candidates.firstOrNull { it.folderPath == storedKey.folderPath }
            ?: candidates.first()
        result[oldId] = match.id
    }
    return result
}
