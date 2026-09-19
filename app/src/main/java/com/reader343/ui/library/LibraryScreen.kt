package com.reader343.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.LibraryFilter
import com.reader343.ui.metadata.MetadataPickerHost
import com.reader343.ui.metadata.rememberMetadataPicker
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SelectableChip
import com.reader343.ui.components.StateContent
import com.reader343.ui.components.formatNumber
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appType
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenInfo: (Long) -> Unit,
    onOpenNotes: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.ImportFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.library_import_failed))
            }
        }
    }
    var menuBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberMetadataPicker()
    val scope = rememberCoroutineScope()

    LibraryScreen(
        state = state,
        importing = importing,
        snackbarHostState = snackbarHostState,
        onImport = { launcher.launch(arrayOf(PDF_MIME)) },
        onOpenBook = onOpenInfo,
        onOpenMenu = { menuBookId = it },
        onReview = picker::openPicker,
        onFilter = viewModel::setFilter,
        onRetry = viewModel::retry,
    )

    BookMenuHost(
        books = (state as? LibraryUiState.Content)?.books.orEmpty(),
        menuBookId = menuBookId,
        onMenuBookChange = { menuBookId = it },
        actions = viewModel.bookMenuActions(
            onOpenBook = onOpenBook,
            onOpenInfo = onOpenInfo,
            onEdit = { picker.startEdit(it) },
            onOpenNotes = onOpenNotes,
        ),
    )

    MetadataPickerHost(
        viewModel = picker,
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    importing: Boolean,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onReview: (Long) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            LibraryUiState.Loading -> LoadingState(Modifier.padding(padding))
            LibraryUiState.Error -> ErrorState(
                message = stringResource(R.string.library_load_failed),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
                modifier = Modifier.padding(padding),
            )
            LibraryUiState.Empty, is LibraryUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "header") {
                    LibraryHeader(
                        showImport = state is LibraryUiState.Content,
                        importing = importing,
                        onImport = onImport,
                    )
                }
                if (state is LibraryUiState.Content) {
                    item(key = "filters") {
                        FilterRow(
                            selected = state.filter,
                            total = state.books.size,
                            onFilter = onFilter,
                        )
                    }
                    val books = state.visibleBooks
                    if (books.isEmpty()) {
                        item(key = "filter_empty") {
                            StateContent(
                                icon = R.drawable.ic_ph_books,
                                title = stringResource(R.string.library_filter_empty),
                                body = stringResource(R.string.library_filter_empty_hint),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                        }
                    }
                    items(books, key = { it.id }) { book ->
                        LibraryBookRow(
                            book = book,
                            onOpen = { onOpenBook(book.id) },
                            onMenu = { onOpenMenu(book.id) },
                            onReview = { onReview(book.id) },
                            modifier = Modifier
                                .animateItem()
                                .padding(start = ScreenPadding, top = 12.dp, end = ScreenPadding),
                        )
                    }
                } else {
                    item(key = "empty") { EmptyLibraryContent(importing = importing, onImport = onImport) }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    showImport: Boolean,
    importing: Boolean,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.appType.screenTitle,
            color = MaterialTheme.appColors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showImport) {
            PrimaryButton(
                text = stringResource(if (importing) R.string.library_importing else R.string.library_import_short),
                onClick = onImport,
                icon = R.drawable.ic_ph_plus,
                enabled = !importing,
                compact = true,
            )
        }
    }
}

@Composable
private fun FilterRow(
    selected: LibraryFilter,
    total: Int,
    onFilter: (LibraryFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .padding(top = 14.dp, bottom = 4.dp)
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(LibraryFilter.entries) { filter ->
            SelectableChip(
                text = when (filter) {
                    LibraryFilter.Reading -> stringResource(R.string.library_filter_reading)
                    LibraryFilter.All -> stringResource(R.string.library_filter_all, formatNumber(total))
                    LibraryFilter.Finished -> stringResource(R.string.library_filter_finished)
                    LibraryFilter.WithNotes -> stringResource(R.string.library_filter_with_notes)
                },
                selected = filter == selected,
                onClick = { onFilter(filter) },
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LibraryContentPreview() {
    Reader343Theme {
        LibraryScreen(
            state = LibraryUiState.Content(
                books = PreviewBooks,
                continueBook = PreviewBooks.first(),
                stats = null,
                filter = LibraryFilter.All,
            ),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onFilter = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900, locale = "ar")
@Composable
private fun LibraryLightRtlPreview() {
    Reader343Theme(darkTheme = false) {
        LibraryScreen(
            state = LibraryUiState.Content(
                books = PreviewBooks,
                continueBook = PreviewBooks.first(),
                stats = null,
                filter = LibraryFilter.Reading,
            ),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onFilter = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LibraryEmptyPreview() {
    Reader343Theme {
        LibraryScreen(
            state = LibraryUiState.Empty,
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onFilter = {},
            onRetry = {},
        )
    }
}
