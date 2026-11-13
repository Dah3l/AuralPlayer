// Despertador con música: configuración, programación con AlarmManager
// y receptores (alarma + arranque). La alarma suena aunque la app esté
// cerrada: el receptor enciende el servicio de música con la canción
// o playlist elegida.
package com.aural.player.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aural.player.data.SettingsStore
import com.aural.player.playback.MusicService
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Configuración del despertador (una sola alarma). */
data class AlarmConfig(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val repeatDaily: Boolean,
    val mode: String, // "song" o "playlist"
    val songIds: List<Long>,
    val playlistId: String?,
    val label: String
) {
    fun toJson(): String = JSONObject().apply {
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("repeatDaily", repeatDaily)
        put("mode", mode)
        put("songIds", JSONArray(songIds))
        put("playlistId", playlistId ?: JSONObject.NULL)
        put("label", label)
    }.toString()

    companion object {
        fun fromJson(json: String?): AlarmConfig? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val ids = mutableListOf<Long>()
                val arr = obj.optJSONArray("songIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) ids.add(arr.optLong(i))
                }
                AlarmConfig(
                    hour = obj.optInt("hour", 7),
                    minute = obj.optInt("minute", 0),
                    enabled = obj.optBoolean("enabled", false),
                    repeatDaily = obj.optBoolean("repeatDaily", false),
                    mode = obj.optString("mode", "song"),
                    songIds = ids,
                    playlistId = obj.optString("playlistId").takeIf { it.isNotBlank() },
                    label = obj.optString("label", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Programa y cancela la alarma en el sistema. */
object AlarmScheduler {
    const val ACTION_ALARM = "com.aural.player.ALARM_RING"
    private const val REQUEST_CODE = 1701

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Próxima ocurrencia de la hora elegida (hoy si falta, si no mañana). */
    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** ¿Puede la app programar alarmas exactas? (Android 12+ lo regula). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, config: AlarmConfig) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextTriggerMillis(config.hour, config.minute)
        val pi = pendingIntent(context)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            // Sin el permiso de alarmas exactas: suena en cuanto el
            // sistema lo permite (puede llegar unos minutos tarde).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}

/** Suena la alarma: enciende la música y reprograma si es diaria. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsStore(context)
                val config = store.alarm.first()
                if (config != null && config.enabled) {
                    // Si es una playlist, se resuelve fresca (por si
                    // cambió desde que se configuró); si no, la foto
                    // guardada.
                    val ids = if (config.mode == "playlist" && config.playlistId != null) {
                        val fresh = store.playlists.first()
                            .firstOrNull { it.id == config.playlistId }?.songIds
                        fresh?.takeIf { it.isNotEmpty() } ?: config.songIds
                    } else {
                        config.songIds
                    }
                    if (ids.isNotEmpty()) {
                        val svc = Intent(context, MusicService::class.java).apply {
                            action = MusicService.ACTION_ALARM_PLAY
                            putExtra(MusicService.EXTRA_ALARM_SONG_IDS, ids.toLongArray())
                        }
                        context.startForegroundService(svc)
                    }
                    if (config.repeatDaily) {
                        AlarmScheduler.schedule(context, config)
                    } else {
                        store.setAlarm(config.copy(enabled = false))
                    }
                }
            } catch (e: Exception) {
                // La alarma nunca debe crashear
            } finally {
                pending.finish()
            }
        }
    }
}

/** Tras reiniciar el teléfono, vuelve a programar la alarma si sigue activa. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = SettingsStore(context).alarm.first()
                if (config != null && config.enabled) {
                    AlarmScheduler.schedule(context, config)
                }
            } catch (e: Exception) {
                // Silencio: sin alarma no hay nada que reprogramar
            } finally {
                pending.finish()
            }
        }
    }
}
