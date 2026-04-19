package com.srini.wheresthatphoto.indexing

enum class IndexPhase {
    Embedding,
    Captioning
}

/**
 * Emitted while [Indexer.indexPickedPhotosFlow] processes each picked photo.
 * [current] is 1-based for display ("Indexing N of M").
 */
data class IndexProgress(
    val current: Int,
    val total: Int,
    val currentUri: String,
    val phase: IndexPhase,
    val captionText: String?
)
