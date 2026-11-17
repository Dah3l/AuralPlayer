// Clave estable de una canción para el respaldo v2: los IDs cambian
// de un equipo a otro, así que el respaldo casa por título + artista
// (+ duración y carpeta como desempate), usando los tags ORIGINALES
// del archivo, nunca los metadatos editados en la app.
// Formato JSON idéntico al de Android (claves t/a/d/f) para que los
// respaldos v2 existentes sigan importando igual.
package com.aural.player.shared

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class SongKey(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folderPath: String = ""
) {
    fun normalizedTitle(): String = normalize(title)
    fun normalizedArtist(): String = normalize(artist)

    fun toJson(): JsonObject = buildJsonObject {
        put("t", title)
        put("a", artist)
        put("d", durationMs)
        put("f", folderPath)
    }

    companion object {
        fun normalize(raw: String): String =
            raw.lowercase().trim().replace(Regex("\\s+"), " ")

        /** En PC la carpeta es el directorio padre de la ruta del archivo. */
        fun fromSharedSong(song: Song): SongKey = SongKey(
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
            folderPath = song.filePath.substringBeforeLast('/', "")
        )

        fun fromJson(json: JsonObject?): SongKey? {
            if (json == null) return null
            return try {
                SongKey(
                    title = (json["t"] as? JsonPrimitive)?.contentOrNull ?: "",
                    artist = (json["a"] as? JsonPrimitive)?.contentOrNull ?: "",
                    durationMs = (json["d"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    folderPath = (json["f"] as? JsonPrimitive)?.contentOrNull ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Resuelve IDs viejos → IDs locales. Dos pasadas: exacta (título +
 * artista + duración) y luego título + artista. Si hay varios
 * candidatos, gana el de la misma carpeta; si no, el primero. Sin
 * candidato, el ID queda fuera (la entrada se descarta en silencio,
 * sin error). Genérica: cada plataforma arma el mapa local con sus
 * propias canciones.
 */
fun resolveSongKeys(
    stored: Map<Long, SongKey>,
    localKeys: Map<Long, SongKey>
): Map<Long, Long> {
    if (stored.isEmpty() || localKeys.isEmpty()) return emptyMap()
    val byExact = mutableMapOf<String, MutableList<Pair<Long, SongKey>>>()
    val byName = mutableMapOf<String, MutableList<Pair<Long, SongKey>>>()
    for ((id, key) in localKeys) {
        val namePart = "${key.normalizedTitle()}\u0001${key.normalizedArtist()}"
        byExact.getOrPut("$namePart\u0001${key.durationMs}") { mutableListOf() }.add(id to key)
        byName.getOrPut(namePart) { mutableListOf() }.add(id to key)
    }
    val result = mutableMapOf<Long, Long>()
    for ((oldId, storedKey) in stored) {
        val namePart = "${storedKey.normalizedTitle()}\u0001${storedKey.normalizedArtist()}"
        val candidates = byExact["$namePart\u0001${storedKey.durationMs}"]
            ?: byName[namePart]
            ?: continue
        val match = candidates.firstOrNull { it.second.folderPath == storedKey.folderPath }
            ?: candidates.first()
        result[oldId] = match.first
    }
    return result
}
