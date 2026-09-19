package com.reader343.ui.metadata

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.reader343.R
import com.reader343.domain.BookMetadata
import com.reader343.domain.MetadataProvider
import com.reader343.domain.RankedCandidate
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.formatNumber
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import java.io.File

@Composable
fun MetadataPickerHost(
    viewModel: MetadataPickerViewModel,
    onMessage: (String) -> Unit = {},
) {
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            onMessage(
                resources.getString(
                    when (event) {
                        MetadataEvent.Applied -> R.string.metadata_applied
                        MetadataEvent.ApplyFailed -> R.string.metadata_apply_failed
                    },
                ),
            )
        }
    }

    picker?.let { state ->
        MetadataPickerSheet(
            state = state,
            actions = PickerActions(
                onSelect = viewModel::select,
                onUse = viewModel::useSelected,
                onDismiss = viewModel::dismissPicker,
                onRetry = viewModel::retry,
                onEditTitle = { viewModel.startEdit(state.bookId, searchAfterSave = true) },
            ),
        )
    }

    edit?.let { target ->
        EditBookDialog(
            initialTitle = target.title,
            initialAuthor = target.author.orEmpty(),
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

@Composable
fun rememberMetadataPicker(): MetadataPickerViewModel = hiltViewModel()

class PickerActions(
    val onSelect: (Int) -> Unit,
    val onUse: () -> Unit,
    val onDismiss: () -> Unit,
    val onRetry: () -> Unit,
    val onEditTitle: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataPickerSheet(
    state: PickerState,
    actions: PickerActions,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = actions.onDismiss, sheetState = sheetState) {
        PickerContent(state = state, actions = actions)
    }
}

@Composable
private fun PickerContent(
    state: PickerState,
    actions: PickerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            is PickerState.Loading -> LoadingContent()
            is PickerState.Results -> ResultsContent(state, actions)
            is PickerState.Empty -> MessageContent(
                icon = R.drawable.ic_ph_magnifying_glass_minus,
                accent = false,
                title = stringResource(R.string.metadata_no_match_title),
                body = stringResource(R.string.metadata_no_match_body),
                secondary = stringResource(R.string.metadata_edit_title),
                onSecondary = actions.onEditTitle,
                primary = stringResource(R.string.metadata_search_again),
                primaryIcon = R.drawable.ic_ph_magnifying_glass,
                onPrimary = actions.onRetry,
            )
            is PickerState.Offline -> MessageContent(
                icon = R.drawable.ic_ph_wifi_slash,
                accent = true,
                title = stringResource(R.string.metadata_offline_title),
                body = stringResource(R.string.metadata_offline_body),
                secondary = stringResource(R.string.metadata_later),
                onSecondary = actions.onDismiss,
                primary = stringResource(R.string.action_retry),
                primaryIcon = R.drawable.ic_ph_arrow_clockwise,
                onPrimary = actions.onRetry,
            )
            is PickerState.Failed -> MessageContent(
                icon = R.drawable.ic_ph_cloud_arrow_down,
                accent = true,
                title = stringResource(R.string.metadata_failed_title),
                body = stringResource(R.string.metadata_failed_body),
                secondary = stringResource(R.string.metadata_later),
                onSecondary = actions.onDismiss,
                primary = stringResource(R.string.action_retry),
                primaryIcon = R.drawable.ic_ph_arrow_clockwise,
                onPrimary = actions.onRetry,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.appColors.acc,
            trackColor = MaterialTheme.appColors.line2,
        )
        Text(
            text = stringResource(R.string.metadata_looking_up),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.ink2,
        )
    }
}

@Composable
private fun ResultsContent(
    state: PickerState.Results,
    actions: PickerActions,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(R.string.metadata_picker_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        Text(
            text = pluralStringResource(
                R.plurals.metadata_picker_subtitle,
                state.candidates.size,
                formatNumber(state.candidates.size),
                state.bookTitle,
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.ink3,
        )
    }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.candidates.forEachIndexed { index, candidate ->
            CandidateRow(
                candidate = candidate,
                selected = index == state.selected,
                enabled = !state.applying,
                onClick = { actions.onSelect(index) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
            text = stringResource(R.string.metadata_keep_current),
            onClick = actions.onDismiss,
            enabled = !state.applying,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(if (state.applying) R.string.metadata_looking_up else R.string.metadata_use_this),
            onClick = actions.onUse,
            icon = if (state.applying) R.drawable.ic_ph_circle_notch else R.drawable.ic_ph_check,
            enabled = !state.applying,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: PickerCandidate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.stepper
    val metadata = candidate.ranked.metadata
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accTint14 else colors.surf2, shape)
            .border(1.dp, if (selected) colors.accMid else colors.line2, shape)
            .focusRing(interactionSource, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CandidateCover(candidate.coverPath)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = colors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            candidateByline(metadata)?.let { byline ->
                Text(
                    text = byline,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            candidateFacts(metadata)?.let { facts ->
                Text(
                    text = facts,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (candidate.ranked.closest) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .height(20.dp)
                        .background(colors.accTint18, MaterialTheme.appShapes.pill)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.metadata_closest_match).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.06.em),
                        fontWeight = FontWeight.Bold,
                        color = colors.accTx,
                    )
                }
            }
        }
        RadioDot(selected)
    }
}

@Composable
private fun CandidateCover(path: String?) {
    val colors = MaterialTheme.appColors
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 62.dp)
            .clip(shape)
            .background(if (path != null) colors.line2 else colors.surf2, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_ph_image_square),
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) colors.acc else colors.handle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (selected) colors.acc else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun candidateByline(metadata: BookMetadata): String? {
    val parts = listOfNotNull(metadata.author, metadata.publishedYear?.toString())
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun candidateFacts(metadata: BookMetadata): String? {
    val pages = metadata.pageCount?.let {
        pluralStringResource(R.plurals.library_pages, it, formatNumber(it))
    }
    val parts = listOfNotNull(metadata.publisher, pages)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun MessageContent(
    @DrawableRes icon: Int,
    accent: Boolean,
    title: String,
    body: String,
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    @DrawableRes primaryIcon: Int,
    onPrimary: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val tileShape = RoundedCornerShape(21.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(if (accent) colors.accTint14 else colors.surf2, tileShape)
                .border(1.dp, if (accent) colors.accLine else colors.line2, tileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (accent) colors.accTx else colors.ink2,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = colors.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 270.dp),
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(text = secondary, onClick = onSecondary, modifier = Modifier.weight(1f))
            PrimaryButton(text = primary, onClick = onPrimary, icon = primaryIcon, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun EditBookDialog(
    initialTitle: String,
    initialAuthor: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var author by rememberSaveable { mutableStateOf(initialAuthor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_ph_pencil_simple), contentDescription = null) },
        title = { Text(stringResource(R.string.edit_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_book_field_title)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.edit_book_field_author)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, author) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.appColors.surf,
    )
}

private val PreviewCandidates = listOf(
    PickerCandidate(
        RankedCandidate(
            BookMetadata(
                title = "Crafting Interpreters",
                authors = listOf("Robert Nystrom"),
                publishedYear = 2021,
                publisher = "Genever Benning",
                pageCount = 640,
                provider = MetadataProvider.OpenLibrary,
            ),
            score = 1f,
            closest = true,
        ),
    ),
    PickerCandidate(
        RankedCandidate(
            BookMetadata(
                title = "Crafting Interpreters (draft edition)",
                authors = listOf("Robert Nystrom"),
                publishedYear = 2015,
                publisher = "Self-published",
                pageCount = 512,
                provider = MetadataProvider.OpenLibrary,
            ),
            score = 0.67f,
            closest = false,
        ),
    ),
    PickerCandidate(
        RankedCandidate(
            BookMetadata(
                title = "The Craft of Interpretation",
                authors = listOf("R. J. Nystrom"),
                publishedYear = 1998,
                publisher = "Academic Press",
                pageCount = 288,
                provider = MetadataProvider.GoogleBooks,
            ),
            score = 0.4f,
            closest = false,
        ),
    ),
)

private val PreviewActions = PickerActions({}, {}, {}, {}, {})

@Composable
private fun PreviewSheet(content: @Composable () -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.appColors.surf).padding(top = 16.dp)) { content() }
}

@Preview(name = "Results dark", showBackground = true)
@Composable
private fun PickerResultsPreview() {
    Reader343Theme(darkTheme = true) {
        PreviewSheet {
            PickerContent(
                state = PickerState.Results(1, "crafting-interpreters", PreviewCandidates),
                actions = PreviewActions,
            )
        }
    }
}

@Preview(name = "Results light RTL", showBackground = true, locale = "ar")
@Composable
private fun PickerResultsRtlPreview() {
    Reader343Theme(darkTheme = false) {
        PreviewSheet {
            PickerContent(
                state = PickerState.Results(1, "crafting-interpreters", PreviewCandidates, selected = 1),
                actions = PreviewActions,
            )
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun PickerEmptyPreview() {
    Reader343Theme(darkTheme = true) {
        PreviewSheet { PickerContent(state = PickerState.Empty(1), actions = PreviewActions) }
    }
}

@Preview(name = "Offline light", showBackground = true)
@Composable
private fun PickerOfflinePreview() {
    Reader343Theme(darkTheme = false) {
        PreviewSheet { PickerContent(state = PickerState.Offline(1), actions = PreviewActions) }
    }
}

@Preview(name = "Edit dialog", showBackground = true)
@Composable
private fun EditBookDialogPreview() {
    Reader343Theme(darkTheme = true) {
        EditBookDialog(initialTitle = "Crafting Interpreters", initialAuthor = "", onSave = { _, _ -> }, onDismiss = {})
    }
}
