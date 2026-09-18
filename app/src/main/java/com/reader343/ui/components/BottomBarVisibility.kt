package com.reader343.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class BottomBarVisibility {
    private var overlays by mutableIntStateOf(0)

    val hidden: Boolean get() = overlays > 0

    internal fun push() {
        overlays++
    }

    internal fun pop() {
        overlays = (overlays - 1).coerceAtLeast(0)
    }
}

val LocalBottomBarVisibility = staticCompositionLocalOf<BottomBarVisibility?> { null }

@Composable
fun HideBottomBarEffect() {
    val visibility = LocalBottomBarVisibility.current ?: return
    DisposableEffect(visibility) {
        visibility.push()
        onDispose { visibility.pop() }
    }
}
