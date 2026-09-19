package com.reader343.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookInfo
import com.reader343.domain.BookWithProgress
import com.reader343.domain.MetadataProvider
import com.reader343.domain.MetadataStatus
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.BookCover
import com.reader343.ui.components.HeroCard
import com.reader343.ui.components.IconBadgeButton
import com.reader343.ui.components.IconButtonTone
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.ProgressBar
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.SectionLabel
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.components.formatRelative
import com.reader343.ui.components.riseIn
import com.reader343.ui.library.BookMenuActions
import com.reader343.ui.library.BookMenuHost
import com.reader343.ui.metadata.MetadataPickerHost
import com.reader343.ui.metadata.PickerState
import com.reader343.ui.metadata.authorLabel
import com.reader343.ui.metadata.providerName
import com.reader343.ui.metadata.rememberMetadataPicker
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import kotlinx.coroutines.launch

@Composable
fun BookDetailRoute(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenNotes: (Long) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberMetadataPicker()
    val pickerState by picker.picker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == BookDetailUiState.Missing) onBack()
    }

    BookDetailScreen(
        state = state,
        lookingUp = pickerState is PickerState.Loading,
        snackbarHostState = snackbarHostState,
        actions = BookDetailActions(
            onBack = onBack,
            onMenu = { menuOpen = true },
            onResume = { onOpenBook(viewModel.bookId) },
            onFetch = { picker.openPicker(viewModel.bookId) },
        ),
    )

    BookMenuHost(
        books = listOfNotNull((state as? BookDetailUiState.Content)?.book),
        menuBookId = viewModel.bookId.takeIf { menuOpen },
        onMenuBookChange = { menuOpen = it != null },
        actions = BookMenuActions(
            onResume = onOpenBook,
            onOpenInfo = null,
            onEdit = { picker.startEdit(it) },
            onOpenNotes = onOpenNotes,
            onSetFinished = viewModel::setFinished,
            onResetProgress = viewModel::resetProgress,
            onRemove = viewModel::deleteBook,
        ),
    )

    MetadataPickerHost(
        viewModel = picker,
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )
}

class BookDetailActions(
    val onBack: () -> Unit,
    val onMenu: () -> Unit,
    val onResume: () -> Unit,
    val onFetch: () -> Unit,
)

@Composable
fun BookDetailScreen(
    state: BookDetailUiState,
    lookingUp: Boolean,
    actions: BookDetailActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            DetailHeader(actions = actions, showMenu = state is BookDetailUiState.Content)
            when (state) {
                is BookDetailUiState.Content -> DetailContent(book = state.book, lookingUp = lookingUp, actions = actions)
                else -> LoadingState(Modifier.height(240.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailHeader(actions: BookDetailActions, showMenu: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 6.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadgeButton(
            icon = R.drawable.ic_ph_arrow_left,
            contentDescription = stringResource(R.string.action_back),
            onClick = actions.onBack,
            tone = IconButtonTone.Plain,
        )
        Text(
            text = stringResource(R.string.book_info_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.ink3,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showMenu) {
            IconBadgeButton(
                icon = R.drawable.ic_ph_dots_three_vertical,
                contentDescription = stringResource(R.string.action_more),
                onClick = actions.onMenu,
                tone = IconButtonTone.Plain,
            )
        }
    }
}

@Composable
private fun DetailContent(
    book: BookWithProgress,
    lookingUp: Boolean,
    actions: BookDetailActions,
) {
    val colors = MaterialTheme.appColors
    HeroCard(
        modifier = Modifier
            .padding(start = ScreenPadding, top = 8.dp, end = ScreenPadding)
            .fillMaxWidth()
            .riseIn(),
        shape = MaterialTheme.appShapes.hero,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                shape = MaterialTheme.appShapes.item,
                modifier = Modifier.size(width = 96.dp, height = 136.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
                    color = colors.ink,
                )
                authorLabel(book)?.let { label ->
                    Text(
                        text = label.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (label.muted) colors.ink3 else colors.ink2,
                    )
                }
                detailFacts(book.metadata)?.let { facts ->
                    Text(
                        text = facts,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.ink2,
                    )
                }
                Spacer(Modifier.weight(1f))
                ProgressBar(
                    progress = book.percent,
                    height = 6.dp,
                    color = colors.accLt,
                    trackColor = colors.accLine,
                )
                Text(
                    text = stringResource(
                        R.string.book_menu_meta,
                        formatPercent(book.percent),
                        formatNumber(book.lastPage + 1),
                        formatNumber(book.pageCount),
                        if (book.marks > 0) {
                            pluralStringResource(R.plurals.library_marks, book.marks, formatNumber(book.marks))
                        } else {
                            stringResource(R.string.library_no_marks)
                        },
                    ),
                    style = MaterialTheme.appType.caption,
                    color = colors.ink2,
                )
            }
        }
        if (book.needsReview) ReviewBanner(onPick = actions.onFetch)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = stringResource(if (book.started) R.string.book_detail_resume else R.string.book_detail_start),
                onClick = actions.onResume,
                icon = R.drawable.ic_ph_play_fill,
                modifier = Modifier.weight(1.5f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            )
            SecondaryButton(
                text = stringResource(if (lookingUp) R.string.metadata_looking_up else R.string.metadata_fetch_info),
                onClick = actions.onFetch,
                icon = if (lookingUp) R.drawable.ic_ph_circle_notch else R.drawable.ic_ph_cloud_arrow_down,
                enabled = !lookingUp,
                borderColor = colors.heroLine,
                modifier = Modifier.weight(1f),
            )
        }
    }

    book.metadata.description?.let { description ->
        AppCard(
            modifier = Modifier
                .padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding)
                .fillMaxWidth(),
            shape = MaterialTheme.appShapes.card,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SectionLabel(text = stringResource(R.string.metadata_description))
                Text(
                    text = description,
                    style = MaterialTheme.appType.quote.copy(fontSize = 14.5.sp, lineHeight = 23.sp),
                    color = colors.ink,
                )
                sourceLine(book.metadata)?.let { source ->
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_cloud_check),
                            contentDescription = null,
                            tint = colors.ink3,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = source,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            fontWeight = FontWeight.Normal,
                            color = colors.ink3,
                        )
                    }
                }
            }
        }
    }

    if (!book.metadata.hasRemoteCover) {
        val shape = MaterialTheme.appShapes.stepper
        Row(
            modifier = Modifier
                .padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding)
                .fillMaxWidth()
                .background(colors.surf0, shape)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_image_square),
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.metadata_local_cover),
                style = MaterialTheme.appType.caption,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun ReviewBanner(onPick: () -> Unit) {
    val amber = MaterialTheme.appColors.amber
    val shape = MaterialTheme.appShapes.button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(amber.fill, shape)
            .border(1.dp, amber.border, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_seal_question),
            contentDescription = null,
            tint = amber.text,
            modifier = Modifier.size(17.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = stringResource(R.string.metadata_review_banner),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = amber.text,
            )
            val buttonShape = MaterialTheme.appShapes.item
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 38.dp)
                    .background(amber.fill, buttonShape)
                    .border(1.dp, amber.border, buttonShape)
                    .appClickable(shape = buttonShape, onClick = onPick)
                    .padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_list_magnifying_glass),
                    contentDescription = null,
                    tint = amber.text,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.metadata_pick_match),
                    style = MaterialTheme.typography.labelMedium,
                    color = amber.text,
                )
            }
        }
    }
}

