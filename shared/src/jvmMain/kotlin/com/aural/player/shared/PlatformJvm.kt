// Puente JVM (escritorio): motor Ktor CIO + archivos con java.io.File.
package com.aural.player.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.io.File

actual fun createHttpClient(): HttpClient = HttpClient(CIO) {
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

    actual fun readBytes(path: String): ByteArray? =
        try { File(path).readBytes() } catch (e: Exception) { null }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        File(path).writeBytes(bytes)
    }

    actual fun deleteFile(path: String): Boolean =
        try { File(path).delete() } catch (e: Exception) { false }
}

actual object SystemDates {
    actual fun today(): DateParts {
        val now = java.time.LocalDate.now()
        return DateParts(now.year, now.monthValue, now.dayOfMonth)
    }

    actual fun keyDaysAgo(daysAgo: Int): String {
        val date = java.time.LocalDate.now().minusDays(daysAgo.toLong())
        return "${date.year}-${date.monthValue.toString().padStart(2, '0')}-" +
            date.dayOfMonth.toString().padStart(2, '0')
    }

    actual fun hour(): Int = java.time.LocalTime.now().hour

    actual fun nowEpochMillis(): Long = System.currentTimeMillis()
}
