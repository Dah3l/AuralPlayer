// Registro de escucha para "Tu semana en Aural": segundos escuchados
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Una canción dentro del resumen semanal, ordenada por tiempo escuchado. */
data class WeekSongStat(
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
                prune(root)
                file().writeText(root.toString())
            }
        } catch (e: Exception) {
            // El registro nunca debe romper la reproducción
        }
    }

    /** Estadísticas de los últimos 7 días (incluido hoy). */
    suspend fun getWeekStats(): WeekStats {
        return try {
            mutex.withLock {
                val root = readRoot()
                val days = root.optJSONObject("days")
                    ?: return WeekStats()
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
