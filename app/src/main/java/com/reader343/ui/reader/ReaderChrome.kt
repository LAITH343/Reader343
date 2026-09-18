package com.reader343.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.R
import com.reader343.domain.Bookmark
import com.reader343.domain.Chapter
import com.reader343.domain.NormRect
import com.reader343.domain.OutlineEntry
import com.reader343.domain.chapterAt
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.IconBadgeButton
import com.reader343.ui.components.IconButtonTone
import com.reader343.ui.components.Motion
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SegmentItem
import com.reader343.ui.components.SegmentedControl
import com.reader343.ui.components.SheetHeader
import com.reader343.ui.components.StateContent
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatRelative
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ReaderTopChrome(
    visible: Boolean,
    state: ReaderUiState.Ready,
    hasNotes: Boolean,
    zoomed: Boolean,
    onBack: () -> Unit,
    onShowNotes: () -> Unit,
    onToggleZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = reducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = Motion.slideFromEdge(reduced, fromTop = true),
        exit = Motion.slideToEdge(reduced, toTop = true),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ReaderTopBar(
                state = state,
                hasNotes = hasNotes,
                zoomed = zoomed,
                onBack = onBack,
                onShowNotes = onShowNotes,
                onToggleZoom = onToggleZoom,
            )
            state.session?.let { SessionPill(session = it, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderUiState.Ready,
    hasNotes: Boolean,
    zoomed: Boolean,
    onBack: () -> Unit,
    onShowNotes: () -> Unit,
    onToggleZoom: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val page = formatNumber(state.currentPage + 1)
    val subtitle = state.chapter?.let { stringResource(R.string.reader_subtitle_chapter, it.title, page) }
        ?: stringResource(R.string.reader_subtitle_page, page)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg.copy(alpha = TopChromeAlpha))
            .edgeLine(colors.line, top = false)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                text = state.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconBadgeButton(
            icon = R.drawable.ic_ph_note,
            contentDescription = stringResource(R.string.book_menu_notes),
            onClick = onShowNotes,
            tone = IconButtonTone.Plain,
            badge = hasNotes,
            badgePulse = false,
        )
        IconBadgeButton(
            icon = R.drawable.ic_ph_magnifying_glass_plus,
            contentDescription = stringResource(R.string.reader_zoom),
            onClick = onToggleZoom,
            tone = if (zoomed) IconButtonTone.Accent else IconButtonTone.Plain,
            modifier = Modifier.semantics { selected = zoomed },
        )
    }
}

