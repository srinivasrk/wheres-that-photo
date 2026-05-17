package com.srini.wheresthatphoto.ml

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

object BitmapCropUtils {
    /** Square crop with ~10% padding around normalized [region]. */
    fun cropSquare(bitmap: Bitmap, region: DetectedRegion, paddingFraction: Float = 0.1f): Bitmap? {
        if (!region.isValid()) return null
        val w = bitmap.width
        val h = bitmap.height
        val boxW = (region.right - region.left) * w
        val boxH = (region.bottom - region.top) * h
        val padX = boxW * paddingFraction
        val padY = boxH * paddingFraction
        var left = region.left * w - padX
        var top = region.top * h - padY
        var right = region.right * w + padX
        var bottom = region.bottom * h + padY
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val side = max(right - left, bottom - top)
        left = cx - side / 2f
        right = cx + side / 2f
        top = cy - side / 2f
        bottom = cy + side / 2f
        val x0 = left.toInt().coerceIn(0, w - 1)
        val y0 = top.toInt().coerceIn(0, h - 1)
        val x1 = right.toInt().coerceIn(x0 + 1, w)
        val y1 = bottom.toInt().coerceIn(y0 + 1, h)
        val cw = x1 - x0
        val ch = y1 - y0
        if (cw < 8 || ch < 8) return null
        return Bitmap.createBitmap(bitmap, x0, y0, cw, ch)
    }

    /** Pet head heuristic: top ~45% of detection box with slight horizontal padding. */
    fun petHeadRegion(region: DetectedRegion): DetectedRegion {
        val h = region.bottom - region.top
        val headH = h * 0.45f
        val padX = (region.right - region.left) * 0.05f
        return DetectedRegion(
            left = (region.left - padX).coerceIn(0f, 1f),
            top = region.top.coerceIn(0f, 1f),
            right = (region.right + padX).coerceIn(0f, 1f),
            bottom = (region.top + headH).coerceIn(0f, 1f),
            confidence = region.confidence
        )
    }
}
