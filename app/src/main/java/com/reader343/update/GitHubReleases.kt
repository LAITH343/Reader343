package com.reader343.update

import com.reader343.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LatestResponse {
    data class Found(val json: String) : LatestResponse
    data object None : LatestResponse
}

@Singleton
class GitHubReleases @Inject constructor() {

    suspend fun fetchLatest(): LatestResponse = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    LatestResponse.Found(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> LatestResponse.None
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPOSITORY = "LAITH343/Reader343"
        private const val LATEST_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val API_VERSION = "2022-11-28"
        private const val TIMEOUT_MS = 15_000

        fun userAgent(): String = "Reader343/${BuildConfig.VERSION_NAME}"
    }
}
