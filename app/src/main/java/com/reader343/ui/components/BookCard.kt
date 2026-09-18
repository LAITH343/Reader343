package com.reader343.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.reader343.R
import com.reader343.domain.BookWithProgress
import com.reader343.ui.theme.spacing
import java.io.File

@Composable
fun BookCard(
    book: BookWithProgress,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val removeLabel = stringResource(R.string.action_delete)
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
            },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.library_open_book),
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onRemove()
        },
        onLongClickLabel = removeLabel,
    ) {
        BookCover(title = book.title, coverPath = book.coverPath)
        Column(
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.md,
                end = MaterialTheme.spacing.xs,
                bottom = MaterialTheme.spacing.md,
            ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = MaterialTheme.spacing.md),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_dots_three_vertical),
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(removeLabel, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ph_trash),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(end = MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                BookProgress(percent = book.percent)
                Text(
                    text = stringResource(
                        R.string.library_book_meta,
                        book.lastReadAt?.let { formatRelative(it) } ?: stringResource(R.string.library_not_started),
                        pluralStringResource(R.plurals.library_pages, book.pageCount, formatNumber(book.pageCount)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
) {
    val coverModifier = modifier
        .fillMaxWidth()
        .aspectRatio(COVER_ASPECT)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = stringResource(R.string.library_cover_description, title),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = coverModifier,
        )
    } else {
        Box(coverModifier, contentAlignment = Alignment.Center) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val COVER_ASPECT = 0.75f
