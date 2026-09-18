package com.reader343.ui.notes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.reader343.domain.Highlight
import com.reader343.domain.Mark
import com.reader343.domain.MarkFilter
import com.reader343.domain.NormRect
import com.reader343.domain.Note
import com.reader343.domain.NoteAnchor
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.GhostButton
import com.reader343.ui.components.IconBadgeButton
import com.reader343.ui.components.IconButtonTone
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.SelectableSurface
import com.reader343.ui.components.StateContent
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatRelative
import com.reader343.ui.components.riseIn
import com.reader343.ui.reader.HighlightColor
import com.reader343.ui.reader.InkSwatch
import com.reader343.ui.reader.NoteSheet
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun NotesRoute(
    onBack: () -> Unit,
    onGoToPage: (Int) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotesScreen(
        state = state,
        actions = NotesActions(
            onBack = onBack,
            onGoToPage = onGoToPage,
            onFilter = viewModel::onFilter,
            onEditNote = viewModel::onEditNote,
            onChangeColor = viewModel::onChangeColor,
            onDelete = viewModel::onDelete,
        ),
    )
    (state as? NotesUiState.Content)?.editor?.let { editor ->
        NoteSheet(
            editor = editor,
            onSave = viewModel::onSaveNote,
            onDelete = viewModel::onDeleteEditedNote,
            onDismiss = viewModel::onDismissEditor,
        )
    }
}

class NotesActions(
    val onBack: () -> Unit,
    val onGoToPage: (Int) -> Unit,
    val onFilter: (MarkFilter) -> Unit,
    val onEditNote: (Mark) -> Unit,
    val onChangeColor: (Mark, Int) -> Unit,
    val onDelete: (Mark) -> Unit,
) {
    companion object {
        val None = NotesActions({}, {}, {}, {}, { _, _ -> }, {})
    }
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
    ) { padding ->
        when (state) {
            NotesUiState.Loading -> LoadingState(Modifier.padding(padding))
            NotesUiState.Error -> ErrorState(
                message = stringResource(R.string.notes_load_failed),
                actionLabel = stringResource(R.string.action_back),
                onAction = actions.onBack,
                modifier = Modifier.padding(padding),
            )
            is NotesUiState.Content -> {
                val visible = state.visible
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    item(key = "header") {
                        NotesHeader(state = state, onBack = actions.onBack)
                    }
                    item(key = "tabs") {
                        NotesTabs(
                            selected = state.filter,
                            total = state.marks.size,
                            onSelect = actions.onFilter,
                        )
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty_${state.filter}") {
                            NotesEmpty(
                                filter = state.filter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                        }
                    }
                    items(visible, key = { it.key }) { mark ->
                        MarkCard(
                            mark = mark,
                            actions = actions,
                            modifier = Modifier
                                .animateItem()
                                .padding(start = ScreenPadding, top = 12.dp, end = ScreenPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesHeader(state: NotesUiState.Content, onBack: () -> Unit) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 6.dp, end = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadgeButton(
            icon = R.drawable.ic_ph_arrow_left,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            tone = IconButtonTone.Plain,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = stringResource(R.string.notes_screen_title),
                style = MaterialTheme.appType.subScreenTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.notes_caption,
                    pluralStringResource(
                        R.plurals.notes_highlight_count,
                        state.highlightCount,
                        formatNumber(state.highlightCount),
                    ),
                    pluralStringResource(R.plurals.notes_note_count, state.noteCount, formatNumber(state.noteCount)),
                    state.title,
                ),
                style = MaterialTheme.appType.caption,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotesTabs(
    selected: MarkFilter,
    total: Int,
    onSelect: (MarkFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding, bottom = 2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkFilter.entries.forEach { filter ->
            val label = when (filter) {
                MarkFilter.All -> stringResource(R.string.notes_tab_all, formatNumber(total))
                MarkFilter.Highlights -> stringResource(R.string.notes_tab_highlights)
                MarkFilter.Notes -> stringResource(R.string.notes_tab_notes)
            }
            SelectableSurface(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                shape = MaterialTheme.appShapes.item,
                minHeight = TabHeight,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotesEmpty(filter: MarkFilter, modifier: Modifier = Modifier) {
    val (icon, title, body) = when (filter) {
        MarkFilter.All -> Triple(R.drawable.ic_ph_note, R.string.notes_empty_all, R.string.notes_empty_all_hint)
        MarkFilter.Highlights -> Triple(
            R.drawable.ic_ph_highlighter,
            R.string.notes_empty_highlights,
            R.string.notes_empty_highlights_hint,
        )
        MarkFilter.Notes -> Triple(R.drawable.ic_ph_note_pencil, R.string.notes_empty, R.string.notes_empty_hint)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        StateContent(icon = icon, title = stringResource(title), body = stringResource(body))
    }
}

@Composable
private fun MarkCard(
    mark: Mark,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.listCard
    val ink = mark.color?.let { Color(it) } ?: colors.acc
    val page = mark.page + 1
    Row(
        modifier = modifier
            .riseIn()
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surf, shape)
            .border(1.dp, colors.line, shape),
    ) {
        Box(
            modifier = Modifier
                .width(InkBarWidth)
                .fillMaxHeight()
                .background(ink),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageChip(
                        page = page,
                        icon = if (mark.highlight != null) R.drawable.ic_ph_highlighter else R.drawable.ic_ph_note_pencil,
                    )
                    Text(
                        text = formatMarkTime(mark.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MarkMenu(mark = mark, actions = actions, modifier = Modifier.offset(x = 10.dp))
            }
            mark.quote?.let { quote ->
                Text(
                    text = stringResource(R.string.selection_quote, quote.trim()),
                    style = MaterialTheme.appType.quote,
                    color = colors.ink,
                )
            }
            mark.note?.let { note -> AttachedNote(body = note.body) }
            GhostButton(
                text = stringResource(R.string.note_open_page, page),
                onClick = { actions.onGoToPage(mark.page) },
                icon = R.drawable.ic_ph_arrow_u_up_left,
                modifier = Modifier.offset(x = (-10).dp),
            )
        }
    }
}

@Composable
private fun PageChip(page: Int, @DrawableRes icon: Int) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .height(PageChipHeight)
            .background(colors.surf2, MaterialTheme.appShapes.pill)
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.ink2,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.note_page, page),
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink2,
            maxLines = 1,
        )
    }
}

@Composable
private fun AttachedNote(body: String) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surf2, MaterialTheme.appShapes.item)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_note_pencil),
            contentDescription = null,
            tint = colors.accLt,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = colors.ink2,
        )
    }
}

@Composable
private fun MarkMenu(
    mark: Mark,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    var expanded by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val itemColors = MenuDefaults.itemColors(
        textColor = colors.ink,
        leadingIconColor = colors.ink2,
    )

    fun close() {
        expanded = false
        picking = false
    }

    Box(modifier = modifier) {
        IconBadgeButton(
            icon = R.drawable.ic_ph_dots_three,
            contentDescription = stringResource(R.string.notes_options, mark.page + 1),
            onClick = { expanded = true },
            tone = IconButtonTone.Plain,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = ::close,
            shape = MaterialTheme.appShapes.stepper,
            containerColor = colors.surf2,
            border = BorderStroke(1.dp, colors.line2),
            tonalElevation = 0.dp,
        ) {
            val highlight = mark.highlight
            if (picking && highlight != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .selectableGroup(),
                ) {
                    HighlightColor.entries.forEach { color ->
                        InkSwatch(
                            color = Color(color.argb),
                            label = stringResource(color.label),
                            selected = color.argb == highlight.color,
                            enabled = true,
                            onClick = {
                                close()
                                actions.onChangeColor(mark, color.argb)
                            },
                        )
                    }
                }
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(if (mark.note != null) R.string.notes_edit_note else R.string.note_add)) },
                    leadingIcon = { MenuIcon(R.drawable.ic_ph_note_pencil) },
                    onClick = {
                        close()
                        actions.onEditNote(mark)
                    },
                    colors = itemColors,
                )
                if (highlight != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_change_color)) },
                        leadingIcon = { MenuIcon(R.drawable.ic_ph_highlighter) },
                        onClick = { picking = true },
                        colors = itemColors,
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notes_delete)) },
                    leadingIcon = { MenuIcon(R.drawable.ic_ph_trash) },
                    onClick = {
                        close()
                        actions.onDelete(mark)
                    },
                    colors = MenuDefaults.itemColors(textColor = colors.danger, leadingIconColor = colors.danger),
                )
            }
        }
    }
}

