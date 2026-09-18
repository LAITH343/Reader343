package com.reader343.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.appColors.bg,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.appColors
    TopAppBar(
        title = {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    text = title,
                    style = MaterialTheme.appType.subScreenTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.appType.caption,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconBadgeButton(
                    icon = R.drawable.ic_ph_arrow_left,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    tone = IconButtonTone.Plain,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = colors.surf0,
            titleContentColor = colors.ink,
            navigationIconContentColor = colors.ink,
            actionIconContentColor = colors.ink2,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun TopBarAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    IconBadgeButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tone = IconButtonTone.Plain,
        badge = badge,
    )
}
