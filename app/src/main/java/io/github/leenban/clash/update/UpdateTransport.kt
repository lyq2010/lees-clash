package io.github.leenban.clash.update

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class UpdateResponse(val status: Int, val body: InputStream, private val disconnect: () -> Unit = {}) : Closeable {
    override fun close() { try { body.close() } finally { disconnect() } }
}

/** Platform HTTPS keeps the updater's transport small; release validation follows Music. */
internal fun openUpdate(url: URL, timeoutMs: Int): UpdateResponse {
    require(url.protocol == "https")
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = minOf(timeoutMs, 10000)
    connection.readTimeout = timeoutMs
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("Cache-Control", "no-cache")
    try {
        val code = connection.responseCode
        return UpdateResponse(code, if (code in 200..299) connection.inputStream else ByteArrayInputStream(byteArrayOf())) { connection.disconnect() }
    } catch (error: Exception) { connection.disconnect(); throw error }
}
