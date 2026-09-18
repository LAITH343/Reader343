package com.reader343.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalUnit
import com.reader343.domain.LibraryFilter
import com.reader343.domain.WeekDay
import com.reader343.domain.WeekDayState
import com.reader343.domain.weekProgress
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.BookCover
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.GhostButton
import com.reader343.ui.components.GoalRing
import com.reader343.ui.components.HeroCard
import com.reader343.ui.components.IconBadgeButton
import com.reader343.ui.components.IconButtonTone
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.Pill
import com.reader343.ui.components.PillTone
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.ProgressBar
import com.reader343.ui.components.SectionHeader
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.components.formatWeekday
import com.reader343.ui.components.riseIn
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.settings.DailyGoalSheetHost
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle

@Composable
fun HomeRoute(
    onOpenBook: (Long) -> Unit,
    onOpenNotes: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
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
    var goalSheet by rememberSaveable { mutableStateOf(false) }

    HomeScreen(
        state = state,
        importing = importing,
        snackbarHostState = snackbarHostState,
        onImport = { launcher.launch(arrayOf(PDF_MIME)) },
        onOpenBook = onOpenBook,
        onOpenMenu = { menuBookId = it },
        onOpenLibrary = onOpenLibrary,
        onOpenSettings = onOpenSettings,
        onSetGoal = { goalSheet = true },
        onRetry = viewModel::retry,
    )

    DailyGoalSheetHost(visible = goalSheet, onDismiss = { goalSheet = false })

    BookMenuHost(
        books = (state as? LibraryUiState.Content)?.books.orEmpty(),
        menuBookId = menuBookId,
        onMenuBookChange = { menuBookId = it },
        actions = viewModel.bookMenuActions(onOpenBook = onOpenBook, onOpenNotes = onOpenNotes),
    )
}

@Composable
fun HomeScreen(
    state: LibraryUiState,
    importing: Boolean,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetGoal: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    updateAvailable: Boolean = false,
    onOpenUpdate: () -> Unit = {},
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
                    HomeHeader(
                        updateAvailable = updateAvailable,
                        onOpenUpdate = onOpenUpdate,
                        onOpenSettings = onOpenSettings,
                    )
                }
                if (state is LibraryUiState.Content) {
                    homeContent(state, onOpenBook, onOpenMenu, onOpenLibrary, onSetGoal)
                } else {
                    item(key = "empty") { EmptyLibraryContent(importing = importing, onImport = onImport) }
                }
            }
        }
    }
}

