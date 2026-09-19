package com.reader343.domain

data class SpeechUnit(
    val page: Int,
    val sentenceIndex: Int,
    val charStart: Int,
    val charEnd: Int,
    val text: String,
    val rects: List<NormRect>,
) {
    val id: String get() = "$page:$sentenceIndex"
}
