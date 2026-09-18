package com.reader343.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalUnit
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.BookCard
import com.reader343.ui.components.DestructiveTextButton
import com.reader343.ui.components.EmptyState
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.SectionHeader
import com.reader343.ui.components.TopBarAction
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.spacing

private const val PDF_MIME = "application/pdf"

@Composable
fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
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
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::retry,
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
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.library_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    if ((state as? LibraryUiState.Content)?.continueBook != null) {
                        ImportAction(importing = importing, onClick = onImport)
                    }
                    TopBarAction(
                        icon = R.drawable.ic_bar_chart,
                        contentDescription = stringResource(R.string.action_stats),
                        onClick = onOpenStats,
                    )
                    TopBarAction(
                        icon = R.drawable.ic_settings,
                        contentDescription = stringResource(R.string.action_settings),
                        onClick = onOpenSettings,
                    )
                },
            )
        },
        floatingActionButton = {
            if (state is LibraryUiState.Content) {
                val continueBook = state.continueBook
                if (continueBook != null) {
                    ContinueFab(onClick = { onOpenBook(continueBook.id) })
                } else {
                    ImportFab(importing = importing, onClick = onImport)
                }
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
                LibraryUiState.Loading -> LoadingState()
                LibraryUiState.Error -> ErrorState(
                    message = stringResource(R.string.library_load_failed),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
                LibraryUiState.Empty -> EmptyState(
                    icon = R.drawable.ic_library,
                    title = stringResource(R.string.library_empty),
                    body = stringResource(R.string.library_empty_hint),
                    action = { ImportButton(importing = importing, onClick = onImport) },
                )
                is LibraryUiState.Content -> HomeContent(
                    state = state,
                    onOpenBook = onOpenBook,
                    onOpenStats = onOpenStats,
                    onRemoveBook = { pendingDeleteId = it },
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
private fun ContinueFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
        text = { Text(stringResource(R.string.home_continue)) },
    )
}

@Composable
private fun ImportAction(importing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !importing) {
        if (importing) {
            ImportIcon(importing = true, description = stringResource(R.string.library_importing))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.library_import),
            )
        }
    }
}

@Composable
private fun ImportFab(importing: Boolean, onClick: () -> Unit) {
    val importingLabel = stringResource(R.string.library_importing)
    ExtendedFloatingActionButton(
        onClick = { if (!importing) onClick() },
        modifier = Modifier.semantics { if (importing) disabled() },
        icon = { ImportIcon(importing = importing, description = importingLabel) },
        text = { Text(stringResource(R.string.library_import)) },
    )
}

@Composable
private fun ImportButton(importing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !importing,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        ImportIcon(
            importing = importing,
            description = stringResource(R.string.library_importing),
            size = ButtonDefaults.IconSize,
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.library_import))
    }
}

@Composable
private fun ImportIcon(importing: Boolean, description: String, size: Dp = FabIconSize) {
    if (importing) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = description },
            strokeWidth = 2.dp,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun HomeContent(
    state: LibraryUiState.Content,
    onOpenBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onRemoveBook: (Long) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = BookMinWidth),
        contentPadding = PaddingValues(
            start = spacing.lg,
            top = spacing.sm,
            end = spacing.lg,
            bottom = spacing.fabClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.stats?.let { stats ->
            item(key = StatsKey, span = { GridItemSpan(maxLineSpan) }) {
                HomeStatsStrip(
                    stats = stats,
                    onOpenStats = onOpenStats,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = BooksHeaderKey, span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(text = stringResource(R.string.home_all_books))
        }
        items(state.books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onOpen = { onOpenBook(book.id) },
                onRemove = { onRemoveBook(book.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun DeleteBookDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.delete_book_title)) },
        text = { Text(stringResource(R.string.delete_book_message, title)) },
        confirmButton = {
            DestructiveTextButton(text = stringResource(R.string.action_delete), onClick = onConfirm)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val StatsKey = "stats"
private const val BooksHeaderKey = "books_header"
private val BookMinWidth = 150.dp
private val FabIconSize = 24.dp

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
            state = LibraryUiState.Content(
                books = PreviewBooks,
                continueBook = PreviewBooks.first(),
                stats = HomeStats(streakDays = 4, todayMs = 1_380_000, todayPages = 12, goal = DailyGoal(GoalUnit.Minutes, 30), booksInProgress = 1),
            ),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onDeleteBook = {},
            onOpenStats = {},
            onOpenSettings = {},
            onRetry = {},
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
            onOpenSettings = {},
            onRetry = {},
        )
    }
}
