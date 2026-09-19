package com.reader343.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.res.painterResource
import com.reader343.domain.BookInfo
import com.reader343.domain.MetadataStatus
import com.reader343.ui.components.appClickable
import com.reader343.ui.metadata.authorLabel
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.R
import com.reader343.domain.BookStatus
import com.reader343.domain.BookWithProgress
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.BookCover
import com.reader343.ui.components.IconBadgeButton
import com.reader343.ui.components.IconButtonTone
import com.reader343.ui.components.Pill
import com.reader343.ui.components.PillTone
import com.reader343.ui.components.ProgressBar
import com.reader343.ui.components.SmallPillHeight
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.components.formatRelative
import com.reader343.ui.components.riseIn
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryBookRow(
    book: BookWithProgress,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onReview: () -> Unit = {},
) {
    val colors = MaterialTheme.appColors
    val haptics = LocalHapticFeedback.current
    val menuLabel = stringResource(R.string.action_more)
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .riseIn()
            .semantics { customActions = listOf(CustomAccessibilityAction(menuLabel) { onMenu(); true }) },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.library_open_book),
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMenu()
        },
        onLongClickLabel = menuLabel,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                modifier = Modifier.size(width = 62.dp, height = 86.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 19.5.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        authorLabel(book)?.let { label ->
                            Text(
                                text = label.text,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = if (label.muted) colors.ink3 else colors.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(
                            text = bookMeta(book),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    IconBadgeButton(
                        icon = R.drawable.ic_ph_dots_three_vertical,
                        contentDescription = menuLabel,
                        onClick = onMenu,
                        tone = IconButtonTone.Plain,
                        modifier = Modifier.offset(x = 8.dp, y = (-8).dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgressBar(
                        progress = book.percent,
                        height = 5.dp,
                        color = statusBarColor(book.status),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatPercent(book.percent),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = colors.ink2,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (book.needsReview) ReviewPill(onClick = onReview)
                    StatusPill(book.status)
                    Pill(
                        text = if (book.marks > 0) {
                            pluralStringResource(R.plurals.library_marks, book.marks, formatNumber(book.marks))
                        } else {
                            stringResource(R.string.library_no_marks)
                        },
                        tone = PillTone.Muted,
                        icon = R.drawable.ic_ph_highlighter,
                        height = SmallPillHeight,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfBook(
    book: BookWithProgress,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val haptics = LocalHapticFeedback.current
    val menuLabel = stringResource(R.string.action_more)
    val shape = MaterialTheme.appShapes.item
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(ShelfCoverWidth)
            .focusRing(interactionSource, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClickLabel = stringResource(R.string.library_open_book),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenu()
                },
                onLongClickLabel = menuLabel,
                onClick = onOpen,
            )
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(menuLabel) { onMenu(); true })
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            shape = shape,
            modifier = Modifier.size(width = ShelfCoverWidth, height = 148.dp),
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressBar(progress = book.percent, height = 4.dp, modifier = Modifier.weight(1f))
            Text(
                text = formatPercent(book.percent),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun ReviewPill(onClick: () -> Unit) {
    val amber = MaterialTheme.appColors.amber
    val shape = MaterialTheme.appShapes.pill
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(SmallPillHeight)
            .background(amber.fill, shape)
            .border(1.dp, amber.border, shape)
            .appClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_seal_question),
            contentDescription = null,
            tint = amber.text,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(R.string.metadata_needs_review),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = amber.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(status: BookStatus) {
    when (status) {
        BookStatus.Reading -> Pill(
            text = stringResource(R.string.library_status_reading),
            tone = PillTone.Accent,
            height = SmallPillHeight,
        )
        BookStatus.AlmostDone -> Pill(
            text = stringResource(R.string.library_status_almost_done),
            tone = PillTone.Amber,
            height = SmallPillHeight,
        )
        BookStatus.NotStarted -> Pill(
            text = stringResource(R.string.library_not_started),
            tone = PillTone.Neutral,
            height = SmallPillHeight,
        )
        BookStatus.Finished -> Pill(
            text = stringResource(R.string.library_status_finished),
            tone = PillTone.Accent,
            icon = R.drawable.ic_ph_check,
            height = SmallPillHeight,
        )
    }
}

@Composable
private fun statusBarColor(status: BookStatus): Color {
    val colors = MaterialTheme.appColors
    return when (status) {
        BookStatus.AlmostDone -> colors.amber.bar
        BookStatus.NotStarted -> colors.handle
        BookStatus.Reading, BookStatus.Finished -> colors.acc
    }
}

@Composable
private fun bookMeta(book: BookWithProgress): String = stringResource(
    R.string.library_book_meta,
    pluralStringResource(R.plurals.library_pages, book.pageCount, formatNumber(book.pageCount)),
    book.lastReadAt?.let { formatRelative(it) } ?: stringResource(R.string.library_meta_not_started),
)

private val ShelfCoverWidth = 108.dp

internal val PreviewBooks = listOf(
    BookWithProgress(
        1, "Designing Data-Intensive Applications", null, 491, 26, 0.05f, System.currentTimeMillis() - 3_600_000,
        highlightCount = 12, noteCount = 2, bookmarkCount = 1,
        chapterTitle = "Chapter 1 · Reliability", chapterEndPage = 40, msPerPage = 90_000L,
    ),
    BookWithProgress(
        2, "The Rust Programming Language", null, 560, 268, 0.48f, System.currentTimeMillis() - 86_400_000,
        highlightCount = 31, metadata = BookInfo(author = "Steve Klabnik & Carol Nichols", status = MetadataStatus.Applied),
    ),
    BookWithProgress(
        3, "Crafting Interpreters", null, 640, 486, 0.76f, System.currentTimeMillis() - 259_200_000,
        highlightCount = 40, noteCount = 12, metadata = BookInfo(status = MetadataStatus.Review),
    ),
    BookWithProgress(4, "Operating Systems: Three Easy Pieces", null, 714, 0, 0f, null),
    BookWithProgress(5, "The Pragmatic Programmer", null, 352, 351, 1f, System.currentTimeMillis() - 604_800_000, finishedAt = 1L, highlightCount = 8),
)

@Preview(showBackground = true)
@Composable
private fun LibraryBookRowPreview() {
    Reader343Theme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            PreviewBooks.forEach { LibraryBookRow(book = it, onOpen = {}, onMenu = {}) }
        }
    }
}
