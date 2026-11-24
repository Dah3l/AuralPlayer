// Registro de escucha para "Tu Top 10": segundos escuchados
// por canción y día, guardados en un JSON en filesDir. Agregado por día
// (sin historial infinito) y podado a 30 días. Nunca lanza: si algo
// falla, el registro se pierde en silencio, jamás rompe la reproducción.
package com.aural.player.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

/** Un artista entre los más escuchados (para "Tus artistas favoritos" del Inicio). */
data class TopArtist(
    val name: String,
    /** Segundos escuchados en total (ventana: últimos 30 días). */
    val seconds: Long,
    /** Canciones distintas de ese artista que sonaron. */
    val songCount: Int,
    /** La canción más escuchada de ese artista (para enseñar su portada). */
    val topSongId: Long? = null
)

/** Una canción escuchada hace poco (para "Escuchadas recientemente" del Inicio). */
data class RecentSong(
    val songId: Long,
    val title: String,
    val artist: String
)

/** Resumen de los últimos 7 días (incluido hoy). */
data class WeekStats(
    val totalSeconds: Long = 0L,
    val distinctSongs: Int = 0,
    val topSongs: List<WeekSongStat> = emptyList()
)

class PlayHistoryStore(private val context: Context) {

    private val mutex = Mutex()

    private fun file(): File = File(context.filesDir, "play_history.json")

