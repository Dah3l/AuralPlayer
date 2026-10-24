// Persistencia de playlists y canciones favoritas con DataStore Preferences
package com.aural.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** DataStore único para toda la aplicación (archivo "aural_prefs"). */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aural_prefs")

/**
 * Almacén de playlists y favoritos.
 * Se guarda como JSON en una clave de preferencias, ya que DataStore
 * Preferences solo admite tipos primitivos.
 */
class SettingsStore(private val context: Context) {

    private companion object {
        val KEY_PLAYLISTS = stringPreferencesKey("playlists_json")
        val KEY_FAVORITES = stringPreferencesKey("favorites_json")
        val KEY_LRC_MAP = stringPreferencesKey("lrc_map_json")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_CUSTOM_COVERS = stringPreferencesKey("custom_covers_json")
        val KEY_FAVORITE_ORDER = stringPreferencesKey("favorite_order_json")
        val KEY_PLAYLIST_COVERS = stringPreferencesKey("playlist_covers_json")
        val KEY_PLAYLIST_PLAY_COUNTS = stringPreferencesKey("playlist_play_counts_json")
        val KEY_CUSTOM_METADATA = stringPreferencesKey("custom_metadata_json")
        val KEY_DISMISSED_UPDATE = stringPreferencesKey("dismissed_update_version")
        val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
    }

