package com.srini.wheresthatphoto.ml

import kotlin.math.sqrt

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return -1f
    var dot = 0f
    var aNorm = 0f
    var bNorm = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        aNorm += a[i] * a[i]
        bNorm += b[i] * b[i]
    }
    val denominator = sqrt(aNorm.toDouble()) * sqrt(bNorm.toDouble())
    return if (denominator == 0.0) -1f else (dot / denominator).toFloat()
}
