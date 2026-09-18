package com.reader343.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.IconTextButton
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.QuoteBlock
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.SheetHeader
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    editor: NoteEditor,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var body by rememberSaveable(editor.key) { mutableStateOf(editor.body) }
    val isNew = editor.noteId == null
    val canSave = body.isNotBlank() && body.trim() != editor.body
    val colors = MaterialTheme.appColors

    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    LaunchedEffect(editor.key) {
        if (isNew) focusRequester.requestFocus()
    }

    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(
            title = stringResource(if (isNew) R.string.note_new else R.string.note_title),
            trailing = stringResource(R.string.note_page, editor.page + 1),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            editor.anchor.snippet?.let { snippet ->
                QuoteBlock(text = snippet, maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = MaterialTheme.appShapes.button,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surf2,
                    unfocusedContainerColor = colors.surf2,
                    focusedBorderColor = colors.accMid,
                    unfocusedBorderColor = colors.line2,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    focusedPlaceholderColor = colors.ink3,
                    unfocusedPlaceholderColor = colors.ink3,
                    cursorColor = colors.acc,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NoteFieldMinHeight)
                    .focusRequester(focusRequester),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isNew) {
                    IconTextButton(
                        icon = R.drawable.ic_ph_trash,
                        text = stringResource(R.string.note_delete),
                        onClick = { hideThen(onDelete) },
                        destructive = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { hideThen(onDismiss) },
                )
                PrimaryButton(
                    text = stringResource(R.string.note_save),
                    onClick = { hideThen { onSave(body) } },
                    enabled = canSave,
                )
            }
        }
    }
}

private val NoteFieldMinHeight = 120.dp
