// Biblioteca de escritorio: carpetas escaneadas + playlists,
// guardadas en un JSON (library.json) en la carpeta de datos de
// la app. Solo lo usa la versión de PC.
package com.aural.player.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class LibraryData(
    val folders: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

class LibraryStore(private val directory: String) {

    private val json = Json {}

    fun load(): LibraryData? {
        val text = LocalFiles.readText("$directory/library.json") ?: return null
        return try {
            val obj = json.parseToJsonElement(text) as? JsonObject ?: return null
            val folders = (obj["folders"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { path -> path.isNotBlank() } }
                ?: emptyList()
            val playlists = (obj["playlists"] as? JsonArray)
                ?.mapNotNull { element ->
                    val p = element as? JsonObject ?: return@mapNotNull null
                    val id = (p["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val name = (p["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val paths = (p["paths"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        ?: emptyList()
                    Playlist(id, name, paths)
                }
                ?: emptyList()
            LibraryData(folders, playlists)
        } catch (e: Exception) {
            null
        }
    }

    fun save(data: LibraryData) {
        try {
            LocalFiles.mkdirs(directory)
            val obj = buildJsonObject {
                put("folders", buildJsonArray { data.folders.forEach { add(it) } })
                put(
                    "playlists",
                    buildJsonArray {
                        data.playlists.forEach { playlist ->
                            add(
                                buildJsonObject {
                                    put("id", playlist.id)
                                    put("name", playlist.name)
                                    put(
                                        "paths",
                                        buildJsonArray { playlist.songPaths.forEach { add(it) } }
                                    )
                                }
                            )
                        }
                    }
                )
            }
            LocalFiles.writeText(
                "$directory/library.json",
                json.encodeToString(JsonObject.serializer(), obj)
            )
        } catch (e: Exception) {
            // Guardar la biblioteca nunca debe romper la app
        }
    }
}
