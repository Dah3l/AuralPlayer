// Registro de escucha para "Tu Top 10": segundos escuchados por
// canción y día, guardados en un JSON en la carpeta de datos de la
// plataforma. Agregado por día (sin historial infinito) y podado a
// 30 días. Nunca lanza: si algo falla, el registro se pierde en
// silencio, jamás rompe la reproducción.
// El formato del archivo es IDÉNTICO al de Android, así que un
// historial guardado por la app se lee igual aquí y viceversa.
package com.aural.player.shared

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Una canción dentro del resumen semanal, ordenada por tiempo escuchado. */
data class WeekSongStat(
    val songId: Long,
    val title: String,
    val artist: String,
    val seconds: Long
)

/** Una canción del Top 10, ordenada por tiempo total escuchado. */
data class TopSong(
    val songId: Long,
    val title: String,
    val artist: String,
    val seconds: Long
)

/** Resumen de los últimos 7 días (incluido hoy). */
data class WeekStats(
    val totalSeconds: Long = 0L,
    val distinctSongs: Int = 0,
    val topSongs: List<WeekSongStat> = emptyList()
)

class PlayHistoryStore(private val directory: String) {

    private val mutex = Mutex()
    private val json = Json {}

    private fun filePath(): String = "$directory/play_history.json"

    private fun lastDayKeys(count: Int): List<String> =
        (0 until count).map { SystemDates.keyDaysAgo(it) }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun readRoot(): JsonObject {
        val text = LocalFiles.readText(filePath()) ?: return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
    }

    private fun writeRoot(root: JsonObject) {
        LocalFiles.mkdirs(directory)
        LocalFiles.writeText(filePath(), json.encodeToString(JsonObject.serializer(), root))
    }

    /** Borra los días con más de 30 días de antigüedad. */
    private fun prune(root: JsonObject): JsonObject {
        val days = root.obj("days") ?: return root
        val keep = lastDayKeys(30).toSet()
        val pruned = JsonObject(days.filterKeys { it in keep })
        return JsonObject(root + ("days" to pruned))
    }

    /** Lee el JSON crudo del historial (para incluirlo en el respaldo). "" si no existe. */
    suspend fun readRawJson(): String {
        return try {
            mutex.withLock { LocalFiles.readText(filePath()) ?: "" }
        } catch (e: Exception) {
            ""
        }
    }

    /** Escribe el JSON crudo del historial (al importar un respaldo remapeado). */
    suspend fun writeRawJson(raw: String) {
        try {
            mutex.withLock {
                LocalFiles.mkdirs(directory)
                LocalFiles.writeText(filePath(), raw)
            }
        } catch (e: Exception) {
            // El historial nunca debe romper la importación
        }
    }

    /** Suma segundos escuchados de una canción al día actual. */
    suspend fun record(songId: Long, title: String, artist: String, seconds: Long) {
        if (seconds <= 0) return
        try {
            mutex.withLock {
                val root = readRoot()
                val days = root.obj("days") ?: JsonObject(emptyMap())
                val today = SystemDates.keyDaysAgo(0)
                val day = days.obj(today) ?: JsonObject(emptyMap())
                val songs = day.obj("songs") ?: JsonObject(emptyMap())
                val key = songId.toString()
                val entry = songs.obj(key) ?: JsonObject(emptyMap())
                val newEntry = buildJsonObject {
                    put("seconds", entry.long("seconds") + seconds)
                    put("title", title)
                    put("artist", artist)
                }
                val newSongs = JsonObject(songs + (key to newEntry))
                val newDay = JsonObject(
                    day + ("seconds" to JsonPrimitive(day.long("seconds") + seconds)) +
                            ("songs" to newSongs)
                )
                val newRoot = JsonObject(root + ("days" to JsonObject(days + (today to newDay))))
                writeRoot(prune(newRoot))
            }
        } catch (e: Exception) {
            // El registro nunca debe romper la reproducción
        }
    }

    /** Las canciones más escuchadas de todo el historial guardado (máx. 30 días). */
    suspend fun getTopSongs(limit: Int = 10): List<TopSong> {
        return try {
            mutex.withLock {
                val root = readRoot()
                val days = root.obj("days") ?: return emptyList()
                val agg = mutableMapOf<Long, TopSong>()
                for ((_, day) in days) {
                    val dayObj = day as? JsonObject ?: continue
                    val songs = dayObj.obj("songs") ?: continue
                    for ((idStr, element) in songs) {
                        val id = idStr.toLongOrNull() ?: continue
                        val e = element as? JsonObject ?: continue
                        val prev = agg[id]
                        agg[id] = TopSong(
                            songId = id,
                            title = e.str("title"),
                            artist = e.str("artist"),
                            seconds = (prev?.seconds ?: 0L) + e.long("seconds")
                        )
                    }
                }
                agg.values.sortedByDescending { it.seconds }.take(limit)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Estadísticas de los últimos 7 días (incluido hoy). */
    suspend fun getWeekStats(): WeekStats {
        return try {
            mutex.withLock {
                val root = readRoot()
                val days = root.obj("days") ?: return WeekStats()
                var total = 0L
                val agg = mutableMapOf<Long, WeekSongStat>()
                for (key in lastDayKeys(7)) {
                    val day = days.obj(key) ?: continue
                    total += day.long("seconds")
                    val songs = day.obj("songs") ?: continue
                    for ((idStr, element) in songs) {
                        val id = idStr.toLongOrNull() ?: continue
                        val e = element as? JsonObject ?: continue
                        val prev = agg[id]
                        agg[id] = WeekSongStat(
                            songId = id,
                            title = e.str("title"),
                            artist = e.str("artist"),
                            seconds = (prev?.seconds ?: 0L) + e.long("seconds")
                        )
                    }
                }
                WeekStats(
                    totalSeconds = total,
                    distinctSongs = agg.size,
                    topSongs = agg.values.sortedByDescending { it.seconds }.take(10)
                )
            }
        } catch (e: Exception) {
            WeekStats()
        }
    }
}
