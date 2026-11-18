package com.aural.player.shared

/**
 * Canción de la biblioteca de escritorio. En PC la identidad es la
 * ruta del archivo (no hay MediaStore ni URIs de contenido).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    /** Última modificación del archivo (ms epoch): alimenta "Agregadas recientemente". */
    val dateAddedMs: Long = 0L
)
