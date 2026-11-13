// Persistencia de playlists y canciones favoritas con DataStore Preferences
package com.aural.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aural.player.alarm.AlarmConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** DataStore único para toda la aplicación (archivo "aural_prefs"). */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aural_prefs")

/** Orden por defecto de las pestañas de la biblioteca (claves estables). */
val defaultTabOrder = listOf("songs", "artists", "albums", "playlists", "downloads", "folders")

/** Etiqueta visible de cada clave de pestaña. */
fun tabLabel(key: String): String = when (key) {
    "songs" -> "Canciones"
    "artists" -> "Artistas"
    "albums" -> "Álbumes"
    "playlists" -> "Playlists"
    "downloads" -> "Descargas"
    "folders" -> "Carpetas"
    else -> key
}

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
        val KEY_LAST_PLAYBACK = stringPreferencesKey("last_playback_json")
        val KEY_DAILY_PLAYLIST = stringPreferencesKey("daily_playlist_json")
        val KEY_THEME_BEFORE_HALLOWEEN = stringPreferencesKey("theme_before_halloween")
        val KEY_HIDDEN_FOLDERS = stringPreferencesKey("hidden_folders_json")
        val KEY_THEME_BEFORE_CHRISTMAS = stringPreferencesKey("theme_before_christmas")
        val KEY_ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        val KEY_ISLAND_APPEARANCE = stringPreferencesKey("island_appearance")
        val KEY_COMET_ENABLED = booleanPreferencesKey("comet_enabled")
        val KEY_AURAL_LIGHT_COLOR = stringPreferencesKey("aural_light_color")
        // v1.0.8: orden de pestañas de la biblioteca + aura de la isla.
        val KEY_TAB_ORDER = stringPreferencesKey("library_tab_order")
        // v1.1.0: despertador (JSON de AlarmConfig).
        val KEY_ALARM = stringPreferencesKey("alarm_json")

        // Perfil: nombre y cumpleaños (solo día y mes, sin año).
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_BIRTHDAY = stringPreferencesKey("birthday_md")
        val KEY_PROFILE_ASKED = booleanPreferencesKey("profile_asked")
        val KEY_BIRTHDAY_SHOWN = stringPreferencesKey("birthday_shown_date")
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

    // ------------------------- Tema de temporada (auto-aplicado) -------------------------

    /**
     * Auto-aplicado del tema Halloween. Se llama una vez por arranque desde
     * MainActivity, antes de pintar:
     * - Dentro de la ventana (27-oct..1-nov): la PRIMERA vez activa
     *   HALLOWEEN, guardando el tema previo en theme_before_halloween y
     *   marcando el flag halloween_applied_<año>. Si el usuario luego elige
     *   otro tema en el picker, se respeta: no se re-aplica.
     * - Fuera de la ventana: si el tema activo es HALLOWEEN, restaura el
     *   tema previo (o Aural Classic si no hay).
     * (El tema se guarda como nombre del enum; se usa el literal para no
     * acoplar la capa de datos con ui.theme.)
     */
    suspend fun runSeasonalAutoTheme(isHalloweenWindow: Boolean, year: Int) {
        context.dataStore.edit { prefs ->
            val appliedKey = booleanPreferencesKey("halloween_applied_$year")
            val current = prefs[KEY_THEME] ?: "BOSQUE"
            if (isHalloweenWindow) {
                if (prefs[appliedKey] != true) {
                    if (current != "HALLOWEEN") {
                        prefs[KEY_THEME_BEFORE_HALLOWEEN] = current
                    }
                    prefs[KEY_THEME] = "HALLOWEEN"
                    prefs[appliedKey] = true
                }
            } else if (current == "HALLOWEEN") {
                prefs[KEY_THEME] = prefs[KEY_THEME_BEFORE_HALLOWEEN] ?: "BOSQUE"
                prefs.remove(KEY_THEME_BEFORE_HALLOWEEN)
            }
        }
    }

    // ------------------------- Tema de temporada: Navidad (v1.0.7) -------------------------

    /**
     * Auto-aplicado del tema Navidad. Mismo patrón que Halloween:
     * - Dentro de la ventana (20-dic..26-dic): la PRIMERA vez activa
     *   CHRISTMAS, guardando el tema previo en theme_before_christmas y
     *   marcando el flag christmas_applied_<año>. Si el usuario luego elige
     *   otro tema en el picker, se respeta: no se re-aplica.
     * - Fuera de la ventana: si el tema activo es CHRISTMAS, restaura el
     *   tema previo (o Aural Classic si no hay).
     */
    suspend fun runChristmasAutoTheme(isChristmasWindow: Boolean, year: Int) {
        context.dataStore.edit { prefs ->
            val appliedKey = booleanPreferencesKey("christmas_applied_$year")
            val current = prefs[KEY_THEME] ?: "BOSQUE"
            if (isChristmasWindow) {
                if (prefs[appliedKey] != true) {
                    if (current != "CHRISTMAS") {
                        prefs[KEY_THEME_BEFORE_CHRISTMAS] = current
                    }
                    prefs[KEY_THEME] = "CHRISTMAS"
                    prefs[appliedKey] = true
                }
            } else if (current == "CHRISTMAS") {
                prefs[KEY_THEME] = prefs[KEY_THEME_BEFORE_CHRISTMAS] ?: "BOSQUE"
                prefs.remove(KEY_THEME_BEFORE_CHRISTMAS)
            }
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

    // ------------------------- Última reproducción -------------------------

    /** Última canción que sonó + la cola que tenía en ese momento. */
    data class LastPlayback(val songId: Long, val queueIds: List<Long>)

    /** Flujo reactivo de la última reproducción guardada (null si no hay). */
    val lastPlayback: Flow<LastPlayback?> = context.dataStore.data.map { prefs ->
        decodeLastPlayback(prefs[KEY_LAST_PLAYBACK])
    }

    /** Guarda la última canción y su cola (se llama al cambiar de tema). */
    suspend fun setLastPlayback(songId: Long, queueIds: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PLAYBACK] = encodeLastPlayback(songId, queueIds)
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

    private fun decodeLastPlayback(json: String?): LastPlayback? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val songId = obj.optLong("song", -1L)
            if (songId < 0L) return null
            val arr = obj.optJSONArray("queue") ?: return null
            val ids = (0 until arr.length()).map { arr.getLong(it) }
            LastPlayback(songId, ids)
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeLastPlayback(songId: Long, queueIds: List<Long>): String {
        val obj = JSONObject()
        obj.put("song", songId)
        obj.put("queue", JSONArray(queueIds))
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

    /** Añade varias canciones al final de la playlist (sin duplicar). v1.0.7. */
    suspend fun addAllToPlaylist(playlistId: String, songIds: List<Long>) =
        mutatePlaylists { list ->
            list.map { pl ->
                if (pl.id == playlistId) {
                    val existing = pl.songIds.toSet()
                    pl.copy(songIds = pl.songIds + songIds.filterNot { it in existing })
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

    // ------------------------- Playlist del día -------------------------

    /**
     * UNA sola "Playlist del día": { fecha (yyyy-MM-dd, hora local del
     * dispositivo), ids de canciones }. Al abrirse la app en un día
     * distinto al guardado se REGENERA y la anterior desaparece (sin
     * historial, sin versiones acumuladas). Solo sobrevive si el usuario
     * la guardó como permanente (copia estática en playlists normales).
     */
    data class DailyPlaylist(val date: String, val songIds: List<Long>)

    /** Flujo reactivo de la Playlist del día (null si aún no se generó). */
    val dailyPlaylist: Flow<DailyPlaylist?> = context.dataStore.data.map { prefs ->
        decodeDailyPlaylist(prefs[KEY_DAILY_PLAYLIST])
    }

    /** Guarda (o regenera) la Playlist del día con la fecha indicada. */
    suspend fun setDailyPlaylist(date: String, songIds: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DAILY_PLAYLIST] = JSONObject()
                .put("date", date)
                .put("songs", JSONArray(songIds))
                .toString()
        }
    }

    private fun decodeDailyPlaylist(json: String?): DailyPlaylist? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val ids = obj.getJSONArray("songs")
            DailyPlaylist(
                date = obj.getString("date"),
                songIds = (0 until ids.length()).map { i -> ids.getLong(i) }
            )
        } catch (_: Exception) { null }
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

    /** Marca varias canciones como favoritas (sin quitar las que ya lo son). v1.0.7. */
    suspend fun addFavorites(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES]).toMutableSet()
            current.addAll(songIds)
            prefs[KEY_FAVORITES] = JSONArray(current.toList()).toString()
        }
    }

    /** Quita varias canciones de favoritos (con su orden). v1.0.9. */
    suspend fun removeFavorites(songIds: Collection<Long>) {
        if (songIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES]).toMutableSet()
            val order = decodeFavoriteOrder(prefs[KEY_FAVORITE_ORDER]).toMutableList()
            current.removeAll(songIds)
            order.removeAll(songIds)
            prefs[KEY_FAVORITES] = JSONArray(current.toList()).toString()
            prefs[KEY_FAVORITE_ORDER] = JSONArray(order).toString()
        }
    }

    /** Quita varias canciones de TODAS las playlists. v1.0.9. */
    suspend fun removeSongsFromAllPlaylists(songIds: Collection<Long>) =
        mutatePlaylists { list ->
            list.map { pl ->
                pl.copy(songIds = pl.songIds.filterNot { it in songIds })
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

    // ------------------------- Carpetas ocultas (v1.0.7) -------------------------

    /** Rutas de carpetas que el usuario ocultó de la biblioteca. */
    val hiddenFolders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        decodeStringSet(prefs[KEY_HIDDEN_FOLDERS])
    }

    /** Oculta o muestra una carpeta. */
    suspend fun toggleHiddenFolder(path: String) {
        context.dataStore.edit { prefs ->
            val current = decodeStringSet(prefs[KEY_HIDDEN_FOLDERS]).toMutableSet()
            if (!current.add(path)) current.remove(path)
            prefs[KEY_HIDDEN_FOLDERS] = JSONArray(current.toList()).toString()
        }
    }

    private fun decodeStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // ------------------------- Isla dinámica (v1.0.7) -------------------------

    /** Si la isla dinámica está activada. */
    val islandEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ISLAND_ENABLED] ?: false
    }

    suspend fun setIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ISLAND_ENABLED] = enabled
        }
    }

    /** Aspecto de Dynamic Aural: "auto" (según el tema) o "classic" (negra). */
    val islandAppearance: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ISLAND_APPEARANCE] ?: "auto"
    }

    suspend fun setIslandAppearance(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ISLAND_APPEARANCE] = mode
        }
    }

    /** Aural Light de Dynamic Aural: activado por defecto. */
    val cometEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_COMET_ENABLED] ?: true
    }

    suspend fun setCometEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COMET_ENABLED] = enabled
        }
    }

    /** Color de Aural Light: "auto" (primario del tema) o un hex "#RRGGBB" de otro tema. */
    val auralLightColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AURAL_LIGHT_COLOR] ?: "auto"
    }

    suspend fun setAuralLightColor(color: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AURAL_LIGHT_COLOR] = color
        }
    }

    // ------------------------- v1.0.8: orden de pestañas -------------------------

    /** Claves de pestañas de la biblioteca en el orden elegido por el usuario. */
    val libraryTabOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_TAB_ORDER] ?: return@map defaultTabOrder
        try {
            val arr = JSONArray(raw)
            val list = List(arr.length()) { arr.getString(it) }
            // Si falta o sobra alguna clave (p. ej. tras una actualización),
            // se normaliza contra el orden por defecto sin perder las válidas.
            val valid = list.filter { it in defaultTabOrder }
            (valid + defaultTabOrder.filter { it !in valid })
        } catch (e: Exception) {
            defaultTabOrder
        }
    }

    suspend fun setLibraryTabOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TAB_ORDER] = JSONArray(order).toString()
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

    // ----------------------------- Despertador -----------------------------

    /** Flujo reactivo de la configuración del despertador (null = sin alarma). */
    val alarm: Flow<AlarmConfig?> = context.dataStore.data.map { prefs ->
        AlarmConfig.fromJson(prefs[KEY_ALARM])
    }

    /** Guarda la configuración del despertador (null la borra). */
    suspend fun setAlarm(config: AlarmConfig?) {
        context.dataStore.edit { prefs ->
            if (config == null) prefs.remove(KEY_ALARM)
            else prefs[KEY_ALARM] = config.toJson()
        }
    }

    // ----------------------------- Perfil -----------------------------

    /** Nombre del usuario (null = no dado). Solo vive en este teléfono. */
    val userName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME]
    }

    /** Cumpleaños como "MM-dd" (sin año). Null = no dado. */
    val birthday: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIRTHDAY]
    }

    /** Si ya se mostró (o se descartó) la tarjeta de bienvenida del perfil. */
    val profileAsked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROFILE_ASKED] ?: false
    }

    /** Fecha "yyyy-MM-dd" en que se mostró el modal de cumpleaños por última vez. */
    val birthdayShownDate: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIRTHDAY_SHOWN]
    }

    /** Guarda nombre y cumpleaños (birthdayMd null = sin fecha). */
    suspend fun setUserProfile(name: String, birthdayMd: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name.trim()
            if (birthdayMd == null) prefs.remove(KEY_BIRTHDAY)
            else prefs[KEY_BIRTHDAY] = birthdayMd
            prefs[KEY_PROFILE_ASKED] = true
        }
    }

    /** Marca la tarjeta de bienvenida como vista (el usuario eligió "Ahora no"). */
    suspend fun setProfileAsked() {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROFILE_ASKED] = true
        }
    }

    /** Marca el día en que se mostró el modal de cumpleaños. */
    suspend fun setBirthdayShown(dateKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIRTHDAY_SHOWN] = dateKey
        }
    }

    /** Borra nombre y cumpleaños (los datos del perfil se van del teléfono). */
    suspend fun clearUserProfile() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_BIRTHDAY)
        }
    }

    // ----------------------------- Respaldo -----------------------------

    /**
     * Claves que entran en el respaldo: todo lo que el usuario perdería
     * al desinstalar (playlists, favoritos, metadatos, portadas, letras,
     * tema, isla, pestañas y despertador). Fuera quedan los datos
     * transitorios (última reproducción, playlist del día, avisos de
     * actualización y marcadores estacionales).
     */
    private val backupStringKeys = listOf(
        "playlists_json" to KEY_PLAYLISTS,
        "favorites_json" to KEY_FAVORITES,
        "lrc_map_json" to KEY_LRC_MAP,
        "app_theme" to KEY_THEME,
        "custom_covers_json" to KEY_CUSTOM_COVERS,
        "favorite_order_json" to KEY_FAVORITE_ORDER,
        "playlist_covers_json" to KEY_PLAYLIST_COVERS,
        "playlist_play_counts_json" to KEY_PLAYLIST_PLAY_COUNTS,
        "custom_metadata_json" to KEY_CUSTOM_METADATA,
        "hidden_folders_json" to KEY_HIDDEN_FOLDERS,
        "library_tab_order" to KEY_TAB_ORDER,
        "island_appearance" to KEY_ISLAND_APPEARANCE,
        "aural_light_color" to KEY_AURAL_LIGHT_COLOR,
        "alarm_json" to KEY_ALARM,
        "user_name" to KEY_USER_NAME,
        "birthday_md" to KEY_BIRTHDAY
    )

    private val backupBooleanKeys = listOf(
        "island_enabled" to KEY_ISLAND_ENABLED,
        "comet_enabled" to KEY_COMET_ENABLED,
        "profile_asked" to KEY_PROFILE_ASKED
    )

    /**
     * Exporta el respaldo como JSON: versión, fecha, valores de las
     * claves y el contenido de los archivos .lrc guardados por la app
     * (los de filesDir, que se pierden al desinstalar).
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val data = JSONObject()
        for ((name, key) in backupStringKeys) {
            prefs[key]?.let { data.put(name, it) }
        }
        for ((name, key) in backupBooleanKeys) {
            prefs[key]?.let { data.put(name, it) }
        }
        // Letras guardadas por la app: van dentro del JSON.
        val lyrics = JSONObject()
        val filesDirPath = context.filesDir.absolutePath
        decodeLrcMap(prefs[KEY_LRC_MAP]).forEach { (id, path) ->
            if (path.startsWith(filesDirPath)) {
                try {
                    val file = File(path)
                    if (file.exists()) lyrics.put(id.toString(), file.readText())
                } catch (e: Exception) {
                    // Esa letra no entra en el respaldo
                }
            }
        }
        JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("prefs", data)
            put("lyrics", lyrics)
        }.toString()
    }

    /**
     * Importa un respaldo: reemplaza los datos actuales con los del JSON
     * y restaura los archivos .lrc (regenerando sus rutas en filesDir).
     * Devuelve false si el JSON no es un respaldo válido.
     */
    suspend fun importBackupJson(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)
            if (root.optInt("version", -1) != 1) return@withContext false
            val prefsJson = root.optJSONObject("prefs") ?: return@withContext false
            val lyricsJson = root.optJSONObject("lyrics")
            context.dataStore.edit { prefs ->
                for ((name, key) in backupStringKeys) {
                    if (prefsJson.has(name)) prefs[key] = prefsJson.getString(name)
                }
                for ((name, key) in backupBooleanKeys) {
                    if (prefsJson.has(name)) prefs[key] = prefsJson.getBoolean(name)
                }
            }
            // Letras: escribir los archivos y regenerar las rutas del mapa
            // (las rutas viejas de filesDir no valen en otra instalación;
            // las rutas fuera de filesDir se conservan tal cual).
            if (prefsJson.has("lrc_map_json")) {
                val filesDirPath = context.filesDir.absolutePath
                val importedMap = decodeLrcMap(prefsJson.getString("lrc_map_json"))
                val newMap = importedMap.mapValues { (id, path) ->
                    if (path.startsWith(filesDirPath)) {
                        val target = File(context.filesDir, path.substringAfterLast('/'))
                        val content = lyricsJson?.optString(id.toString())
                        if (!content.isNullOrEmpty()) {
                            try {
                                target.writeText(content)
                            } catch (e: Exception) {
                                // Se conserva la ruta aunque el archivo falle
                            }
                        }
                        target.absolutePath
                    } else {
                        path
                    }
                }
                context.dataStore.edit { prefs ->
                    prefs[KEY_LRC_MAP] = JSONObject(
                        newMap.mapKeys { it.key.toString() } as Map<String, String>
                    ).toString()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** Clave "yyyy-MM-dd" del día actual (para mostrar el modal una vez al día). */
fun todayDateKey(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

/** ¿Hoy es el cumpleaños guardado ("MM-dd")? El 29 de febrero se celebra el 28 en años no bisiestos. */
fun isBirthdayToday(monthDay: String): Boolean {
    val cal = java.util.Calendar.getInstance()
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val today = "%02d-%02d".format(month, day)
    if (monthDay == today) return true
    if (monthDay == "02-29" && today == "02-28") {
        val year = cal.get(java.util.Calendar.YEAR)
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        if (!leap) return true
    }
    return false
}

/**
 * Casos personalizados por combinación de nombre + fecha de cumpleaños.
 * Devuelve el nombre especial o null si no aplica. El saludo y el modal
 * de cumpleaños pueden usar nombres distintos para el mismo caso.
 */
private fun specialDisplayName(name: String, monthDay: String?, forBirthday: Boolean): String? {
    if (monthDay == null) return null
    val first = name.trim().split(" ").firstOrNull()
        ?.lowercase(java.util.Locale.ROOT) ?: return null
    return when {
        first in listOf("vanesa", "ness", "nessa") && monthDay == "11-13" ->
            if (forBirthday) "PurryPrincesa" else "Purry"
        first in listOf("frank", "sensato") && monthDay == "10-24" -> "Sensato🗿"
        else -> null
    }
}

/** Nombre que muestra el mensaje de cumpleaños. Solo la combinación
 * Vanesa/Ness/Nessa + 13 de noviembre usa "PurryPrincesa". */
fun birthdayDisplayName(name: String, monthDay: String): String {
    val clean = name.trim()
    return specialDisplayName(clean, monthDay, forBirthday = true) ?: clean
}

/** Nombre que muestra el saludo personalizado (primer nombre). Solo la
 * combinación Vanesa/Ness/Nessa + 13 de noviembre usa "Purry". */
fun greetingDisplayName(name: String?, monthDay: String?): String? {
    if (name.isNullOrBlank()) return null
    val clean = name.trim()
    specialDisplayName(clean, monthDay, forBirthday = false)?.let { return it }
    return clean.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
}
