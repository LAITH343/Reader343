package com.reader343.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.spacing

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StateContent(icon = icon, title = title, body = body, action = action)
    }
}

@Composable
fun StateContent(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .widthIn(max = StateMaxWidth)
            .padding(MaterialTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.appColors.acc,
            modifier = Modifier.size(StateIconSize),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.ink3,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = MaterialTheme.spacing.sm)) { action() }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    EmptyState(
        icon = R.drawable.ic_ph_warning_circle,
        title = message,
        body = stringResource(R.string.state_error_hint),
        modifier = modifier,
        action = if (actionLabel != null && onAction != null) {
            { PrimaryButton(text = actionLabel, onClick = onAction) }
        } else {
            null
        },
    )
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.state_loading),
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = description },
            color = MaterialTheme.appColors.acc,
            trackColor = MaterialTheme.appColors.line2,
        )
    }
}

private val StateIconSize = 48.dp
private val StateMaxWidth = 420.dp
