package com.reader343.domain

data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun contains(x: Float, y: Float, slopX: Float = 0f, slopY: Float = 0f): Boolean =
        x >= left - slopX && x <= right + slopX && y >= top - slopY && y <= bottom + slopY

    fun union(other: NormRect): NormRect = NormRect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    companion object {
        fun spanning(x1: Float, y1: Float, x2: Float, y2: Float): NormRect =
            NormRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
    }
}

data class Highlight(
    val id: Long,
    val page: Int,
    val rects: List<NormRect>,
    val color: Int,
    val charStart: Int?,
    val charEnd: Int?,
    val snippet: String?,
    val createdAt: Long,
) {
    fun contains(x: Float, y: Float, slopX: Float, slopY: Float): Boolean =
        rects.any { it.contains(x, y, slopX, slopY) }
}

data class NewHighlight(
    val page: Int,
    val rects: List<NormRect>,
    val color: Int,
    val charStart: Int?,
    val charEnd: Int?,
    val snippet: String?,
)
