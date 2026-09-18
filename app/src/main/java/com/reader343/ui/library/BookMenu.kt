package com.reader343.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.BookCover
import com.reader343.ui.components.DestructiveTextButton
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.bidiWrap
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import kotlinx.coroutines.launch

class BookMenuActions(
    val onResume: (Long) -> Unit,
    val onOpenNotes: (Long) -> Unit,
    val onSetFinished: (Long, Boolean) -> Unit,
    val onResetProgress: (Long) -> Unit,
    val onRemove: (Long) -> Unit,
)

fun LibraryViewModel.bookMenuActions(
    onOpenBook: (Long) -> Unit,
    onOpenNotes: (Long) -> Unit,
) = BookMenuActions(
    onResume = onOpenBook,
    onOpenNotes = onOpenNotes,
    onSetFinished = ::setFinished,
    onResetProgress = ::resetProgress,
    onRemove = ::deleteBook,
)

private enum class BookConfirm { Reset, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMenuHost(
    books: List<BookWithProgress>,
    menuBookId: Long?,
    onMenuBookChange: (Long?) -> Unit,
    actions: BookMenuActions,
) {
    var confirm by rememberSaveable { mutableStateOf<BookConfirm?>(null) }
    var confirmBookId by rememberSaveable { mutableStateOf<Long?>(null) }

    val menuBook = books.firstOrNull { it.id == menuBookId }
    if (menuBook != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val closeThen: (() -> Unit) -> Unit = { action ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                onMenuBookChange(null)
                action()
            }
        }
        AppBottomSheet(
            onDismissRequest = { onMenuBookChange(null) },
            sheetState = sheetState,
        ) {
            BookMenuContent(
                book = menuBook,
                onResume = { closeThen { actions.onResume(menuBook.id) } },
                onOpenNotes = { closeThen { actions.onOpenNotes(menuBook.id) } },
                onToggleFinished = { closeThen { actions.onSetFinished(menuBook.id, !menuBook.finished) } },
                onReset = {
                    closeThen {
                        confirmBookId = menuBook.id
                        confirm = BookConfirm.Reset
                    }
                },
                onRemove = {
                    closeThen {
                        confirmBookId = menuBook.id
                        confirm = BookConfirm.Remove
                    }
                },
            )
        }
    }

    val confirmBook = books.firstOrNull { it.id == confirmBookId }
    val dismissConfirm = {
        confirm = null
        confirmBookId = null
    }
    if (confirmBook != null) {
        when (confirm) {
            BookConfirm.Reset -> ResetProgressDialog(
                title = confirmBook.title,
                onConfirm = {
                    actions.onResetProgress(confirmBook.id)
                    dismissConfirm()
                },
                onDismiss = dismissConfirm,
            )
            BookConfirm.Remove -> DeleteBookDialog(
                title = confirmBook.title,
                onConfirm = {
                    actions.onRemove(confirmBook.id)
                    dismissConfirm()
                },
                onDismiss = dismissConfirm,
            )
            null -> Unit
        }
    }
}

@Composable
private fun BookMenuContent(
    book: BookWithProgress,
    onResume: () -> Unit,
    onOpenNotes: () -> Unit,
    onToggleFinished: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 14.dp)
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                shape = MaterialTheme.appShapes.cover,
                modifier = Modifier.size(width = 48.dp, height = 66.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(lineHeight = 19.5.sp),
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.chapterTitle?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.book_menu_meta,
                        formatPercent(book.percent),
                        formatNumber(book.lastPage + 1),
                        formatNumber(book.pageCount),
                        pluralStringResource(R.plurals.book_menu_highlights, book.highlightCount, formatNumber(book.highlightCount)),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.ink3,
                )
            }
        }
        MenuItem(
            icon = R.drawable.ic_ph_play,
            text = if (book.started) {
                stringResource(R.string.home_resume_at, formatNumber(book.lastPage + 1))
            } else {
                stringResource(R.string.home_start_reading)
            },
            onClick = onResume,
        )
        MenuItem(icon = R.drawable.ic_ph_note, text = stringResource(R.string.book_menu_notes), onClick = onOpenNotes)
        MenuItem(
            icon = R.drawable.ic_ph_check_circle,
            text = stringResource(if (book.finished) R.string.book_menu_mark_unread else R.string.book_menu_mark_finished),
            onClick = onToggleFinished,
        )
        MenuItem(
            icon = R.drawable.ic_ph_arrow_counter_clockwise,
            text = stringResource(R.string.book_menu_reset),
            onClick = onReset,
        )
        MenuItem(
            icon = R.drawable.ic_ph_trash,
            text = stringResource(R.string.book_menu_remove),
            onClick = onRemove,
            color = colors.danger,
        )
    }
}

@Composable
private fun MenuItem(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.appColors.ink,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .appClickable(shape = MaterialTheme.appShapes.button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
fun DeleteBookDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_ph_trash), contentDescription = null) },
        title = { Text(stringResource(R.string.delete_book_title)) },
        text = { Text(stringResource(R.string.delete_book_message, bidiWrap(title))) },
        confirmButton = {
            DestructiveTextButton(text = stringResource(R.string.action_delete), onClick = onConfirm)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ResetProgressDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_ph_arrow_counter_clockwise), contentDescription = null) },
        title = { Text(stringResource(R.string.reset_progress_title)) },
        text = { Text(stringResource(R.string.reset_progress_message, bidiWrap(title))) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_reset)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BookMenuPreview() {
    Reader343Theme {
        BookMenuContent(
            book = PreviewBooks.first(),
            onResume = {},
            onOpenNotes = {},
            onToggleFinished = {},
            onReset = {},
            onRemove = {},
        )
    }
}
