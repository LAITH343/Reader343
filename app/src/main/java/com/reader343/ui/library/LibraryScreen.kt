package com.reader343.ui.library

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.ui.theme.Reader343Theme
import java.io.File
import kotlin.math.roundToInt

private const val PDF_MIME = "application/pdf"

@Composable
fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.ImportFailed ->
                    snackbarHostState.showSnackbar(context.getString(R.string.library_import_failed))
            }
        }
    }

    LibraryScreen(
        state = state,
        importing = importing,
        snackbarHostState = snackbarHostState,
        onImport = { launcher.launch(arrayOf(PDF_MIME)) },
        onOpenBook = onOpenBook,
        onDeleteBook = viewModel::deleteBook,
        onOpenStats = onOpenStats,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    importing: Boolean,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onDeleteBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    TextButton(onClick = onOpenStats) {
                        Text(stringResource(R.string.action_stats))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is LibraryUiState.Content) {
                ImportFab(importing = importing, onClick = onImport)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                LibraryUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LibraryUiState.Empty -> EmptyLibrary(
                    importing = importing,
                    onImport = onImport,
                    modifier = Modifier.align(Alignment.Center),
                )
                is LibraryUiState.Content -> BookGrid(
                    books = state.books,
                    onOpenBook = onOpenBook,
                    onLongPressBook = { pendingDeleteId = it },
                )
            }
        }
    }

    val pendingBook = (state as? LibraryUiState.Content)?.books?.firstOrNull { it.id == pendingDeleteId }
    if (pendingBook != null) {
        DeleteBookDialog(
            title = pendingBook.title,
            onConfirm = {
                onDeleteBook(pendingBook.id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun ImportFab(importing: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = { if (!importing) onClick() },
        icon = {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            }
        },
        text = { Text(stringResource(R.string.library_import)) },
    )
}

@Composable
private fun EmptyLibrary(
    importing: Boolean,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.library_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.library_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onImport, enabled = !importing) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.library_import))
        }
    }
}

@Composable
private fun BookGrid(
    books: List<BookWithProgress>,
    onOpenBook: (Long) -> Unit,
    onLongPressBook: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = { onOpenBook(book.id) },
                onLongClick = { onLongPressBook(book.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: BookWithProgress,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        shape = CardShape,
    ) {
        BookCover(book)
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { book.percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.library_progress_detail,
                    stringResource(R.string.library_percent, (book.percent * 100).roundToInt()),
                    lastReadLabel(book.lastReadAt),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.library_pages, book.pageCount, book.pageCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookCover(book: BookWithProgress) {
    val coverModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(COVER_ASPECT)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (book.coverPath != null) {
        AsyncImage(
            model = File(book.coverPath),
            contentDescription = stringResource(R.string.library_cover_description, book.title),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = coverModifier,
        )
    } else {
        Box(coverModifier, contentAlignment = Alignment.Center) {
            Text(
                text = book.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun lastReadLabel(lastReadAt: Long?): String =
    if (lastReadAt == null) {
        stringResource(R.string.library_not_started)
    } else {
        DateUtils.getRelativeTimeSpanString(
            lastReadAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

@Composable
private fun DeleteBookDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_book_title)) },
        text = { Text(stringResource(R.string.delete_book_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val CardShape = RoundedCornerShape(12.dp)
private const val COVER_ASPECT = 0.75f

private val PreviewBooks = listOf(
    BookWithProgress(1, "Designing Data-Intensive Applications", null, 611, 120, 0.2f, System.currentTimeMillis() - 3_600_000),
    BookWithProgress(2, "Structure and Interpretation of Computer Programs", null, 657, 0, 0f, null),
    BookWithProgress(3, "The Pragmatic Programmer", null, 352, 351, 1f, System.currentTimeMillis() - 86_400_000),
)

@Preview(showBackground = true)
@Composable
private fun LibraryContentPreview() {
    Reader343Theme {
        LibraryScreen(
            state = LibraryUiState.Content(PreviewBooks),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onDeleteBook = {},
            onOpenStats = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryEmptyPreview() {
    Reader343Theme {
        LibraryScreen(
            state = LibraryUiState.Empty,
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onDeleteBook = {},
            onOpenStats = {},
        )
    }
}