private fun LazyListScope.homeContent(
    state: LibraryUiState.Content,
    onOpenBook: (Long) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onSetGoal: () -> Unit,
) {
    state.continueBook?.let { book ->
        item(key = "resume") {
            ResumeCard(
                book = book,
                onResume = { onOpenBook(book.id) },
                modifier = Modifier
                    .padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding)
                    .riseIn(delayMs = 20),
            )
        }
    }
    state.stats?.let { stats ->
        item(key = "goal") {
            GoalCard(
                stats = stats,
                inProgress = state.inProgressCount,
                onSetGoal = onSetGoal,
                modifier = Modifier
                    .padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding)
                    .riseIn(delayMs = 60),
            )
        }
    }
    item(key = "shelf_header") {
        SectionHeader(
            text = stringResource(R.string.home_shelf),
            actionLabel = stringResource(R.string.home_see_all, formatNumber(state.books.size)),
            onAction = onOpenLibrary,
            modifier = Modifier.padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding),
        )
    }
    item(key = "shelf") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(state.shelf, key = { it.id }) { book ->
                ShelfBook(
                    book = book,
                    onOpen = { onOpenBook(book.id) },
                    onMenu = { onOpenMenu(book.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    updateAvailable: Boolean,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = greeting(), style = MaterialTheme.typography.bodySmall, color = colors.ink3)
            Text(text = stringResource(R.string.home_title), style = MaterialTheme.appType.screenTitle, color = colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (updateAvailable) {
                IconBadgeButton(
                    icon = R.drawable.ic_ph_download_simple,
                    contentDescription = stringResource(R.string.home_update_available),
                    onClick = onOpenUpdate,
                    tone = IconButtonTone.Accent,
                    badge = true,
                )
            }
            IconBadgeButton(
                icon = R.drawable.ic_ph_gear_six,
                contentDescription = stringResource(R.string.action_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun greeting(): String {
    val now = remember { LocalDateTime.now() }
    val weekday = formatWeekday(now.toLocalDate(), TextStyle.FULL)
    val res = when (now.hour) {
        in 5 until 12 -> R.string.home_greeting_morning
        in 12 until 17 -> R.string.home_greeting_afternoon
        in 17 until 22 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
    return stringResource(res, weekday)
}

@Composable
private fun ResumeCard(
    book: BookWithProgress,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val page = formatNumber(book.lastPage + 1)
    HeroCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                shape = MaterialTheme.appShapes.small,
                modifier = Modifier.size(width = 78.dp, height = 108.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.home_continue_reading).uppercase(currentLocale()),
                        style = MaterialTheme.appType.kicker,
                        color = colors.accLt,
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 21.sp),
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressBar(progress = book.percent, color = colors.accLt, trackColor = colors.accLine)
                    Text(
                        text = stringResource(
                            R.string.home_page_progress,
                            page,
                            formatNumber(book.pageCount),
                            formatPercent(book.percent),
                        ),
                        style = MaterialTheme.appType.caption,
                        color = colors.ink2,
                    )
                }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.home_resume_at, page),
            onClick = onResume,
            icon = R.drawable.ic_ph_play_fill,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    stats: HomeStats,
    inProgress: Int,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val goal = stats.goal
    val today = when (goal.unit) {
        GoalUnit.Minutes -> formatMinutes(stats.todayMs)
        GoalUnit.Pages -> pluralStringResource(R.plurals.stats_pages_value, stats.todayPages, formatNumber(stats.todayPages))
    }
    AppCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.appShapes.card) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (goal.enabled) {
                    val progress = goalProgress(stats)
                    val percent = formatPercent(progress)
                    GoalRing(progress = progress, pulse = progress < 1f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = percent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.ink)
                            Text(text = stringResource(R.string.home_of_goal), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Normal, color = colors.ink3)
                        }
                    }
                } else {
                    GoalRing(progress = 0f) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_target),
                            contentDescription = null,
                            tint = colors.accLt,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (goal.enabled) {
                                stringResource(R.string.home_today_of_goal, today, goalTarget(goal))
                            } else {
                                stringResource(R.string.home_today_amount, today)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink,
                        )
                        Text(
                            text = goalMessage(stats),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                        )
                    }
                    if (!goal.enabled) {
                        GhostButton(
                            text = stringResource(R.string.home_set_goal),
                            onClick = onSetGoal,
                            icon = R.drawable.ic_ph_target,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill(
                            text = pluralStringResource(R.plurals.home_streak, stats.streakDays, formatNumber(stats.streakDays)),
                            tone = PillTone.Accent,
                            icon = R.drawable.ic_ph_flame_fill,
                        )
                        Pill(
                            text = pluralStringResource(R.plurals.home_in_progress, inProgress, formatNumber(inProgress)),
                            tone = PillTone.Neutral,
                            icon = R.drawable.ic_ph_books,
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.line)
            WeekStrip(week = stats.week)
        }
    }
}

