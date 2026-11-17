// Puente Android: motor Ktor OkHttp + archivos con java.io.File.
package com.aural.player.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.io.File

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 5000
        requestTimeoutMillis = 5000
        socketTimeoutMillis = 5000
    }
    defaultRequest { header(HttpHeaders.UserAgent, "AuralPlayer/1.0") }
}

actual object LocalFiles {
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun readText(path: String): String? =
        try { File(path).readText() } catch (e: Exception) { null }

    actual fun writeText(path: String, text: String) {
        File(path).writeText(text)
    }

    actual fun mkdirs(path: String) {
        File(path).mkdirs()
    }
}
