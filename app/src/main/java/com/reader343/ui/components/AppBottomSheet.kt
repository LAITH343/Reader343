package com.reader343.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.appColors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.sheetTopBorder(colors.accLine, SheetRadius),
        sheetState = sheetState,
        shape = MaterialTheme.appShapes.sheet,
        containerColor = colors.surf,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
        content = content,
    )
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(MaterialTheme.appColors.accLine, MaterialTheme.appShapes.pill),
        )
    }
}

fun Modifier.sheetTopBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val r = radius.toPx()
    val half = stroke / 2f
    val path = Path().apply {
        moveTo(half, r)
        arcTo(Rect(half, half, half + r * 2, half + r * 2), 180f, 90f, false)
        lineTo(size.width - r - half, half)
        arcTo(Rect(size.width - half - r * 2, half, size.width - half, half + r * 2), 270f, 90f, false)
    }
    drawPath(path, color, style = Stroke(width = stroke))
}

val SheetRadius = 26.dp
