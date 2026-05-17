package com.srini.wheresthatphoto.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class IdentityKind {
    PERSON,
    PET;

    fun toStorage(): String = name

    companion object {
        fun fromStorage(value: String): IdentityKind =
            entries.firstOrNull { it.name == value } ?: PERSON
    }
}

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val displayName: String?,
    val representativeFaceId: String?,
    val createdAt: Long
)

@Entity(
    tableName = "detected_faces",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("photoId"),
        Index("identityId"),
        Index("kind")
    ]
)
data class DetectedFaceEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    val identityId: String,
    val kind: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val embedding: FloatArray,
    val thumbnailPath: String,
    val detectedAt: Long
)
