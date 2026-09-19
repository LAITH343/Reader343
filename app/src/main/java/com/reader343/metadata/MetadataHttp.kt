package com.reader343.metadata

import com.reader343.update.GitHubReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

class HttpStatusException(val code: Int) : IOException("HTTP $code")

object AllowedHosts {
    private val exact = setOf(
        "www.googleapis.com",
        "books.google.com",
        "openlibrary.org",
        "covers.openlibrary.org",
        "archive.org",
    )
    private const val ARCHIVE_SUFFIX = ".archive.org"

    fun isAllowed(url: URL): Boolean {
        if (url.protocol != "https") return false
        val host = url.host.lowercase()
        return host in exact || host.endsWith(ARCHIVE_SUFFIX)
    }
}

@Singleton
class MetadataHttp @Inject constructor() {

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val connection = open(url, accept = "application/json", headers = headers)
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            val connection = open(url, accept = "image/*")
            try {
                val type = connection.contentType.orEmpty()
                if (!type.startsWith("image/")) return@withContext false
                if (connection.contentLengthLong > MAX_IMAGE_BYTES) return@withContext false
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) throw IOException("Image too large")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            partial.length() > MIN_IMAGE_BYTES && partial.renameTo(target)
        } catch (e: IOException) {
            false
        } finally {
            partial.delete()
        }
    }

    private fun open(url: String, accept: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            if (!AllowedHosts.isAllowed(current)) throw IOException("Host not allowed: ${current.host}")
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", GitHubReleases.userAgent())
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            val code = connection.responseCode
            when (code) {
                in 200..299 -> return connection
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current = URL(current, location ?: throw IOException("Redirect without location"))
                }
                else -> {
                    connection.disconnect()
                    throw HttpStatusException(code)
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_REDIRECTS = 4
        const val BUFFER_SIZE = 16 * 1024
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
        const val MIN_IMAGE_BYTES = 200L
    }
}
