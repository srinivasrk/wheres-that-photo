package com.srini.wheresthatphoto.ml

data class DetectedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
) {
    fun isValid(): Boolean =
        right > left && bottom > top &&
            left >= 0f && top >= 0f && right <= 1f && bottom <= 1f
}
