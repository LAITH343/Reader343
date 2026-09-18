package com.reader343.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.reader343.pdf.PageSize

data class ZoomState(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > MIN_SCALE + ZOOM_EPSILON

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        private const val ZOOM_EPSILON = 0.01f
    }
}

data class PageLayout(val viewport: IntSize, val page: Rect) {

    fun transform(zoom: ZoomState, centroid: Offset, pan: Offset, factor: Float): ZoomState {
        val newScale = (zoom.scale * factor).coerceIn(ZoomState.MIN_SCALE, ZoomState.MAX_SCALE)
        val ratio = newScale / zoom.scale
        val x = centroid.x - (centroid.x - zoom.offsetX) * ratio + pan.x
        val y = centroid.y - (centroid.y - zoom.offsetY) * ratio + pan.y
        return clamp(ZoomState(newScale, x, y))
    }

    fun clamp(zoom: ZoomState): ZoomState {
        if (!zoom.isZoomed) return ZoomState()
        return zoom.copy(
            offsetX = clampAxis(zoom.offsetX, zoom.scale, page.left, page.right, viewport.width.toFloat()),
            offsetY = clampAxis(zoom.offsetY, zoom.scale, page.top, page.bottom, viewport.height.toFloat()),
        )
    }

    fun visibleRegion(zoom: ZoomState): Rect? {
        if (page.width <= 0f || page.height <= 0f) return null
        val left = ((-zoom.offsetX / zoom.scale - page.left) / page.width).coerceIn(0f, 1f)
        val top = ((-zoom.offsetY / zoom.scale - page.top) / page.height).coerceIn(0f, 1f)
        val right = (((viewport.width - zoom.offsetX) / zoom.scale - page.left) / page.width).coerceIn(0f, 1f)
        val bottom = (((viewport.height - zoom.offsetY) / zoom.scale - page.top) / page.height).coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }

    private fun clampAxis(offset: Float, scale: Float, start: Float, end: Float, extent: Float): Float {
        val scaledStart = start * scale
        val scaledEnd = end * scale
        return if (scaledEnd - scaledStart <= extent) {
            (extent - (scaledEnd - scaledStart)) / 2f - scaledStart
        } else {
            offset.coerceIn(extent - scaledEnd, -scaledStart)
        }
    }

    companion object {
        fun fit(viewport: IntSize, pageSize: PageSize): PageLayout {
            val available = Size(viewport.width.toFloat(), viewport.height.toFloat())
            val width = minOf(available.width, available.height * pageSize.aspectRatio)
            val height = width / pageSize.aspectRatio
            val left = (available.width - width) / 2f
            val top = (available.height - height) / 2f
            return PageLayout(viewport, Rect(left, top, left + width, top + height))
        }
    }
}
