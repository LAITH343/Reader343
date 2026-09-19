package com.reader343.pdf

import com.reader343.domain.NormRect
import com.reader343.domain.SpeechSource
import com.reader343.domain.SpeechUnit
import com.reader343.domain.isUsableText
import java.util.Locale
import kotlin.math.sqrt

data class TextChar(val char: Char, val box: NormRect?)

class PageText(val page: Int, val chars: List<TextChar>) {

    val hasText: Boolean = chars.any { it.box != null && !it.char.isWhitespace() }

    val text: String by lazy { chars.joinToString("") { it.char.toString() } }

    val isUsable: Boolean by lazy { isUsableText(text) }

    fun charNear(x: Float, y: Float, aspect: Float, maxDistance: Float): Int? {
        val index = nearestChar(x, y, aspect) ?: return null
        val box = chars[index].box ?: return null
        return index.takeIf { distance(box, x, y, aspect, verticalWeight = 1f) <= maxDistance }
    }

    fun nearestChar(x: Float, y: Float, aspect: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        chars.forEachIndexed { i, c ->
            val box = c.box ?: return@forEachIndexed
            if (c.char.isWhitespace()) return@forEachIndexed
            val d = distance(box, x, y, aspect, VERTICAL_WEIGHT)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    fun wordRange(index: Int): IntRange {
        var start = index
        var end = index
        while (start > 0 && isWordChar(start - 1)) start--
        while (end < chars.lastIndex && isWordChar(end + 1)) end++
        return start..end
    }

    fun rectsFor(start: Int, end: Int): List<NormRect> =
        rectsFor(start.coerceAtLeast(0)..end.coerceAtMost(chars.lastIndex))

    fun rectsFor(indices: Iterable<Int>): List<NormRect> {
        val result = mutableListOf<NormRect>()
        var line: NormRect? = null
        for (i in indices) {
            val box = chars[i].box ?: continue
            val current = line
            line = if (current != null && sameLine(current, box)) {
                current.union(box)
            } else {
                current?.let(result::add)
                box
            }
        }
        line?.let(result::add)
        return result
    }

    fun textFor(start: Int, end: Int): String {
        val builder = StringBuilder()
        for (i in start.coerceAtLeast(0)..end.coerceAtMost(chars.lastIndex)) {
            val c = chars[i].char
            if (c.isWhitespace() || c.isISOControl()) {
                if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
            } else {
                builder.append(c)
            }
        }
        return builder.toString().trim().take(MAX_SNIPPET)
    }

    private fun isWordChar(index: Int): Boolean {
        val c = chars[index]
        return c.box != null && !c.char.isWhitespace()
    }

    private fun sameLine(line: NormRect, box: NormRect): Boolean {
        val overlap = minOf(line.bottom, box.bottom) - maxOf(line.top, box.top)
        return overlap >= minOf(line.height, box.height) * LINE_OVERLAP &&
            box.left >= line.left - box.width
    }

    private fun distance(box: NormRect, x: Float, y: Float, aspect: Float, verticalWeight: Float): Float {
        val dx = when {
            x < box.left -> box.left - x
            x > box.right -> x - box.right
            else -> 0f
        }
        val dy = when {
            y < box.top -> box.top - y
            y > box.bottom -> y - box.bottom
            else -> 0f
        } / aspect * verticalWeight
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val VERTICAL_WEIGHT = 3f
        const val LINE_OVERLAP = 0.5f
        const val MAX_SNIPPET = 500
    }
}

class TextLayer(
    private val engine: PdfEngine,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val cache = object : LinkedHashMap<Int, PageText>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageText>): Boolean = size > capacity
    }

    fun peek(page: Int): PageText? = cache[page]

    suspend fun get(page: Int): PageText =
        cache[page] ?: engine.loadText(page).also { cache[page] = it }

    suspend fun speechUnits(
        page: Int,
        pageCount: Int,
        locale: Locale = Locale.getDefault(),
        skipFurniture: Boolean = true,
    ): List<SpeechUnit> {
        if (!skipFurniture) return TextSegmenter.segment(get(page), locale, skipFurniture = false)
        val neighbors = listOf(page - 1, page + 1).filter { it in 0 until pageCount }.map { get(it) }
        return TextSegmenter.segment(get(page), locale, neighbors)
    }

    fun clear() = cache.clear()

    private companion object {
        const val DEFAULT_CAPACITY = 8
    }
}

class TextLayerSpeechSource(
    val textLayer: TextLayer,
    override val bookId: Long,
    override val pageCount: Int,
    override val locale: Locale = Locale.getDefault(),
    val skipFurniture: Boolean = true,
) : SpeechSource {

    override suspend fun units(page: Int): List<SpeechUnit> =
        textLayer.speechUnits(page, pageCount, locale, skipFurniture)
}
