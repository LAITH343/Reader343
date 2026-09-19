package com.reader343.ui.metadata

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.domain.MetadataProvider
import com.reader343.domain.MetadataStatus

data class AuthorLabel(val text: String, val muted: Boolean)

@Composable
fun authorLabel(book: BookWithProgress): AuthorLabel? {
    val author = book.metadata.author
    return when {
        author != null -> AuthorLabel(author, muted = false)
        book.needsReview -> AuthorLabel(stringResource(R.string.metadata_unconfirmed), muted = true)
        book.metadata.status == MetadataStatus.None || book.metadata.status == MetadataStatus.Applied ->
            AuthorLabel(stringResource(R.string.metadata_author_unknown), muted = false)
        else -> null
    }
}

@Composable
fun providerName(provider: MetadataProvider): String = stringResource(
    when (provider) {
        MetadataProvider.GoogleBooks -> R.string.metadata_provider_google_books
        MetadataProvider.OpenLibrary -> R.string.metadata_provider_open_library
    },
)
