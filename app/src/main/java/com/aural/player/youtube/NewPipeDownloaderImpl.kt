// Implementación del Downloader de NewPipe usando HttpURLConnection
package com.aural.player.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = request.httpMethod()
            connection.instanceFollowRedirects = true
            // Headers de navegador real para evitar detección
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            for ((key, values) in request.headers()) {
                for (value in values) {
                    // No sobrescribir el User-Agent que ya pusimos
                    if (!key.equals("User-Agent", ignoreCase = true)) {
                        connection.setRequestProperty(key, value)
                    }
                }
            }
            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }
            val code = connection.responseCode
            val message = connection.responseMessage ?: ""
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { it.value }
            return Response(code, message, headers, body, request.url())
        } catch (e: IOException) {
            throw IOException("Error en request a ${request.url()}: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }
}
