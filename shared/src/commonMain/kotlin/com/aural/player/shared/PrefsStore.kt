// Preferencias de escritorio: tema, volumen y silencio guardados en
// un JSON pequeño (prefs.json) en la carpeta de datos de la app.
// Solo lo usa la versión de PC; Android sigue con su DataStore.
package com.aural.player.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class DesktopPrefs(
    val themeName: String,
    val volume: Int,
    val muted: Boolean
)

class PrefsStore(private val directory: String) {

    private val json = Json {}

    fun load(): DesktopPrefs? {
        val text = LocalFiles.readText("$directory/prefs.json") ?: return null
        return try {
            val obj = json.parseToJsonElement(text) as? JsonObject ?: return null
            DesktopPrefs(
                themeName = (obj["theme"] as? JsonPrimitive)?.contentOrNull ?: return null,
                volume = (obj["volume"] as? JsonPrimitive)?.intOrNull ?: 100,
                muted = (obj["muted"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(prefs: DesktopPrefs) {
        try {
            LocalFiles.mkdirs(directory)
            val obj = buildJsonObject {
                put("theme", prefs.themeName)
                put("volume", prefs.volume)
                put("muted", prefs.muted)
            }
            LocalFiles.writeText(
                "$directory/prefs.json",
                json.encodeToString(JsonObject.serializer(), obj)
            )
        } catch (e: Exception) {
            // Guardar preferencias nunca debe romper la app
        }
    }
}
