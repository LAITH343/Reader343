package com.reader343.metadata

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.reader343.BuildConfig
import com.reader343.domain.BookMetadata
import com.reader343.domain.MetadataQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleBooksProvider @Inject constructor(
    private val http: MetadataHttp,
    private val identity: AppIdentity,
) {

    suspend fun search(query: MetadataQuery): List<BookMetadata> {
        val key = BuildConfig.GOOGLE_BOOKS_API_KEY
        val url = "https://www.googleapis.com/books/v1/volumes?q=${encode(query.text)}" +
            "&maxResults=$MAX_RESULTS&printType=books" +
            if (key.isNotBlank()) "&key=${encode(key)}" else ""
        return GoogleBooksParser.parse(http.getText(url, androidHeaders(key)))
    }

    private fun androidHeaders(key: String): Map<String, String> {
        if (key.isBlank()) return emptyMap()
        val cert = identity.certificateSha1 ?: return mapOf("X-Android-Package" to identity.packageName)
        return mapOf("X-Android-Package" to identity.packageName, "X-Android-Cert" to cert)
    }
}

@Singleton
class OpenLibraryProvider @Inject constructor(private val http: MetadataHttp) {

    suspend fun search(query: MetadataQuery): List<BookMetadata> {
        val url = "https://openlibrary.org/search.json?q=${encode(query.text)}" +
            "&limit=$MAX_RESULTS&fields=${OpenLibraryParser.SEARCH_FIELDS}"
        val preferred = (query as? MetadataQuery.Isbn)?.isbn
        return OpenLibraryParser.parse(http.getText(url), preferred)
    }

    suspend fun description(workKey: String): String? {
        if (!workKey.matches(WorkKey)) return null
        return OpenLibraryParser.parseDescription(http.getText("https://openlibrary.org$workKey.json"))
    }

    private companion object {
        val WorkKey = Regex("""/works/OL\d+W""")
    }
}

@Singleton
class Connectivity @Inject constructor(@ApplicationContext private val context: Context) {

    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

const val MAX_RESULTS = 5
