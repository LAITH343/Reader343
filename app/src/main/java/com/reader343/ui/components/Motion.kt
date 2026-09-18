package com.reader343.ui.components

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 250

    fun fadeEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn(tween(MEDIUM_MS))

    fun fadeExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(tween(SHORT_MS))

    fun slideFromEdge(reduced: Boolean, fromTop: Boolean): EnterTransition =
        if (reduced) {
            EnterTransition.None
        } else {
            fadeIn(tween(MEDIUM_MS)) + slideInVertically(tween(MEDIUM_MS)) { if (fromTop) -it else it }
        }

    fun slideToEdge(reduced: Boolean, toTop: Boolean): ExitTransition =
        if (reduced) {
            ExitTransition.None
        } else {
            fadeOut(tween(SHORT_MS)) + slideOutVertically(tween(SHORT_MS)) { if (toTop) -it else it }
        }
}