@Composable
private fun MenuIcon(@DrawableRes icon: Int) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
}

@Composable
private fun formatMarkTime(time: Long): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(time)
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(
            R.string.notes_today_at,
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(instant.atZone(zone)),
        )
        today.minusDays(1) -> stringResource(R.string.notes_yesterday)
        else -> formatRelative(time)
    }
}

private val ScreenPadding = 20.dp
private val TabHeight = 40.dp
private val InkBarWidth = 4.dp
private val PageChipHeight = 24.dp

private val PreviewRect = NormRect(0f, 0f, 1f, 0.1f)

private fun previewMark(id: Long, page: Int, ago: Long, color: HighlightColor?, quote: String, note: String?): Mark {
    val now = System.currentTimeMillis()
    val highlight = color?.let {
        Highlight(id, page, listOf(PreviewRect), it.argb, null, null, quote, now - ago)
    }
    val attached = note?.let {
        Note(id, page, NoteAnchor(PreviewRect, highlight?.id, quote), it, now - ago)
    }
    return Mark(page = page, highlight = highlight, note = attached)
}

private val PreviewMarks = listOf(
    previewMark(
        1, 26, 20 * 60_000L, HighlightColor.Yellow,
        "Because the probability of a fault can never be driven to zero, the useful goal is to design mechanisms that keep faults from turning into failures.",
        "This is the whole thesis of chapter 1.",
    ),
    previewMark(2, 23, 35 * 60_000L, HighlightColor.Blue, "A fault describes one component drifting from its specification.", null),
    previewMark(
        3, 18, 26 * 3_600_000L, null,
        "Reliability, scalability and maintainability recur in every system.",
        "Map these three onto our own service checklist.",
    ),
)

private val PreviewState = NotesUiState.Content(
    title = "Designing Data-Intensive Applications",
    marks = PreviewMarks,
    filter = MarkFilter.All,
)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun NotesScreenPreview() {
    Reader343Theme {
        NotesScreen(state = PreviewState, actions = NotesActions.None)
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun NotesScreenLightPreview() {
    Reader343Theme(darkTheme = false) {
        NotesScreen(state = PreviewState, actions = NotesActions.None)
    }
}

@Preview(showBackground = true, heightDp = 780, locale = "ar")
@Composable
private fun NotesScreenRtlEmptyPreview() {
    Reader343Theme {
        NotesScreen(state = PreviewState.copy(marks = emptyList(), filter = MarkFilter.Highlights), actions = NotesActions.None)
    }
}
