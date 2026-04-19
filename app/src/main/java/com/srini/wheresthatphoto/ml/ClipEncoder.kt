package com.srini.wheresthatphoto.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.srini.wheresthatphoto.ml.tokenizer.ClipBytePairTokenizer
import org.json.JSONObject
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.math.roundToInt

class ClipEncoder(private val context: Context) {

    private val envRef = AtomicReference<OrtEnvironment?>(null)
    private val visionRef = AtomicReference<OrtSession?>(null)
    private val textRef = AtomicReference<OrtSession?>(null)
    private val tokRef = AtomicReference<ClipBytePairTokenizer?>(null)

    private fun env(): OrtEnvironment =
        envRef.updateAndGet { cur -> cur ?: OrtEnvironment.getEnvironment() }!!

    @Synchronized
    private fun tokenizer(): ClipBytePairTokenizer {
        tokRef.get()?.let { return it }
        val json = context.assets.open("models/mobileclip/tokenizer.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val model = JSONObject(json).getJSONObject("model")
        val vocabJson = model.getJSONObject("vocab")
        val vocab = LinkedHashMap<String, Int>(vocabJson.length())
        val keys = vocabJson.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            vocab[k] = vocabJson.getInt(k)
        }
        val mergesArr = model.getJSONArray("merges")
        val merges = List(mergesArr.length()) { mergesArr.getString(it) }
        val t = ClipBytePairTokenizer.fromMergesAndVocab(vocab, merges)
        tokRef.set(t)
        return t
    }

    @Synchronized
    private fun visionSession(): OrtSession {
        visionRef.get()?.let { return it }
        val path = cachedOnnxFileFromAsset(context, "models/mobileclip/vision_model.onnx").absolutePath
        val session = env().createSession(path, OrtSession.SessionOptions())
        visionRef.set(session)
        return session
    }

    @Synchronized
    private fun textSession(): OrtSession {
        textRef.get()?.let { return it }
        val path = cachedOnnxFileFromAsset(context, "models/mobileclip/text_model.onnx").absolutePath
        val session = env().createSession(path, OrtSession.SessionOptions())
        textRef.set(session)
        return session
    }

    fun embedImage(bitmap: Bitmap): FloatArray {
        val chw = preprocessImage(bitmap)
        val env = env()
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, 256, 256))
        return try {
            val outputs = visionSession().run(mapOf("pixel_values" to tensor))
            try {
                val t = outputs[0] as OnnxTensor
                val emb = FloatArray(512)
                t.floatBuffer.get(emb)
                l2Normalize(emb)
            } finally {
                outputs.close()
            }
        } finally {
            tensor.close()
        }
    }

    fun embedText(query: String): FloatArray {
        val ids = tokenizer().encodeToIds(query.trim(), 77)
        val env = env()
        val tensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, 77))
        return try {
            val outputs = textSession().run(mapOf("input_ids" to tensor))
            try {
                val t = outputs[0] as OnnxTensor
                val emb = FloatArray(512)
                t.floatBuffer.get(emb)
                l2Normalize(emb)
            } finally {
                outputs.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val target = 256
        val w = bitmap.width
        val h = bitmap.height
        val scale = target.toFloat() / min(w, h)
        val nw = maxOf(1, (w * scale).roundToInt())
        val nh = maxOf(1, (h * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        val cropW = min(target, nw)
        val cropH = min(target, nh)
        val cropX = (nw - cropW) / 2
        val cropY = (nh - cropH) / 2
        val cropped = Bitmap.createBitmap(scaled, cropX, cropY, cropW, cropH)
        val chw = FloatArray(1 * 3 * target * target)
        var i = 0
        for (c in 0 until 3) {
            for (y in 0 until target) {
                for (x in 0 until target) {
                    val pixel = cropped.getPixel(x, y)
                    val v = when (c) {
                        0 -> ((pixel shr 16) and 0xff) / 255f
                        1 -> ((pixel shr 8) and 0xff) / 255f
                        else -> (pixel and 0xff) / 255f
                    }
                    chw[i++] = v
                }
            }
        }
        cropped.recycle()
        if (scaled != bitmap) scaled.recycle()
        return chw
    }
}
