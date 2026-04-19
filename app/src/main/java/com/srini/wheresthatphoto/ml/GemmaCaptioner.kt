package com.srini.wheresthatphoto.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.srini.wheresthatphoto.media.PhotoMetadata
import java.io.File

private const val TAG = "WTP/Gemma"

class GemmaCaptioner(private val context: Context) {
    private val modelPath = "/data/local/tmp/llm/model.task"

    /**
     * Base visual description instruction appended after any metadata context.
     * Kept separate so the metadata preamble can be injected cleanly.
     */
    private val captionInstruction =
        "Look carefully at this specific image and describe only what you can actually see in it. " +
            "Write 2-3 sentences covering the main subject, setting, and mood. " +
            "Mention visible people, animals, or objects with accurate detail. " +
            "Do not guess, infer, or describe anything not visible in the image."

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
     * Ask Gemma (text-only, one call) which of the [captions] are relevant to [query].
     * All captions are batched into a single prompt so we pay model-load cost only once.
     * Returns a list of booleans parallel to [captions].
     */
    fun judgeRelevanceBatch(query: String, captions: List<String>): List<Boolean> {
        if (captions.isEmpty()) return emptyList()
        if (validateModelPath().isFailure) {
            Log.w(TAG, "judgeRelevanceBatch: model unavailable, returning all false")
            return List(captions.size) { false }
        }

        // Build a numbered list so Gemma can answer per-item in one shot.
        val numberedCaptions = captions.mapIndexed { i, cap ->
            "${i + 1}. \"$cap\""
        }.joinToString("\n")

        val prompt =
            "You are a search relevance judge. " +
            "For each numbered image caption below, decide if it is relevant to the search query. " +
            "Consider synonyms and related concepts (e.g. 'deity' is relevant to 'god', 'canine' to 'dog').\n\n" +
            "Search query: \"$query\"\n\n" +
            "Captions:\n$numberedCaptions\n\n" +
            "Reply with exactly one line per caption in this format (no other text):\n" +
            "1: yes\n2: no\n3: yes\n..."

        Log.d(TAG, "judgeRelevanceBatch: query=\"$query\" captions=${captions.size}")
        Log.v(TAG, "judgeRelevanceBatch prompt:\n$prompt")

        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(false).build())
                .setTopK(1)
                .setTemperature(0.1f)
                .build()
        )
        return try {
            session.addQueryChunk(prompt)
            val response = session.generateResponse().trim()
            Log.i(TAG, "judgeRelevanceBatch response:\n$response")
            parseRelevanceResponse(response, captions.size)
        } finally {
            session.close()
        }
    }

    /**
     * Parse Gemma's numbered yes/no response into a boolean list.
     * Expected format per line: "N: yes" or "N: no" (case-insensitive).
     * Falls back to false for any line that can't be parsed.
     */
    private fun parseRelevanceResponse(response: String, count: Int): List<Boolean> {
        val results = MutableList(count) { false }
        response.lines().forEach { line ->
            val clean = line.trim().lowercase()
            // Match "1: yes", "2: no", "1 yes", "1-yes" etc.
            val match = Regex("""^(\d+)\s*[:\-]\s*(yes|no)""").find(clean)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
                if (index in results.indices) {
                    results[index] = match.groupValues[2] == "yes"
                }
            }
        }
        Log.d(TAG, "judgeRelevanceBatch parsed: $results")
        return results
    }

    /**
     * Generate a caption for [bitmap].
     *
     * If [metadata] is provided (date, time, or location extracted from EXIF), a context
     * sentence is prepended to the prompt so Gemma weaves those facts into the caption
     * naturally — making captions like "A birthday party at a café in Rome on July 4, 2023"
     * searchable by date, time, and place.
     */
    fun caption(bitmap: Bitmap, metadata: PhotoMetadata? = null): String {
        val check = validateModelPath()
        if (check.isFailure) {
            return "Model unavailable: ${check.exceptionOrNull()?.message}"
        }

        // Build the prompt: optional metadata preamble + visual instruction.
        val prompt = buildString {
            if (metadata != null && metadata.hasAny) {
                val parts = listOfNotNull(
                    metadata.dateTaken?.let { "on $it" },
                    metadata.timeTaken?.let { "at $it" },
                    metadata.location?.let { "in $it" }
                )
                if (parts.isNotEmpty()) {
                    append("This photo was taken ${parts.joinToString(" ")}. ")
                    append("Include this date, time, and location naturally in your description. ")
                }
            }
            append(captionInstruction)
        }
        Log.d(TAG, "caption: metadata=${metadata?.dateTaken} ${metadata?.timeTaken} ${metadata?.location}")

        // Vision session for this image.
        // Temperature lowered to 0.3 — captioning is a factual task, not creative writing.
        // Lower temperature reduces hallucination while keeping natural phrasing.
        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .setTopK(20)
                .setTemperature(0.3f)
                .build()
        )

        val caption = try {
            session.addImage(BitmapImageBuilder(bitmap).build())
            session.addQueryChunk(prompt)
            session.generateResponse().trim()
        } finally {
            session.close()
        }

        // Flush: open a cheap text-only session immediately after the vision session
        // closes. MediaPipe's LlmInference engine has been observed retaining image
        // state across sessions; this blank round-trip forces the internal image buffer
        // to be cleared before the next photo is captioned.
        try {
            val flushSession = LlmInferenceSession.createFromOptions(
                llm,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setGraphOptions(GraphOptions.builder().setEnableVisionModality(false).build())
                    .setTopK(1)
                    .setTemperature(0.1f)
                    .build()
            )
            try {
                flushSession.addQueryChunk(".")
                flushSession.generateResponse()
            } finally {
                flushSession.close()
            }
            Log.d(TAG, "caption: flush session complete")
        } catch (e: Exception) {
            Log.w(TAG, "caption: flush session failed (non-fatal): ${e.message}")
        }

        return caption
    }
}
