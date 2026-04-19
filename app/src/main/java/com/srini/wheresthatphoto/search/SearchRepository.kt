package com.srini.wheresthatphoto.search

import android.util.Log
import com.srini.wheresthatphoto.data.AppDatabase
import com.srini.wheresthatphoto.ml.ClipEncoder
import com.srini.wheresthatphoto.ml.GemmaCaptioner
import com.srini.wheresthatphoto.ml.TextEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val TAG = "WTP/Search"

data class SearchResult(
    val photoId: String,
    val uri: String,
    val score: Float,
    val clipSimilarity: Float,
    val captionEmbeddingSimilarity: Float,
    val lexicalMatch: Boolean
)

/** Row for the Library tab: everything stored in Room with CLIP + caption embeddings. */
data class IndexedPhotoSummary(
    val photoId: String,
    val uri: String,
    val indexedAt: Long?,
    val captionedAt: Long?
)

class SearchRepository(
    private val database: AppDatabase,
    private val clipEncoder: ClipEncoder,
    private val textEncoder: TextEncoder,
    private val gemmaCaptioner: GemmaCaptioner
) {
    /**
     * Only returns photos whose CLIP or MiniLM cosine similarity clears [minSimilarity],
     * or whose stored caption contains the query (case-insensitive), so unrelated images are dropped.
     */
    suspend fun search(
        query: String,
        limit: Int = 50,
        minSimilarity: Float = DEFAULT_MIN_SIMILARITY
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        Log.i(TAG, "search: query=\"$q\" minSimilarity=$minSimilarity")

        coroutineScope {
            val clipQuery = async { clipEncoder.embedText(q) }
            val captionQuery = async { textEncoder.embed(q) }

            val dao = database.photoDao()
            val photos = dao.getAllPhotos().associateBy { it.id }
            val captionsById = dao.getAllCaptions().associateBy { it.photoId }
            val clipEmbeddings = dao.getAllClipEmbeddings().associate { it.photoId to it.embedding }
            val captionEmbeddings = dao.getAllCaptionEmbeddings().associate { it.photoId to it.embedding }

            Log.d(TAG, "search: DB has ${photos.size} photo(s), " +
                "${clipEmbeddings.size} CLIP embedding(s), " +
                "${captionEmbeddings.size} caption embedding(s)")

            val clipQ = clipQuery.await()
            val capQ = captionQuery.await()
            val qLower = q.lowercase()
            val lexicalTerms = qLower.split(Regex("\\s+")).filter { it.length >= 2 }

            val ranked = ArrayList<ScoredPhoto>()
            var skippedNoClip = 0
            var skippedNoCapEmb = 0
            var skippedLowScore = 0
            for ((id, _) in photos) {
                val clipEmb = clipEmbeddings[id]
                if (clipEmb == null) { skippedNoClip++; continue }
                val capEmb = captionEmbeddings[id]
                if (capEmb == null) { skippedNoCapEmb++; continue }
                val clipSim = cosineSimilarity(clipQ, clipEmb)
                val capSim = cosineSimilarity(capQ, capEmb)
                val captionText = captionsById[id]?.text?.lowercase().orEmpty()
                val lexical = if (lexicalTerms.isEmpty()) {
                    captionText.contains(qLower)
                } else {
                    lexicalTerms.all { captionText.contains(it) }
                }
                val fused = 0.45f * clipSim + 0.55f * capSim + if (lexical) LEXICAL_BONUS else 0f
                if (fused < minSimilarity && !lexical) {
                    Log.v(TAG, "  skip $id: clipSim=${"%.3f".format(clipSim)} capSim=${"%.3f".format(capSim)} fused=${"%.3f".format(fused)}")
                    skippedLowScore++
                    continue
                }
                Log.v(TAG, "  pass $id: clipSim=${"%.3f".format(clipSim)} capSim=${"%.3f".format(capSim)} fused=${"%.3f".format(fused)}")
                ranked.add(ScoredPhoto(id, fused, clipSim, capSim, lexical))
            }

            Log.i(TAG, "search: skippedNoClip=$skippedNoClip skippedNoCapEmb=$skippedNoCapEmb " +
                "skippedLowScore=$skippedLowScore passed=${ranked.size}")

            ranked.sortByDescending { it.fused }
            val results = ranked.take(limit).mapNotNull { scored ->
                val photo = photos[scored.photoId] ?: return@mapNotNull null
                SearchResult(
                    photoId = scored.photoId,
                    uri = photo.uri,
                    score = scored.fused,
                    clipSimilarity = scored.clipSim,
                    captionEmbeddingSimilarity = scored.capSim,
                    lexicalMatch = scored.lexical
                )
            }

            if (results.isNotEmpty()) {
                Log.i(TAG, "search: returning ${results.size} result(s)")
                return@coroutineScope results
            }

            // ── Gemma reranking fallback ──────────────────────────────────────────
            // Normal vector search found nothing (likely a synonym gap, e.g. "god"
            // for a photo captioned "deity"). Lower the threshold, collect the top
            // candidates by fused score, and ask Gemma to judge each caption.
            Log.i(TAG, "search: 0 vector results — running Gemma reranking fallback")
            val candidates = photos.keys
                .mapNotNull { id ->
                    val clipEmb = clipEmbeddings[id] ?: return@mapNotNull null
                    val capEmb  = captionEmbeddings[id] ?: return@mapNotNull null
                    val clipSim = cosineSimilarity(clipQ, clipEmb)
                    val capSim  = cosineSimilarity(capQ, capEmb)
                    val fused   = 0.45f * clipSim + 0.55f * capSim
                    Triple(id, fused, captionsById[id]?.text.orEmpty())
                }
                .filter { (_, fused, _) -> fused > GEMMA_CANDIDATE_THRESHOLD }
                .sortedByDescending { (_, fused, _) -> fused }
                .take(GEMMA_MAX_CANDIDATES)

            Log.i(TAG, "search: Gemma evaluating ${candidates.size} candidate(s)")
            val gemmaResults = candidates.mapNotNull { (id, fused, caption) ->
                if (caption.isBlank()) return@mapNotNull null
                val relevant = gemmaCaptioner.judgeRelevance(q, caption)
                if (!relevant) return@mapNotNull null
                val photo = photos[id] ?: return@mapNotNull null
                val clipSim = cosineSimilarity(clipQ, clipEmbeddings[id]!!)
                val capSim  = cosineSimilarity(capQ, captionEmbeddings[id]!!)
                SearchResult(
                    photoId = id,
                    uri = photo.uri,
                    score = fused,
                    clipSimilarity = clipSim,
                    captionEmbeddingSimilarity = capSim,
                    lexicalMatch = false
                )
            }
            Log.i(TAG, "search: Gemma confirmed ${gemmaResults.size} result(s)")
            gemmaResults
        }
    }

    suspend fun getCaption(photoId: String): String? = withContext(Dispatchers.IO) {
        database.photoDao().getCaption(photoId)?.text
    }

    /**
     * Returns only photos that have BOTH a CLIP embedding AND a caption embedding —
     * i.e., photos that are fully indexed and will actually show up in search results.
     * Ordered newest-indexed first.
     */
    suspend fun getIndexedPhotoSummaries(): List<IndexedPhotoSummary> = withContext(Dispatchers.IO) {
        val dao = database.photoDao()
        val allPhotos = dao.getAllPhotos()
        val clipIds = dao.getAllClipEmbeddings().mapTo(HashSet()) { it.photoId }
        val captionEmbIds = dao.getAllCaptionEmbeddings().mapTo(HashSet()) { it.photoId }
        Log.d(TAG, "getIndexedPhotoSummaries: totalPhotos=${allPhotos.size} " +
            "withClip=${clipIds.size} withCaptionEmb=${captionEmbIds.size}")
        val summaries = allPhotos
            .filter { it.id in clipIds && it.id in captionEmbIds }
            .map { p ->
                IndexedPhotoSummary(
                    photoId = p.id,
                    uri = p.uri,
                    indexedAt = p.indexedAt,
                    captionedAt = p.captionedAt
                )
            }
        Log.i(TAG, "getIndexedPhotoSummaries: returning ${summaries.size} fully-indexed photo(s)")
        summaries
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
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

    private data class ScoredPhoto(
        val photoId: String,
        val fused: Float,
        val clipSim: Float,
        val capSim: Float,
        val lexical: Boolean
    )

    companion object {
        private const val DEFAULT_MIN_SIMILARITY = 0.25f
        private const val LEXICAL_BONUS = 0.08f

        // Gemma fallback: minimum fused score to be considered a candidate for reranking.
        // Set low enough to catch synonym misses (deity/god fused ≈ 0.18).
        private const val GEMMA_CANDIDATE_THRESHOLD = 0.12f

        // Max photos passed to Gemma per query to keep latency bounded.
        private const val GEMMA_MAX_CANDIDATES = 8
    }
}
