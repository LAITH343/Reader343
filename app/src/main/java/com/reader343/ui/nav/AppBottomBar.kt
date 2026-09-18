package com.reader343.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.components.Motion
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

enum class TopLevelTab(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Home(Routes.HOME, R.string.nav_home, R.drawable.ic_ph_house),
    Library(Routes.LIBRARY, R.string.nav_library, R.drawable.ic_ph_books),
    Stats(Routes.STATS, R.string.nav_stats, R.drawable.ic_ph_chart_bar),
    Settings(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_ph_gear_six),
}

@Composable
fun AppBottomBar(
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.nav)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 10.dp)
                .selectableGroup(),
        ) {
            TopLevelTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: TopLevelTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.button
    val spec = tween<Color>(if (reducedMotion()) 0 else Motion.MEDIUM_MS)
    val content by animateColorAsState(if (selected) colors.ink else colors.ink3, spec, label = "tabContent")
    val pill by animateColorAsState(if (selected) colors.accTint22 else Color.Transparent, spec, label = "tabPill")
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = TabHeight)
            .clip(shape)
            .focusRing(interactionSource, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 26.dp)
                .background(pill, MaterialTheme.appShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
        )
    }
}

private val TabHeight = 52.dp

@Preview
@Composable
private fun AppBottomBarPreview() {
    Reader343Theme {
        AppBottomBar(selected = TopLevelTab.Home, onSelect = {})
    }
}

@Preview(locale = "ar")
@Composable
private fun AppBottomBarLightRtlPreview() {
    Reader343Theme(darkTheme = false) {
        AppBottomBar(selected = TopLevelTab.Library, onSelect = {})
    }
}
