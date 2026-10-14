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
            for ((key, values) in request.headers()) {
                for (value in values) {
                    connection.setRequestProperty(key, value)
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