@Composable
private fun SessionPill(session: SessionUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.pill
    val now by produceState(System.currentTimeMillis(), session.startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(SESSION_TICK_MS)
        }
    }
    val text = stringResource(
        R.string.reader_session,
        formatMinutes((now - session.startedAt).coerceAtLeast(0L)),
        pluralStringResource(R.plurals.reader_session_pages, session.pages, formatNumber(session.pages)),
    )
    Row(
        modifier = modifier
            .height(SessionPillHeight)
            .background(colors.surf, shape)
            .background(colors.accTint16, shape)
            .border(1.dp, colors.accLine, shape)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_timer_fill),
            contentDescription = null,
            tint = colors.accTx,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = colors.accTx,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ReaderBottomChrome(
    visible: Boolean,
    state: ReaderUiState.Ready,
    markup: MarkupState,
    actions: ReaderActions,
    markupActions: MarkupActions,
    onAddNoteFromSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = reducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = Motion.slideFromEdge(reduced, fromTop = false),
        exit = Motion.slideToEdge(reduced, toTop = false),
    ) {
        Column {
            val selection = markup.selection?.takeIf { markup.loupe == null }
            AnimatedVisibility(
                visible = selection != null,
                enter = Motion.slideFromEdge(reduced, fromTop = false),
                exit = Motion.fadeExit(reduced),
            ) {
                val current = remember { mutableStateOf(selection) }
                if (selection != null) current.value = selection
                current.value?.let { shown ->
                    SelectionToolbar(
                        selection = shown,
                        onHighlight = { color ->
                            markupActions.onColorSelected(color)
                            markupActions.onConfirmHighlight()
                        },
                        onNote = onAddNoteFromSelection,
                        onDismiss = markupActions::onDismissSelection,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }
            ReaderBottomBar(state = state, markup = markup, actions = actions)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState.Ready,
    markup: MarkupState,
    actions: ReaderActions,
) {
    val colors = MaterialTheme.appColors
    var preview by remember { mutableStateOf<Int?>(null) }
    val shownPage = preview ?: state.currentPage
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg.copy(alpha = BottomChromeAlpha))
            .edgeLine(colors.line, top = true)
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.reader_page_fraction,
                    formatNumber(shownPage + 1),
                    formatNumber(state.pageCount),
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = colors.ink2,
                maxLines = 1,
                modifier = Modifier.widthIn(min = PageLabelMinWidth),
            )
            PageSlider(
                page = state.currentPage,
                pageCount = state.pageCount,
                onPreview = {
                    preview = it
                    actions.onChromeInteraction()
                },
                onSeek = actions::onSeek,
                modifier = Modifier.weight(1f),
            )
            state.timeLeftMs?.let { left ->
                Text(
                    text = stringResource(R.string.reader_time_left, formatMinutes(left.coerceAtLeast(MIN_TIME_LEFT_MS))),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Normal,
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolTile(
                icon = R.drawable.ic_ph_highlighter,
                label = stringResource(R.string.reader_tool_highlight),
                checked = markup.highlighting,
                onClick = actions::onToggleHighlightMode,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = R.drawable.ic_ph_note_pencil,
                label = stringResource(R.string.reader_tool_note),
                onClick = actions::onAddPageNote,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = if (state.bookmarked) R.drawable.ic_ph_bookmark_simple_fill else R.drawable.ic_ph_bookmark_simple,
                label = stringResource(R.string.reader_tool_bookmark),
                checked = state.bookmarked,
                onClick = actions::onToggleBookmark,
                modifier = Modifier.weight(1f),
            )
            if (state.hasContents) {
                ToolTile(
                    icon = R.drawable.ic_ph_list_dashes,
                    label = stringResource(R.string.reader_tool_contents),
                    onClick = actions::onShowContents,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(
    page: Int,
    pageCount: Int,
    onPreview: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val last = (pageCount - 1).coerceAtLeast(1)
    val value = dragging ?: page.toFloat()
    val label = stringResource(R.string.reader_page_slider)
    val position = stringResource(
        R.string.reader_page_indicator,
        formatNumber(value.roundToInt() + 1),
        formatNumber(pageCount),
    )
    Slider(
        value = value,
        onValueChange = {
            dragging = it
            onPreview(it.roundToInt())
        },
        onValueChangeFinished = {
            dragging?.let { onSeek(it.roundToInt()) }
            dragging = null
            onPreview(null)
        },
        enabled = pageCount > 1,
        valueRange = 0f..last.toFloat(),
        steps = (pageCount - 2).coerceAtLeast(0),
        modifier = modifier.semantics {
            contentDescription = label
            stateDescription = position
        },
        thumb = { PageThumb() },
        track = { sliderState ->
            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
            val fraction = if (range <= 0f) 0f else (sliderState.value - sliderState.valueRange.start) / range
            PageTrack(fraction = fraction)
        },
    )
}

@Composable
private fun PageThumb() {
    val colors = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .size(ThumbSize + ThumbRing * 2)
            .background(colors.accTint22, CircleShape)
            .padding(ThumbRing)
            .background(colors.accTx, CircleShape),
    )
}

@Composable
private fun PageTrack(fraction: Float) {
    val colors = MaterialTheme.appColors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = colors.line2, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(
                color = colors.acc,
                topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun ToolTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.control
    val on = checked == true
    val container = if (on) colors.accTint18 else colors.surf
    val border = if (on) colors.accMid else colors.line
    val content = if (on) colors.ink else colors.ink2
    val interaction = if (checked != null) {
        val source = remember { MutableInteractionSource() }
        Modifier
            .clip(shape)
            .focusRing(source, shape)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = { onClick() },
            )
    } else {
        Modifier.appClickable(shape = shape, onClick = onClick)
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = ToolTileHeight)
            .background(container, shape)
            .border(1.dp, border, shape)
            .then(interaction)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SelectionToolbar(
    selection: SelectionUi,
    onHighlight: (Int) -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.stepper
    val quote = selection.text?.let { stringResource(R.string.selection_quote, it.trim()) }
        ?: stringResource(R.string.selection_area)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surf2, shape)
            .border(1.dp, colors.accLine, shape)
            .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = quote,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Normal,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconBadgeButton(
                icon = R.drawable.ic_ph_x,
                contentDescription = stringResource(R.string.selection_dismiss),
                onClick = onDismiss,
                tone = IconButtonTone.Plain,
            )
        }
        Row(
            modifier = Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.selectableGroup()) {
                HighlightColor.entries.forEach { color ->
                    InkSwatch(
                        color = Color(color.argb),
                        label = stringResource(R.string.highlight_with_color, stringResource(color.label)),
                        selected = color.argb == selection.color,
                        enabled = selection.canConfirm,
                        onClick = { onHighlight(color.argb) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = stringResource(R.string.reader_tool_note),
                onClick = onNote,
                icon = R.drawable.ic_ph_note_pencil,
                enabled = selection.canConfirm,
                compact = true,
                contentPadding = PaddingValues(horizontal = 12.dp),
            )
        }
    }
}

@Composable
internal fun InkSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .size(SwatchTouchSize)
            .appClickable(shape = CircleShape, enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SwatchSize)
                .background(color, CircleShape)
                .border(2.dp, if (selected) colors.ink else Color.Transparent, CircleShape),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentsSheet(
    state: ReaderUiState.Ready,
    onJump: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ContentsSheetContent(
            outline = state.outline,
            chapter = state.chapter,
            bookmarks = state.bookmarks,
            currentPage = state.currentPage,
            onJump = { page ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { onJump(page) }
            },
            onRemoveBookmark = onRemoveBookmark,
        )
    }
}

@Composable
private fun ContentsSheetContent(
    outline: List<OutlineEntry>,
    chapter: Chapter?,
    bookmarks: List<Bookmark>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
) {
    val hasOutline = outline.isNotEmpty()
    var tab by rememberSaveable { mutableIntStateOf(TAB_CONTENTS) }
    val showBookmarks = !hasOutline || tab == TAB_BOOKMARKS
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(if (hasOutline) R.string.contents_title else R.string.bookmarks_title))
        if (hasOutline) {
            SegmentedControl(
                items = listOf(
                    SegmentItem(stringResource(R.string.contents_title), R.drawable.ic_ph_list_dashes),
                    SegmentItem(stringResource(R.string.bookmarks_title), R.drawable.ic_ph_bookmark_simple),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (showBookmarks) {
            BookmarkList(
                bookmarks = bookmarks,
                currentPage = currentPage,
                onJump = onJump,
                onRemove = onRemoveBookmark,
            )
        } else {
            OutlineList(outline = outline, chapter = chapter, onJump = onJump)
        }
    }
}

@Composable
private fun OutlineList(
    outline: List<OutlineEntry>,
    chapter: Chapter?,
    onJump: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ((chapter?.index ?: 0) - LEAD_ITEMS).coerceAtLeast(0),
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
    ) {
        itemsIndexed(outline) { index, entry ->
            OutlineRow(entry = entry, current = chapter?.index == index, onClick = { onJump(entry.page) })
        }
    }
}

@Composable
private fun OutlineRow(entry: OutlineEntry, current: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.item
    val currentLabel = stringResource(R.string.contents_current)
    val openLabel = stringResource(R.string.note_open_page, entry.page + 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .background(if (current) colors.accTint16 else Color.Transparent, shape)
            .appClickable(shape = shape, onClickLabel = openLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = current
                if (current) stateDescription = currentLabel
            }
            .padding(
                start = 12.dp + DepthIndent * entry.depth.coerceAtMost(MAX_INDENT_DEPTH),
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current || entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                current -> colors.accTx
                entry.depth == 0 -> colors.ink
                else -> colors.ink2
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatNumber(entry.page + 1),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Normal,
            color = if (current) colors.accTx else colors.ink3,
        )
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        StateContent(
            icon = R.drawable.ic_ph_bookmark_simple,
            title = stringResource(R.string.bookmarks_empty),
            body = stringResource(R.string.bookmarks_empty_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
    ) {
        items(bookmarks, key = { it.id }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                current = bookmark.page == currentPage,
                onClick = { onJump(bookmark.page) },
                onRemove = { onRemove(bookmark.page) },
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    current: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.item
    val page = formatNumber(bookmark.page + 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .background(if (current) colors.accTint16 else Color.Transparent, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .appClickable(
                    shape = shape,
                    onClickLabel = stringResource(R.string.note_open_page, bookmark.page + 1),
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) { selected = current }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_bookmark_simple_fill),
                contentDescription = null,
                tint = colors.acc,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.reader_subtitle_page, page),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (current) colors.accTx else colors.ink,
                )
                Text(
                    text = formatRelative(bookmark.createdAt),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Normal,
                    color = colors.ink3,
                )
            }
        }
        IconBadgeButton(
            icon = R.drawable.ic_ph_x,
            contentDescription = stringResource(R.string.bookmark_remove, page),
            onClick = onRemove,
            tone = IconButtonTone.Plain,
        )
    }
}

private fun Modifier.edgeLine(color: Color, top: Boolean): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val y = if (top) stroke / 2f else size.height - stroke / 2f
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

private const val TopChromeAlpha = 0.92f
private const val BottomChromeAlpha = 0.94f
private const val SESSION_TICK_MS = 15_000L
private const val MIN_TIME_LEFT_MS = 60_000L
private const val TAB_CONTENTS = 0
private const val TAB_BOOKMARKS = 1
private const val LEAD_ITEMS = 2
private const val MAX_INDENT_DEPTH = 4
private val SessionPillHeight = 28.dp
private val PageLabelMinWidth = 54.dp
private val ToolTileHeight = 46.dp
private val TrackHeight = 4.dp
private val ThumbSize = 14.dp
private val ThumbRing = 4.dp
private val SwatchTouchSize = 44.dp
private val SwatchSize = 34.dp
private val RowMinHeight = 48.dp
private val DepthIndent = 16.dp

private val PreviewOutline = listOf(
    OutlineEntry("Preface", 0, 0),
    OutlineEntry("Chapter 1 · Reliability", 12, 0),
    OutlineEntry("Hardware faults", 18, 1),
    OutlineEntry("Software errors", 31, 1),
    OutlineEntry("Chapter 2 · Data models", 44, 0),
)

private val PreviewBookmarks = listOf(
    Bookmark(id = 1, page = 26, createdAt = System.currentTimeMillis() - 3_600_000L),
    Bookmark(id = 2, page = 40, createdAt = System.currentTimeMillis() - 86_400_000L),
)

@Preview(showBackground = true)
@Composable
private fun ContentsSheetPreview() {
    Reader343Theme {
        Column(Modifier.background(MaterialTheme.appColors.surf)) {
            ContentsSheetContent(
                outline = PreviewOutline,
                chapter = PreviewOutline.chapterAt(26, 60),
                bookmarks = PreviewBookmarks,
                currentPage = 26,
                onJump = {},
                onRemoveBookmark = {},
            )
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun BookmarksSheetRtlPreview() {
    Reader343Theme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.appColors.surf)) {
            ContentsSheetContent(
                outline = emptyList(),
                chapter = null,
                bookmarks = PreviewBookmarks,
                currentPage = 26,
                onJump = {},
                onRemoveBookmark = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectionToolbarPreview() {
    Reader343Theme {
        SelectionToolbar(
            selection = SelectionUi(
                page = 0,
                rects = listOf(NormRect(0.1f, 0.1f, 0.5f, 0.12f)),
                start = HandleMark(0.1f, 0.1f, 0.12f),
                end = HandleMark(0.5f, 0.1f, 0.12f),
                region = false,
                color = HighlightColor.Yellow.argb,
                text = "keep faults from turning into failures",
            ),
            onHighlight = {},
            onNote = {},
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
