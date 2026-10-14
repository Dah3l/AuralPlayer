// Persistencia de playlists y canciones favoritas con DataStore Preferences
package com.aural.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    /** Flujo reactivo de playlists del usuario. */
    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        decodePlaylists(prefs[KEY_PLAYLISTS])
    }

    /** Flujo reactivo del conjunto de canciones favoritas. */
    val favorites: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        decodeFavorites(prefs[KEY_FAVORITES])
    }

    // ------------------------- Playlists -------------------------

    /** Crea una playlist vacía con el nombre indicado. */
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
            if (!current.add(songId)) current.remove(songId)
            prefs[KEY_FAVORITES] = JSONArray(current.toList()).toString()
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
}