@Composable
private fun detailFacts(info: BookInfo): String? {
    val parts = listOfNotNull(info.publishedYear?.toString(), info.publisher)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun sourceLine(info: BookInfo): String? {
    val provider = info.provider ?: return null
    val fetchedAt = info.fetchedAt ?: return null
    return stringResource(R.string.metadata_source, providerName(provider), formatRelative(fetchedAt))
}

private val ScreenPadding = 20.dp

private val PreviewEnriched = BookWithProgress(
    id = 1,
    title = "Designing Data-Intensive Applications",
    coverPath = null,
    pageCount = 491,
    lastPage = 26,
    percent = 0.05f,
    lastReadAt = System.currentTimeMillis() - 3_600_000,
    highlightCount = 12,
    noteCount = 2,
    metadata = BookInfo(
        author = "Martin Kleppmann",
        description = "A tour of the ideas behind modern data systems: replication, partitioning, transactions and consensus.",
        publishedYear = 2017,
        publisher = "O'Reilly Media",
        provider = MetadataProvider.OpenLibrary,
        fetchedAt = System.currentTimeMillis() - 2 * 86_400_000L,
        status = MetadataStatus.Applied,
        hasRemoteCover = true,
    ),
)

private val PreviewReview = BookWithProgress(
    id = 3,
    title = "crafting-interpreters",
    coverPath = null,
    pageCount = 640,
    lastPage = 485,
    percent = 0.76f,
    lastReadAt = System.currentTimeMillis() - 259_200_000,
    highlightCount = 40,
    noteCount = 12,
    metadata = BookInfo(status = MetadataStatus.Review),
)

private val PreviewActions = BookDetailActions({}, {}, {}, {})

@Preview(name = "Enriched dark", showBackground = true, heightDp = 900)
@Composable
private fun BookDetailEnrichedPreview() {
    Reader343Theme(darkTheme = true) {
        BookDetailScreen(state = BookDetailUiState.Content(PreviewEnriched), lookingUp = false, actions = PreviewActions)
    }
}

@Preview(name = "Review light", showBackground = true, heightDp = 900)
@Composable
private fun BookDetailReviewPreview() {
    Reader343Theme(darkTheme = false) {
        BookDetailScreen(state = BookDetailUiState.Content(PreviewReview), lookingUp = true, actions = PreviewActions)
    }
}

@Preview(name = "Review RTL", showBackground = true, heightDp = 900, locale = "ar")
@Composable
private fun BookDetailRtlPreview() {
    Reader343Theme(darkTheme = true) {
        BookDetailScreen(state = BookDetailUiState.Content(PreviewReview), lookingUp = false, actions = PreviewActions)
    }
}
