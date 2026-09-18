package com.reader343.data.repo

import com.reader343.domain.NormRect
import org.json.JSONArray

internal fun NormRect.toJson(): JSONArray =
    JSONArray()
        .put(left.toDouble())
        .put(top.toDouble())
        .put(right.toDouble())
        .put(bottom.toDouble())

internal fun JSONArray.toNormRect(): NormRect = NormRect(
    left = getDouble(0).toFloat(),
    top = getDouble(1).toFloat(),
    right = getDouble(2).toFloat(),
    bottom = getDouble(3).toFloat(),
)