    private fun dayKey(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private fun lastDayKeys(count: Int): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        return (0 until count).map {
            val key = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -1)
            key
        }
    }

    private fun readRoot(): JSONObject {
        val f = file()
        if (!f.exists()) return JSONObject()
        return try {
            JSONObject(f.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Borra los días con más de 30 días de antigüedad. */
    private fun prune(root: JSONObject) {
        val days = root.optJSONObject("days") ?: return
        val keep = lastDayKeys(30).toSet()
        val keys = mutableListOf<String>()
        days.keys().forEach { keys.add(it) }
        keys.filter { it !in keep }.forEach { days.remove(it) }
    }

    /** Lee el JSON crudo del historial (para incluirlo en el respaldo). "" si no existe. */
    suspend fun readRawJson(): String {
        return try {
            mutex.withLock {
                val f = file()
                if (f.exists()) f.readText() else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Escribe el JSON crudo del historial (al importar un respaldo remapeado). */
    suspend fun writeRawJson(json: String) {
        try {
            mutex.withLock { file().writeText(json) }
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
                val days = root.optJSONObject("days")
                    ?: JSONObject().also { root.put("days", it) }
                val today = dayKey(Date())
                val day = days.optJSONObject(today)
                    ?: JSONObject().also { days.put(today, it) }
                day.put("seconds", day.optLong("seconds") + seconds)
                val songs = day.optJSONObject("songs")
                    ?: JSONObject().also { day.put("songs", it) }
                val key = songId.toString()
                val entry = songs.optJSONObject(key)
                    ?: JSONObject().also { songs.put(key, it) }
                entry.put("seconds", entry.optLong("seconds") + seconds)
                entry.put("title", title)
                entry.put("artist", artist)
                entry.put("lastPlayedAt", System.currentTimeMillis())
                prune(root)
                file().writeText(root.toString())
            }
        } catch (e: Exception) {
            // El registro nunca debe romper la reproducción
        }
    }

    /** Las canciones más escuchadas de todo el historial guardado (máx. 30 días). */
    suspend fun getTopSongs(limit: Int = 10): List<TopSong> {
        // Lectura + parseo en IO: antes corría en el hilo principal al
        // abrir "Tu Top 10".
        return withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val root = readRoot()
                    val days = root.optJSONObject("days")
                        ?: return@withContext emptyList()
                val agg = mutableMapOf<Long, TopSong>()
                days.keys().forEach { dayKey ->
                    val day = days.optJSONObject(dayKey) ?: return@forEach
                    val songs = day.optJSONObject("songs") ?: return@forEach
                    songs.keys().forEach { idStr ->
                        val id = idStr.toLongOrNull() ?: return@forEach
                        val e = songs.optJSONObject(idStr) ?: return@forEach
                        val prev = agg[id]
                        agg[id] = TopSong(
                            songId = id,
                            title = e.optString("title"),
                            artist = e.optString("artist"),
                            seconds = (prev?.seconds ?: 0L) + e.optLong("seconds")
                        )
                    }
                }
                agg.values.sortedByDescending { it.seconds }.take(limit)
            }
        } catch (e: Exception) {
            emptyList()
        }
        }
    }

    /** Los artistas más escuchados: mismo historial que el Top 10, agrupado por artista. */
    suspend fun getTopArtists(limit: Int = 5): List<TopArtist> {
        return withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val root = readRoot()
                    val days = root.optJSONObject("days")
                        ?: return@withContext emptyList()
                    val secondsByArtist = mutableMapOf<String, Long>()
                    val songsByArtist = mutableMapOf<String, MutableSet<Long>>()
                    val secondsBySong = mutableMapOf<Long, Long>()
                    days.keys().forEach { dayKey ->
                        val day = days.optJSONObject(dayKey) ?: return@forEach
                        val songs = day.optJSONObject("songs") ?: return@forEach
                        songs.keys().forEach { idStr ->
                            val id = idStr.toLongOrNull() ?: return@forEach
                            val e = songs.optJSONObject(idStr) ?: return@forEach
                            // Sin artista real no hay artista favorito.
                            val artist = e.optString("artist")
                            if (artist.isBlank() || artist == "Artista misterioso") return@forEach
                            val secs = e.optLong("seconds")
                            secondsByArtist[artist] =
                                (secondsByArtist[artist] ?: 0L) + secs
                            secondsBySong[id] = (secondsBySong[id] ?: 0L) + secs
                            songsByArtist.getOrPut(artist) { mutableSetOf() }.add(id)
                        }
                    }
                    secondsByArtist.entries
                        .sortedByDescending { it.value }
                        .take(limit)
                        .map { (name, seconds) ->
                            TopArtist(
                                name = name,
                                seconds = seconds,
                                songCount = songsByArtist[name]?.size ?: 0,
                                topSongId = songsByArtist[name]
                                    ?.maxByOrNull { secondsBySong[it] ?: 0L }
                            )
                        }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Las últimas canciones que sonaron, en orden de recencia. Cada
     * canción guarda su última reproducción (lastPlayedAt); las que aún
     * no lo tienen (historial viejo) quedan al final.
     */
    suspend fun getRecentSongs(limit: Int = 10): List<RecentSong> {
        return withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val root = readRoot()
                    val days = root.optJSONObject("days")
                        ?: return@withContext emptyList()
                    val latest = mutableMapOf<Long, RecentSong>()
                    val playedAt = mutableMapOf<Long, Long>()
                    days.keys().forEach { dayKey ->
                        val day = days.optJSONObject(dayKey) ?: return@forEach
                        val songs = day.optJSONObject("songs") ?: return@forEach
                        songs.keys().forEach { idStr ->
                            val id = idStr.toLongOrNull() ?: return@forEach
                            val e = songs.optJSONObject(idStr) ?: return@forEach
                            val at = e.optLong("lastPlayedAt", 0L)
                            if (at > (playedAt[id] ?: 0L)) {
                                playedAt[id] = at
                                latest[id] = RecentSong(
                                    songId = id,
                                    title = e.optString("title"),
                                    artist = e.optString("artist")
                                )
                            }
                        }
                    }
                    latest.values
                        .sortedByDescending { playedAt[it.songId] ?: 0L }
                        .take(limit)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Estadísticas de los últimos 7 días (incluido hoy). */
    suspend fun getWeekStats(): WeekStats {
        return withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val root = readRoot()
                    val days = root.optJSONObject("days")
                        ?: return@withContext WeekStats()
                var total = 0L
                val agg = mutableMapOf<Long, WeekSongStat>()
                for (key in lastDayKeys(7)) {
                    val day = days.optJSONObject(key) ?: continue
                    total += day.optLong("seconds")
                    val songs = day.optJSONObject("songs") ?: continue
                    songs.keys().forEach { idStr ->
                        val id = idStr.toLongOrNull() ?: return@forEach
                        val e = songs.optJSONObject(idStr) ?: return@forEach
                        val prev = agg[id]
                        agg[id] = WeekSongStat(
                            songId = id,
                            title = e.optString("title"),
                            artist = e.optString("artist"),
                            seconds = (prev?.seconds ?: 0L) + e.optLong("seconds")
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
}
