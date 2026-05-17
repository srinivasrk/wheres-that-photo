package com.srini.wheresthatphoto.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "WTP/FaceDetector"
private const val MODEL_ASSET = "models/mediapipe/face_detection_short_range.tflite"
private const val MIN_CONFIDENCE = 0.5f

class FaceDetectorEngine(private val context: Context) {

    private val detectorRef = AtomicReference<FaceDetector?>(null)
    private var initFailed = false

    @Synchronized
    private fun detector(): FaceDetector? {
        if (initFailed) return null
        detectorRef.get()?.let { return it }
        return try {
            if (!assetExists(MODEL_ASSET)) {
                Log.w(TAG, "Face model missing at assets/$MODEL_ASSET — face detection disabled")
                initFailed = true
                return null
            }
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(MIN_CONFIDENCE)
                .build()
            FaceDetector.createFromOptions(context, options).also { detectorRef.set(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceDetector", e)
            initFailed = true
            null
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedRegion> {
        val det = detector() ?: return emptyList()
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        return try {
            val mpImage = BitmapImageBuilder(argb).build()
            val result = det.detect(mpImage) ?: return emptyList()
            val w = bitmap.width.toFloat().coerceAtLeast(1f)
            val h = bitmap.height.toFloat().coerceAtLeast(1f)
            result.detections().mapNotNull { detection ->
                val box = detection.boundingBox()
                val left = box.left / w
                val top = box.top / h
                val right = box.right / w
                val bottom = box.bottom / h
                val score = detection.categories().firstOrNull()?.score() ?: 0f
                DetectedRegion(left, top, right, bottom, score).takeIf { it.isValid() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "detect failed", e)
            emptyList()
        } finally {
            if (argb !== bitmap) argb.recycle()
        }
    }

    fun close() {
        detectorRef.getAndSet(null)?.close()
    }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (_: Exception) {
        false
    }
}
