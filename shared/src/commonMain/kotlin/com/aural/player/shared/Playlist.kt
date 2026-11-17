// Playlist de escritorio: nombre + rutas de archivo (la identidad
// estable de una canción en PC; los IDs salen de la ruta y las
// rutas sobreviven a re-escaneos y cambios de carpeta).
package com.aural.player.shared

data class Playlist(
    val id: String,
    val name: String,
    val songPaths: List<String> = emptyList()
)
