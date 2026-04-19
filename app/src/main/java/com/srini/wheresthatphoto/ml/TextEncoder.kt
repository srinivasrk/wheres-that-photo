package com.srini.wheresthatphoto.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.srini.wheresthatphoto.ml.tokenizer.MiniLmWordpieceTokenizer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicReference

class TextEncoder(private val context: Context) {

    private val envRef = AtomicReference<OrtEnvironment?>(null)
    private val sessionRef = AtomicReference<OrtSession?>(null)
    private val tokenizerRef = AtomicReference<MiniLmWordpieceTokenizer?>(null)

    private fun env(): OrtEnvironment =
        envRef.updateAndGet { cur -> cur ?: OrtEnvironment.getEnvironment() }!!

    @Synchronized
    private fun session(): OrtSession {
        sessionRef.get()?.let { return it }
        val path = cachedOnnxFileFromAsset(context, "models/minilm/model.onnx").absolutePath
        val session = env().createSession(path, OrtSession.SessionOptions())
        sessionRef.set(session)
        return session
    }

    @Synchronized
    private fun tokenizer(): MiniLmWordpieceTokenizer {
        tokenizerRef.get()?.let { return it }
        val lines = context.assets.open("models/minilm/vocab.txt").bufferedReader().use { it.readLines() }
        val tok = MiniLmWordpieceTokenizer.fromVocabLines(lines)
        tokenizerRef.set(tok)
        return tok
    }

    fun embed(text: String): FloatArray {
        val tok = tokenizer()
        val (inputIds, attentionMask) = tok.encode(text)
        val seq = inputIds.size
        val tokenTypeIds = LongArray(seq) { 0L }

        val env = env()
        val session = session()
        val idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, seq.toLong()))
        val mT = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), longArrayOf(1, seq.toLong()))
        val tT = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), longArrayOf(1, seq.toLong()))
        return try {
            val outputs = session.run(
                mapOf(
                    "input_ids" to idsT,
                    "attention_mask" to mT,
                    "token_type_ids" to tT
                )
            )
            try {
                val tensor = outputs[0] as OnnxTensor
                val info = tensor.info as TensorInfo
                val shape = info.shape
                val s = shape[1].toInt()
                val dim = shape[2].toInt()
                val buf = FloatArray(s * dim)
                tensor.floatBuffer.get(buf)
                val pooled = meanPool(buf, s, dim, attentionMask)
                l2Normalize(pooled)
            } finally {
                outputs.close()
            }
        } finally {
            idsT.close()
            mT.close()
            tT.close()
        }
    }

    private fun meanPool(flat: FloatArray, seq: Int, dim: Int, mask: LongArray): FloatArray {
        val out = FloatArray(dim)
        var count = 0f
        for (t in 0 until seq) {
            if (t >= mask.size || mask[t] == 0L) continue
            count += 1f
            val off = t * dim
            for (d in 0 until dim) {
                out[d] += flat[off + d]
            }
        }
        if (count <= 0f) return out
        for (d in 0 until dim) out[d] /= count
        return out
    }
}
