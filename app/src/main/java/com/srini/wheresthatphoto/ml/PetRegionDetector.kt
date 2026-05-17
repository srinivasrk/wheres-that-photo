package com.srini.wheresthatphoto.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "WTP/PetDetector"
private const val MODEL_ASSET = "models/mediapipe/efficientdet_lite0.tflite"
private const val MIN_CONFIDENCE = 0.4f
private val PET_LABELS = setOf("dog", "cat", "bird", "horse")

class PetRegionDetector(private val context: Context) {

    private val detectorRef = AtomicReference<ObjectDetector?>(null)
    private var initFailed = false

    @Synchronized
    private fun detector(): ObjectDetector? {
        if (initFailed) return null
        detectorRef.get()?.let { return it }
        return try {
            if (!assetExists(MODEL_ASSET)) {
                Log.w(TAG, "Pet detector model missing at assets/$MODEL_ASSET — pet detection disabled")
                initFailed = true
                return null
            }
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setScoreThreshold(MIN_CONFIDENCE)
                .setMaxResults(8)
                .build()
            ObjectDetector.createFromOptions(context, options).also { detectorRef.set(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector", e)
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
                val label = detection.categories().firstOrNull()?.categoryName()?.lowercase() ?: return@mapNotNull null
                if (label !in PET_LABELS) return@mapNotNull null
                val box = detection.boundingBox()
                val body = DetectedRegion(
                    left = box.left / w,
                    top = box.top / h,
                    right = box.right / w,
                    bottom = box.bottom / h,
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f
                )
                BitmapCropUtils.petHeadRegion(body).takeIf { it.isValid() }
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
