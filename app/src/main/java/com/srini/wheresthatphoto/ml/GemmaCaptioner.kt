package com.srini.wheresthatphoto.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

private const val TAG = "WTP/Gemma"

class GemmaCaptioner(private val context: Context) {
    private val modelPath = "/data/local/tmp/llm/model.task"

    private val captionPrompt =
        "Describe this image in 2-3 short sentences. " +
            "Include the main subject, setting, and mood. " +
            "Mention visible people, animals, or objects. " +
            "Do not guess at identities."

    // Gemma 3n E4B is ~3 GB. Build the LlmInference once on first caption() call
    // and reuse it for every photo. Rebuilding per image would add minutes of
    // model-load cost to every caption.
    private val llm: LlmInference by lazy {
        LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setMaxNumImages(1)
                .build()
        )
    }

    fun validateModelPath(): Result<Unit> {
        val modelFile = File(modelPath)
        return if (modelFile.exists() && modelFile.canRead()) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "Model missing at $modelPath. Push it with: adb push <local_model_path> $modelPath"
                )
            )
        }
    }

    /**
     * Ask Gemma (text-only, no image) whether [caption] is relevant to [query].
     * Used as a semantic reranker when embedding-based search returns no results —
     * Gemma understands synonym relationships (e.g. "god" ↔ "deity") that vector
     * similarity alone misses.
     * Returns true if Gemma responds with "yes", false otherwise.
     */
    fun judgeRelevance(query: String, caption: String): Boolean {
        if (validateModelPath().isFailure) {
            Log.w(TAG, "judgeRelevance: model unavailable, skipping")
            return false
        }
        val prompt =
            "Search query: \"$query\"\n" +
            "Image caption: \"$caption\"\n\n" +
            "Is the image described by the caption relevant to the search query? " +
            "Consider synonyms and closely related concepts " +
            "(for example, 'deity' is relevant to 'god', 'canine' to 'dog'). " +
            "Reply with only the single word yes or no."

        Log.d(TAG, "judgeRelevance: query=\"$query\" caption=\"${caption.take(80)}…\"")

        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(false).build())
                .setTopK(1)          // deterministic
                .setTemperature(0.1f)
                .build()
        )
        return try {
            session.addQueryChunk(prompt)
            val response = session.generateResponse().trim().lowercase()
            val relevant = response.startsWith("yes")
            Log.i(TAG, "judgeRelevance: response=\"$response\" → relevant=$relevant")
            relevant
        } finally {
            session.close()
        }
    }

    fun caption(bitmap: Bitmap): String {
        val check = validateModelPath()
        if (check.isFailure) {
            return "Model unavailable: ${check.exceptionOrNull()?.message}"
        }

        // Fresh session per image — cheap, and avoids prompt or image context
        // from the previous photo bleeding into the next caption.
        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .setTopK(40)
                .setTemperature(0.8f)
                .build()
        )

        return try {
            session.addImage(BitmapImageBuilder(bitmap).build())
            session.addQueryChunk(captionPrompt)
            session.generateResponse().trim()
        } finally {
            session.close()
        }
    }
}
