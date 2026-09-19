package com.reader343.domain

const val MIN_USABLE_CHARS = 20
const val TEXT_SAMPLE_PAGES = 5

fun isUsableText(text: CharSequence): Boolean {
    var count = 0
    for (c in text) {
        if (c.isLetterOrDigit() && ++count >= MIN_USABLE_CHARS) return true
    }
    return false
}

fun textSamplePages(pageCount: Int, samples: Int = TEXT_SAMPLE_PAGES): List<Int> {
    if (pageCount <= 0 || samples <= 0) return emptyList()
    if (pageCount <= samples) return (0 until pageCount).toList()
    return (1..samples).map { i -> (i.toLong() * pageCount / (samples + 1)).toInt() }.distinct()
}

inline fun detectTextLayer(pageCount: Int, extract: (page: Int) -> CharSequence?): Boolean =
    textSamplePages(pageCount).any { page -> extract(page)?.let(::isUsableText) == true }
