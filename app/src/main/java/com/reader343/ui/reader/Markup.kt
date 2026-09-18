package com.reader343.ui.reader

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.reader343.R
import com.reader343.domain.Highlight
import com.reader343.domain.NormRect
import com.reader343.ui.theme.InkColors

enum class HighlightColor(val argb: Int, @StringRes val label: Int) {
    Yellow(InkColors.Yellow.toArgb(), R.string.highlight_color_yellow),
    Green(InkColors.Green.toArgb(), R.string.highlight_color_green),
    Blue(InkColors.Blue.toArgb(), R.string.highlight_color_blue),
    Pink(InkColors.Pink.toArgb(), R.string.highlight_color_pink),
    Orange(InkColors.Orange.toArgb(), R.string.highlight_color_orange),
}

enum class SelectionHandle { Start, End }

data class HandleMark(val x: Float, val top: Float, val bottom: Float)

data class SelectionUi(
    val page: Int,
    val rects: List<NormRect>,
    val start: HandleMark,
    val end: HandleMark,
    val region: Boolean,
    val color: Int,
    val text: String? = null,
) {
    val bounds: NormRect
        get() = rects.reduceOrNull(NormRect::union)
            ?: NormRect.spanning(start.x, start.top, end.x, end.bottom)

    val canConfirm: Boolean get() = rects.isNotEmpty()
}

data class Loupe(val source: Offset, val touch: Offset)

data class MarkupState(
    val highlights: Map<Int, List<Highlight>> = emptyMap(),
    val selection: SelectionUi? = null,
    val activeHighlight: Highlight? = null,
    val loupe: Loupe? = null,
    val highlightMode: Boolean = false,
) {
    val highlighting: Boolean get() = highlightMode || selection != null
}

interface MarkupActions {
    fun onLongPress(position: Offset)
    fun onHandleGrab(handle: SelectionHandle, position: Offset, grabOffset: Offset)
    fun onSelectionDrag(position: Offset)
    fun onSelectionDragEnd()
    fun onColorSelected(color: Int)
    fun onConfirmHighlight()
    fun onDeleteHighlight()
    fun onDismissSelection()

    companion object {
        val None = object : MarkupActions {
            override fun onLongPress(position: Offset) = Unit
            override fun onHandleGrab(handle: SelectionHandle, position: Offset, grabOffset: Offset) = Unit
            override fun onSelectionDrag(position: Offset) = Unit
            override fun onSelectionDragEnd() = Unit
            override fun onColorSelected(color: Int) = Unit
            override fun onConfirmHighlight() = Unit
            override fun onDeleteHighlight() = Unit
            override fun onDismissSelection() = Unit
        }
    }
}
