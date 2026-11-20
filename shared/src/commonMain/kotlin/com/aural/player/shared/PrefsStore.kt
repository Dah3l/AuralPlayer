// Preferencias de escritorio guardadas en un JSON pequeño (prefs.json)
// en la carpeta de datos de la app. Solo lo usa la versión de PC;
// Android sigue con su DataStore. Toda clave nueva lleva su valor
// por defecto: un prefs.json viejo se lee igual.
package com.aural.player.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class DesktopPrefs(
    val themeName: String,
    val volume: Int,
    val muted: Boolean,
    val repeatModeName: String = RepeatMode.OFF.name,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val eqEnabled: Boolean = false,
    val eqPreamp: Float = 0f,
    val eqAmps: List<Float> = emptyList(),
    val islandEnabled: Boolean = true,
    val cometEnabled: Boolean = true,
    val islandAppearance: String = "auto"
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
                muted = (obj["muted"] as? JsonPrimitive)?.booleanOrNull ?: false,
                repeatModeName = (obj["repeatMode"] as? JsonPrimitive)?.contentOrNull
                    ?: RepeatMode.OFF.name,
                shuffleEnabled = (obj["shuffle"] as? JsonPrimitive)?.booleanOrNull ?: false,
                playbackSpeed = (obj["speed"] as? JsonPrimitive)?.floatOrNull ?: 1f,
                eqEnabled = (obj["eqEnabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                eqPreamp = (obj["eqPreamp"] as? JsonPrimitive)?.floatOrNull ?: 0f,
                eqAmps = (obj["eqAmps"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                    ?: emptyList(),
                islandEnabled = (obj["islandEnabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                cometEnabled = (obj["cometEnabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                islandAppearance = (obj["islandAppearance"] as? JsonPrimitive)?.contentOrNull
                    ?: "auto"
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
                put("repeatMode", prefs.repeatModeName)
                put("shuffle", prefs.shuffleEnabled)
                put("speed", prefs.playbackSpeed)
                put("eqEnabled", prefs.eqEnabled)
                put("eqPreamp", prefs.eqPreamp)
                put("eqAmps", buildJsonArray { prefs.eqAmps.forEach { add(JsonPrimitive(it)) } })
                put("islandEnabled", prefs.islandEnabled)
                put("cometEnabled", prefs.cometEnabled)
                put("islandAppearance", prefs.islandAppearance)
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
