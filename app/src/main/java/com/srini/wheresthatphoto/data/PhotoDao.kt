package com.srini.wheresthatphoto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClipEmbedding(embedding: ClipEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaption(caption: CaptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaptionEmbedding(embedding: CaptionEmbeddingEntity)

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): PhotoEntity?

    @Query("SELECT * FROM photos ORDER BY indexedAt DESC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE captionedAt IS NULL")
    suspend fun getUncaptionedPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM clip_embeddings")
    suspend fun getAllClipEmbeddings(): List<ClipEmbeddingEntity>

    @Query("SELECT * FROM caption_embeddings")
    suspend fun getAllCaptionEmbeddings(): List<CaptionEmbeddingEntity>

    @Query("SELECT * FROM captions WHERE photoId = :photoId LIMIT 1")
    suspend fun getCaption(photoId: String): CaptionEntity?

    @Query("SELECT * FROM captions")
    suspend fun getAllCaptions(): List<CaptionEntity>

    @Transaction
    suspend fun upsertCaptionBundle(
        caption: CaptionEntity,
        embedding: CaptionEmbeddingEntity
    ) {
        upsertCaption(caption)
        upsertCaptionEmbedding(embedding)
    }

    @Query("DELETE FROM clip_embeddings")
    suspend fun deleteAllClipEmbeddings()

    @Query("DELETE FROM caption_embeddings")
    suspend fun deleteAllCaptionEmbeddings()

    @Query("DELETE FROM captions")
    suspend fun deleteAllCaptions()

    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()

    @Transaction
    suspend fun clearAll(identityDao: IdentityDao) {
        identityDao.clearAllIdentityData()
        deleteAllClipEmbeddings()
        deleteAllCaptionEmbeddings()
        deleteAllCaptions()
        deleteAllPhotos()
    }

    @Query("DELETE FROM clip_embeddings WHERE photoId IN (:ids)")
    suspend fun deleteClipEmbeddingsByIds(ids: List<String>)

    @Query("DELETE FROM caption_embeddings WHERE photoId IN (:ids)")
    suspend fun deleteCaptionEmbeddingsByIds(ids: List<String>)

    @Query("DELETE FROM captions WHERE photoId IN (:ids)")
    suspend fun deleteCaptionsByIds(ids: List<String>)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<String>)

    @Transaction
    suspend fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        deleteClipEmbeddingsByIds(ids)
        deleteCaptionEmbeddingsByIds(ids)
        deleteCaptionsByIds(ids)
        deletePhotosByIds(ids)
    }
}