    /** Flujo reactivo de playlists del usuario. */
    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        decodePlaylists(prefs[KEY_PLAYLISTS])
    }

    /** Flujo reactivo del conjunto de canciones favoritas. */
    val favorites: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        decodeFavorites(prefs[KEY_FAVORITES])
    }

    /**
     * Orden de favoritos elegido por el usuario (drag en la lista).
     * La pertenencia sigue viviendo en el conjunto de favoritos: los
     * favoritos que aún no están en esta lista van al final, en orden
     * de biblioteca, así ningún favorito se pierde al migrar.
     */
    val favoriteOrder: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        decodeFavoriteOrder(prefs[KEY_FAVORITE_ORDER])
    }

    /** Fotos de playlists creadas: playlistId -> URI de la foto. */
    val playlistCovers: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        decodePlaylistCovers(prefs[KEY_PLAYLIST_COVERS])
    }

    suspend fun setPlaylistCover(playlistId: String, coverUri: String) {
        context.dataStore.edit { prefs ->
            val current = decodePlaylistCovers(prefs[KEY_PLAYLIST_COVERS]).toMutableMap()
            if (coverUri.isBlank()) {
                current.remove(playlistId)
            } else {
                current[playlistId] = coverUri
            }
            prefs[KEY_PLAYLIST_COVERS] = encodePlaylistCovers(current)
        }
    }

    /** Reproducciones por playlist: playlistId -> veces que se puso a sonar. */
    val playlistPlayCounts: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        decodePlaylistPlayCounts(prefs[KEY_PLAYLIST_PLAY_COUNTS])
    }

    /** Suma una reproducción a la playlist indicada. */
    suspend fun incrementPlaylistPlayCount(playlistId: String) {
        context.dataStore.edit { prefs ->
            val current = decodePlaylistPlayCounts(prefs[KEY_PLAYLIST_PLAY_COUNTS]).toMutableMap()
            current[playlistId] = (current[playlistId] ?: 0L) + 1L
            prefs[KEY_PLAYLIST_PLAY_COUNTS] = encodePlaylistPlayCounts(current)
        }
    }

    // ------------------------- Playlists -------------------------

    /** Crea una playlist vacía con el nombre indicado. */
    /** Tema seleccionado de la app (BOSQUE por defecto). */
    val appTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "BOSQUE"
    }

    suspend fun setAppTheme(themeName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = themeName
        }
    }

    /** Portadas personalizadas: songId -> URL de portada. */
    /** Última versión descartada con "Más tarde" (no se vuelve a avisar). */
    val dismissedUpdateVersion: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISMISSED_UPDATE]
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISMISSED_UPDATE] = version
        }
    }

    /** Momento del último chequeo automático (milisegundos). */
    val lastUpdateCheckMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK] ?: 0L
    }

    suspend fun setLastUpdateCheckMs(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK] = millis
        }
    }

    val customCovers: Flow<Map<Long, String>> = context.dataStore.data.map { prefs ->
        decodeCovers(prefs[KEY_CUSTOM_COVERS])
    }

    suspend fun setCustomCover(songId: Long, coverUrl: String) {
        context.dataStore.edit { prefs ->
            val current = decodeCovers(prefs[KEY_CUSTOM_COVERS]).toMutableMap()
            if (coverUrl.isBlank()) {
                current.remove(songId)
            } else {
                current[songId] = coverUrl
            }
            prefs[KEY_CUSTOM_COVERS] = encodeCovers(current)
        }
    }

    /** Metadatos personalizados: songId -> "título|artista|álbum". */
    val customMetadata: Flow<Map<Long, Triple<String, String, String>>> =
        context.dataStore.data.map { prefs ->
            decodeMetadata(prefs[KEY_CUSTOM_METADATA])
        }

    suspend fun setCustomMetadata(songId: Long, title: String, artist: String, album: String) {
        context.dataStore.edit { prefs ->
            val current = decodeMetadata(prefs[KEY_CUSTOM_METADATA]).toMutableMap()
            if (title.isBlank() && artist.isBlank() && album.isBlank()) {
                current.remove(songId)
            } else {
                current[songId] = Triple(title, artist, album)
            }
            prefs[KEY_CUSTOM_METADATA] = encodeMetadata(current)
        }
    }

    private fun decodeMetadata(json: String?): Map<Long, Triple<String, String, String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<Long, Triple<String, String, String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val parts = obj.optString(k).split("|", limit = 3)
                if (parts.size == 3) {
                    map[id] = Triple(parts[0], parts[1], parts[2])
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun encodeMetadata(map: Map<Long, Triple<String, String, String>>): String {
        val obj = org.json.JSONObject()
        for ((k, v) in map) {
            obj.put(k.toString(), "${v.first}|${v.second}|${v.third}")
        }
        return obj.toString()
    }

    private fun decodeCovers(json: String?): Map<Long, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<Long, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k.toLongOrNull() ?: continue] = obj.optString(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun decodeFavoriteOrder(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodePlaylistCovers(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun encodePlaylistCovers(map: Map<String, String>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodePlaylistPlayCounts(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optLong(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun encodePlaylistPlayCounts(map: Map<String, Long>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun encodeCovers(map: Map<Long, String>): String {
        val obj = org.json.JSONObject()
        for ((k, v) in map) obj.put(k.toString(), v)
        return obj.toString()
    }

    suspend fun createPlaylist(name: String): String {
        val id = "pl_${System.currentTimeMillis()}"
        mutatePlaylists { list ->
            if (list.none { it.name.equals(name, ignoreCase = true) }) {
                list + Playlist(id, name.trim(), emptyList())
            } else list
        }
        return id
    }

    /** Renombra una playlist existente. */
    suspend fun renamePlaylist(id: String, newName: String) = mutatePlaylists { list ->
        list.map { if (it.id == id) it.copy(name = newName.trim()) else it }
    }

    /** Elimina una playlist completa. */
    suspend fun deletePlaylist(id: String) = mutatePlaylists { list ->
        list.filterNot { it.id == id }
    }

    /** Añade una canción al final de la playlist (sin duplicarla). */
    suspend fun addToPlaylist(playlistId: String, songId: Long) = mutatePlaylists { list ->
        list.map { pl ->
            if (pl.id == playlistId && pl.songIds.none { it == songId }) {
                pl.copy(songIds = pl.songIds + songId)
            } else pl
        }
    }

    /** Quita una canción de la playlist. */
    suspend fun removeFromPlaylist(playlistId: String, songId: Long) = mutatePlaylists { list ->
        list.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(songIds = pl.songIds.filterNot { it == songId })
            } else pl
        }
    }

    /** Mueve una canción dentro de la playlist (reordenar). */
    suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) = mutatePlaylists { list ->
        list.map { pl ->
            if (pl.id == playlistId) {
                val ids = pl.songIds.toMutableList()
                if (fromIndex in ids.indices && toIndex in ids.indices && fromIndex != toIndex) {
                    val songId = ids.removeAt(fromIndex)
                    ids.add(toIndex, songId)
                }
                pl.copy(songIds = ids)
            } else pl
        }
    }

    /** Sustituye el orden completo de una playlist (al soltar tras reordenar en local). */
    suspend fun setPlaylistSongs(playlistId: String, songIds: List<Long>) = mutatePlaylists { list ->
        list.map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = songIds) else pl
        }
    }

    private suspend fun mutatePlaylists(block: (List<Playlist>) -> List<Playlist>) {
        context.dataStore.edit { prefs ->
            val current = decodePlaylists(prefs[KEY_PLAYLISTS])
            prefs[KEY_PLAYLISTS] = encodePlaylists(block(current))
        }
    }

    // ------------------------- Favoritos -------------------------

    /** Alterna el estado de favorito de una canción. */
    suspend fun toggleFavorite(songId: Long) {
        context.dataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES]).toMutableSet()
            val order = decodeFavoriteOrder(prefs[KEY_FAVORITE_ORDER]).toMutableList()
            if (!current.add(songId)) {
                current.remove(songId)
                order.remove(songId)
            } else if (songId !in order && order.isNotEmpty()) {
                // Solo se alimenta el orden una vez el usuario ha
                // reordenado alguna vez; si no, manda el orden de biblioteca
                order.add(songId)
            }
            prefs[KEY_FAVORITES] = JSONArray(current.toList()).toString()
            prefs[KEY_FAVORITE_ORDER] = JSONArray(order).toString()
        }
    }

    /** Guarda el orden completo de favoritos (la UI reordena en local y confirma al soltar). */
    suspend fun setFavoriteOrder(songIds: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FAVORITE_ORDER] = JSONArray(songIds).toString()
        }
    }

    // ------------------------- Codificación JSON -------------------------

    private fun encodePlaylists(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { pl ->
            arr.put(
                JSONObject()
                    .put("id", pl.id)
                    .put("name", pl.name)
                    .put("songs", JSONArray(pl.songIds))
            )
        }
        return arr.toString()
    }

    private fun decodePlaylists(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val ids = obj.getJSONArray("songs")
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeFavorites(json: String?): Set<Long> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // ------------------------- Letras .lrc -------------------------

    /** Flujo reactivo del mapa canción->archivo .lrc. */
    val lrcMap: Flow<Map<Long, String>> = context.dataStore.data.map { prefs ->
        decodeLrcMap(prefs[KEY_LRC_MAP])
    }

    /** Asocia un archivo .lrc a una canción. */
    suspend fun setLrcForSong(songId: Long, lrcPath: String) {
        context.dataStore.edit { prefs ->
            val map = decodeLrcMap(prefs[KEY_LRC_MAP]).toMutableMap()
            map[songId] = lrcPath
            prefs[KEY_LRC_MAP] = JSONObject(map.mapKeys { it.key.toString() } as Map<String, String>).toString()
        }
    }

    /** Quita la asociación de .lrc de una canción. */
    suspend fun removeLrcForSong(songId: Long) {
        context.dataStore.edit { prefs ->
            val map = decodeLrcMap(prefs[KEY_LRC_MAP]).toMutableMap()
            map.remove(songId)
            prefs[KEY_LRC_MAP] = JSONObject(map.mapKeys { it.key.toString() } as Map<String, String>).toString()
        }
    }

    private fun decodeLrcMap(json: String?): Map<Long, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k ->
                    k.toLongOrNull()?.let { put(it, obj.getString(k)) }
                }
            }
        } catch (e: Exception) { emptyMap() }
    }
}
