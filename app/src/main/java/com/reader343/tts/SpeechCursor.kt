package com.reader343.tts

import com.reader343.domain.SpeechPosition
import com.reader343.domain.SpeechSource
import com.reader343.domain.SpeechUnit

class SpeechCursor(
    val source: SpeechSource,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val cache = object : LinkedHashMap<Int, List<SpeechUnit>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<SpeechUnit>>): Boolean = size > capacity
    }

    suspend fun units(page: Int): List<SpeechUnit> {
        if (page !in 0 until source.pageCount) return emptyList()
        return cache[page] ?: source.units(page).also { cache[page] = it }
    }

    suspend fun from(position: SpeechPosition): List<SpeechUnit> {
        if (position.page !in 0 until source.pageCount) return emptyList()
        val rest = units(position.page).filter { it.sentenceIndex >= position.sentenceIndex }
        return rest.ifEmpty { after(position.page).orEmpty() }
    }

    suspend fun after(page: Int): List<SpeechUnit>? {
        for (p in page + 1 until source.pageCount) {
            units(p).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    suspend fun next(position: SpeechPosition): SpeechUnit? =
        units(position.page).firstOrNull { it.sentenceIndex > position.sentenceIndex }
            ?: after(position.page)?.first()

    suspend fun previous(position: SpeechPosition): SpeechUnit? =
        units(position.page).lastOrNull { it.sentenceIndex < position.sentenceIndex }
            ?: before(position.page)?.last()

    private suspend fun before(page: Int): List<SpeechUnit>? {
        for (p in minOf(page, source.pageCount) - 1 downTo 0) {
            units(p).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4
    }
}
