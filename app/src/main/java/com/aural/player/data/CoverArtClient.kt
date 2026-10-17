// Cliente para buscar portadas en línea vía iTunes Search API (gratis, sin API key).
package com.aural.player.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Resultado de búsqueda de portada. */
data class CoverArtResult(
    val artworkUrl: String,      // URL en alta resolución
    val thumbnailUrl: String,    // URL miniatura
    val trackName: String,
    val artistName: String,
    val collectionName: String
)

object CoverArtClient {

    private const val TAG = "CoverArtClient"
    private const val TIMEOUT_MS = 15_000

    /**
     * Busca portadas en iTunes por artista y título.
     * Devuelve lista de resultados con URLs de artwork.
     */
    suspend fun search(artist: String, title: String): List<CoverArtResult> =
        withContext(Dispatchers.IO) {
            try {
                val query = "$artist $title".trim()
                if (query.isBlank()) return@withContext emptyList()

                val encoded = URLEncoder.encode(query, "UTF-8")
                val urlStr = "https://itunes.apple.com/search" +
                    "?term=$encoded&entity=song&limit=20&country=US"

                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", "AuralPlayer/1.0")
                }

                if (conn.responseCode != 200) {
                    Log.w(TAG, "iTunes HTTP ${conn.responseCode}")
                    return@withContext emptyList()
                }

                val json = conn.inputStream.bufferedReader().readText()
                parseResults(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error buscando portada: ${e.message}")
                emptyList()
            }
        }

    private fun parseResults(json: String): List<CoverArtResult> {
        val results = mutableListOf<CoverArtResult>()
        try {
            val root = JSONObject(json)
            val array = root.optJSONArray("results") ?: return results

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val artwork100 = item.optString("artworkUrl100")
                if (artwork100.isBlank()) continue

                // Mejorar resolución: 100x100 -> 600x600
                val artwork600 = artwork100.replace("100x100", "600x600")

                results.add(
                    CoverArtResult(
                        artworkUrl = artwork600,
                        thumbnailUrl = artwork100,
                        trackName = item.optString("trackName"),
                        artistName = item.optString("artistName"),
                        collectionName = item.optString("collectionName")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando: ${e.message}")
        }
        return results
    }
}