@Composable
private fun WeekStrip(week: List<WeekDay>) {
    val description = week.map { day ->
        val name = formatWeekday(day.date, TextStyle.FULL)
        when (day.state) {
            WeekDayState.Met -> stringResource(R.string.home_week_met, name)
            WeekDayState.Today -> stringResource(R.string.home_week_today, name)
            WeekDayState.Missed -> stringResource(R.string.home_week_missed, name)
            WeekDayState.Future -> stringResource(R.string.home_week_future, name)
        }
    }.joinToString("; ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        week.forEach { day ->
            WeekCell(day = day, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekCell(day: WeekDay, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.tile
    val (container, border, icon) = when (day.state) {
        WeekDayState.Met -> Triple(colors.accTint22, colors.accMid, R.drawable.ic_ph_check_fill)
        WeekDayState.Today -> Triple(colors.acc, colors.accMid, R.drawable.ic_ph_flame_fill)
        WeekDayState.Missed, WeekDayState.Future -> Triple(colors.surf, colors.line, R.drawable.ic_ph_minus)
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = formatWeekday(day.date, TextStyle.NARROW),
            style = MaterialTheme.typography.labelSmall,
            color = if (day.state == WeekDayState.Today) colors.accTx else colors.ink3,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(container, shape)
                .border(1.dp, border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = colors.ink, modifier = Modifier.size(13.dp))
        }
    }
}

private fun goalProgress(stats: HomeStats): Float {
    val goal = stats.goal
    if (!goal.enabled) return 0f
    return when (goal.unit) {
        GoalUnit.Minutes -> stats.todayMs.toFloat() / (goal.value * MINUTE_MS)
        GoalUnit.Pages -> stats.todayPages.toFloat() / goal.value
    }.coerceIn(0f, 1f)
}

@Composable
private fun goalTarget(goal: DailyGoal): String = when (goal.unit) {
    GoalUnit.Minutes -> formatMinutes(goal.value * MINUTE_MS)
    GoalUnit.Pages -> pluralStringResource(R.plurals.stats_pages_value, goal.value, formatNumber(goal.value))
}

@Composable
private fun goalMessage(stats: HomeStats): String {
    val goal = stats.goal
    if (!goal.enabled) return stringResource(R.string.home_goal_none)
    if (goalProgress(stats) >= 1f) return stringResource(R.string.home_goal_met)
    return when (goal.unit) {
        GoalUnit.Minutes -> {
            val left = (goal.value - (stats.todayMs / MINUTE_MS).toInt()).coerceAtLeast(1)
            pluralStringResource(R.plurals.home_goal_remaining_minutes, left, formatNumber(left))
        }
        GoalUnit.Pages -> {
            val left = (goal.value - stats.todayPages).coerceAtLeast(1)
            pluralStringResource(R.plurals.home_goal_remaining_pages, left, formatNumber(left))
        }
    }
}

internal const val PDF_MIME = "application/pdf"
internal val ScreenPadding = 20.dp
private const val MINUTE_MS = 60_000L

private fun previewStats(goal: DailyGoal) = HomeStats(
    streakDays = 6,
    todayMs = 12 * MINUTE_MS,
    todayPages = 11,
    goal = goal,
    week = weekProgress(emptyList(), goal, LocalDate.now()),
)

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HomePreview() {
    Reader343Theme {
        HomeScreen(
            state = LibraryUiState.Content(
                books = PreviewBooks,
                continueBook = PreviewBooks.first(),
                stats = previewStats(DailyGoal(GoalUnit.Minutes, 15)),
                filter = LibraryFilter.Reading,
            ),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            onSetGoal = {},
            onRetry = {},
            updateAvailable = true,
        )
    }
}

@Preview(showBackground = true, heightDp = 1000, locale = "ar")
@Composable
private fun HomeNoGoalRtlPreview() {
    Reader343Theme(darkTheme = false) {
        HomeScreen(
            state = LibraryUiState.Content(
                books = PreviewBooks,
                continueBook = PreviewBooks.first(),
                stats = previewStats(DailyGoal()),
                filter = LibraryFilter.Reading,
            ),
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            onSetGoal = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeEmptyPreview() {
    Reader343Theme {
        HomeScreen(
            state = LibraryUiState.Empty,
            importing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onImport = {},
            onOpenBook = {},
            onOpenMenu = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            onSetGoal = {},
            onRetry = {},
        )
    }
}
