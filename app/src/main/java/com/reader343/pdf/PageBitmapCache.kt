package com.reader343.pdf

import android.graphics.Bitmap
import android.util.LruCache

class PageBitmapCache(maxBytes: Int = defaultMaxBytes()) {

    data class Key(val page: Int, val widthPx: Int)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    operator fun get(key: Key): Bitmap? = cache.get(key)

    operator fun set(key: Key, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() = cache.evictAll()

    private companion object {
        fun defaultMaxBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
