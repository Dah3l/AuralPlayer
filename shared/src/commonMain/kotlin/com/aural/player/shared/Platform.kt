// Puentes a la plataforma: lo que commonMain no puede hacer solo.
// Ambas plataformas son JVM, así que los actual usan java.io.File
// y un motor Ktor distinto (OkHttp en Android, CIO en escritorio).
package com.aural.player.shared

import io.ktor.client.HttpClient

/** Cliente HTTP de la plataforma, con timeouts de 5 s y User-Agent de Aural. */
expect fun createHttpClient(): HttpClient

/** Archivos locales (lectura/escritura simple, rutas con "/"). */
expect object LocalFiles {
    fun exists(path: String): Boolean
    fun readText(path: String): String?
    fun writeText(path: String, text: String)
    fun mkdirs(path: String)
}
