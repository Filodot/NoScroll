package com.filodot.noscroll.data.learning.ai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class AiHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>>,
)

fun interface AiHttpTransport {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

class UrlConnectionAiHttpTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 90_000,
    private val maxResponseBytes: Int = 2_000_000,
) : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as? HttpsURLConnection)
                ?: error("AI endpoint must use HTTPS")
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                request.headers.forEach(connection::setRequestProperty)
                connection.outputStream.use { output ->
                    output.write(request.body.toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                AiHttpResponse(
                    statusCode = status,
                    body = stream?.use { it.readUtf8Limited(maxResponseBytes) }.orEmpty(),
                    headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapKeys { requireNotNull(it.key) },
                )
            } catch (error: SocketTimeoutException) {
                throw AiNetworkTimeoutException(error)
            } finally {
                connection.disconnect()
            }
        }

    private fun InputStream.readUtf8Limited(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) error("AI response exceeds $maxBytes bytes")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

class AiNetworkTimeoutException(cause: Throwable) : Exception(cause)
