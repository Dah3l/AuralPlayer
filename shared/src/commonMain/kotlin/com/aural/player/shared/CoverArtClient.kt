// Cliente para buscar portadas en línea vía iTunes Search API (gratis,
// sin API key). Puerto del de Android (CoverArtClient.kt): misma URL,
// mismo límite, mismo escalado 100→600. La red va por Ktor y el JSON
// por la API de árbol de kotlinx-serialization (igual que LrcLibClient).
package com.aural.player.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Resultado de búsqueda de portada. */
data class CoverArtResult(
    val artworkUrl: String,      // URL en alta resolución (600x600)
    val thumbnailUrl: String,    // URL miniatura (100x100)
    val trackName: String,
    val artistName: String,
    val collectionName: String
)

object CoverArtClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient by lazy { createHttpClient() }

    /**
     * Busca portadas en iTunes por artista y título.
     * Devuelve lista de resultados con URLs de artwork.
     */
    suspend fun search(artist: String, title: String): List<CoverArtResult> {
        return try {
            val query = "$artist $title".trim()
            if (query.isBlank()) return emptyList()
            val url = "https://itunes.apple.com/search" +
                    "?term=${query.encodeURLParameter()}&entity=song&limit=20&country=US"
            val response = client.get(url)
            if (response.status != HttpStatusCode.OK) return emptyList()
            parseResults(response.bodyAsText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Descarga la imagen de una URL (la portada elegida): bytes crudos. */
    suspend fun download(url: String): ByteArray? {
        return try {
            val response = client.get(url)
            if (response.status != HttpStatusCode.OK) null else response.bodyAsBytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseResults(body: String): List<CoverArtResult> {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
            val array = root["results"] as? JsonArray ?: return emptyList()
            val results = mutableListOf<CoverArtResult>()
            for (element in array) {
                val item = element as? JsonObject ?: continue
                val artwork100 =
                    (item["artworkUrl100"] as? JsonPrimitive)?.contentOrNull ?: continue
                if (artwork100.isBlank()) continue
                // Mejorar resolución: 100x100 -> 600x600.
                val artwork600 = artwork100.replace("100x100", "600x600")
                results += CoverArtResult(
                    artworkUrl = artwork600,
                    thumbnailUrl = artwork100,
                    trackName = (item["trackName"] as? JsonPrimitive)?.contentOrNull ?: "",
                    artistName = (item["artistName"] as? JsonPrimitive)?.contentOrNull ?: "",
                    collectionName = (item["collectionName"] as? JsonPrimitive)?.contentOrNull ?: ""
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }
}
