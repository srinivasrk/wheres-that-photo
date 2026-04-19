package com.srini.wheresthatphoto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val width: Int?,
    val height: Int?,
    val indexedAt: Long?,
    val captionedAt: Long?
)

@Entity(tableName = "captions")
data class CaptionEntity(
    @PrimaryKey val photoId: String,
    val text: String,
    val modelVersion: String
)

@Entity(tableName = "clip_embeddings")
data class ClipEmbeddingEntity(
    @PrimaryKey val photoId: String,
    val embedding: FloatArray
)

@Entity(tableName = "caption_embeddings")
data class CaptionEmbeddingEntity(
    @PrimaryKey val photoId: String,
    val embedding: FloatArray
)
