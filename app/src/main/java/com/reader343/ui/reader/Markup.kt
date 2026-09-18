package com.reader343.ui.reader

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import com.reader343.R
import com.reader343.domain.Highlight
import com.reader343.domain.NormRect

enum class HighlightColor(val argb: Int, @StringRes val label: Int) {
    Yellow(0xFFFFE066.toInt(), R.string.highlight_color_yellow),
    Green(0xFF8BE08B.toInt(), R.string.highlight_color_green),
    Blue(0xFF8CC8FF.toInt(), R.string.highlight_color_blue),
    Pink(0xFFFF9EC7.toInt(), R.string.highlight_color_pink),
    Orange(0xFFFFB86B.toInt(), R.string.highlight_color_orange),
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
) {
    val bounds: NormRect
        get() = rects.reduceOrNull(NormRect::union)
            ?: NormRect.spanning(start.x, start.top, end.x, end.bottom)

    val canConfirm: Boolean get() = rects.isNotEmpty()
}

data class MarkupState(
    val highlights: Map<Int, List<Highlight>> = emptyMap(),
    val selection: SelectionUi? = null,
    val activeHighlight: Highlight? = null,
)

interface MarkupActions {
    fun onLongPress(position: Offset)
    fun onHandleGrab(handle: SelectionHandle, grabOffset: Offset)
    fun onSelectionDrag(position: Offset)
    fun onSelectionDragEnd()
    fun onColorSelected(color: Int)
    fun onConfirmHighlight()
    fun onDeleteHighlight()

    companion object {
        val None = object : MarkupActions {
            override fun onLongPress(position: Offset) = Unit
            override fun onHandleGrab(handle: SelectionHandle, grabOffset: Offset) = Unit
            override fun onSelectionDrag(position: Offset) = Unit
            override fun onSelectionDragEnd() = Unit
            override fun onColorSelected(color: Int) = Unit
            override fun onConfirmHighlight() = Unit
            override fun onDeleteHighlight() = Unit
        }
    }
}
