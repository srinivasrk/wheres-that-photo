package com.srini.wheresthatphoto.ml

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

internal fun l2Normalize(vector: FloatArray): FloatArray {
    var sum = 0f
    for (x in vector) sum += x * x
    val n = sqrt(sum.toDouble()).toFloat().coerceAtLeast(1e-12f)
    return FloatArray(vector.size) { i -> vector[i] / n }
}

private val copyLocks = ConcurrentHashMap<String, Any>()

private const val STREAM_BUFFER_BYTES = 256 * 1024

/**
 * Copies a packaged ONNX asset to app files dir in small chunks (no giant [ByteArray]),
 * then returns that file for [OrtEnvironment.createSession] path loading (mmap-friendly).
 */
internal fun cachedOnnxFileFromAsset(context: Context, assetPath: String): File {
    val lock = copyLocks.getOrPut(assetPath) { Any() }
    synchronized(lock) {
        val dir = File(context.filesDir, "onnx_models").apply { mkdirs() }
        val safeName = assetPath.replace('/', '_')
        val outFile = File(dir, safeName)
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile
        }
        val tmp = File(dir, "$safeName.tmp")
        context.assets.open(assetPath).use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                }
                output.flush()
            }
        }
        if (outFile.exists()) {
            outFile.delete()
        }
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
        return outFile
    }
}
