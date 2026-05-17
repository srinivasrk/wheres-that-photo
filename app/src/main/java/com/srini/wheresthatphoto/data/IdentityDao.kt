package com.srini.wheresthatphoto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class IdentityTabRow(
    val identityId: String,
    val kind: String,
    val displayName: String?,
    val representativeFaceId: String?,
    val thumbnailPath: String?,
    val photoCount: Int
)

@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentity(identity: IdentityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetectedFace(face: DetectedFaceEntity)

    @Query("SELECT * FROM identities WHERE id = :id LIMIT 1")
    suspend fun getIdentity(id: String): IdentityEntity?

    @Query("SELECT * FROM detected_faces WHERE kind = :kind")
    suspend fun getAllFacesByKind(kind: String): List<DetectedFaceEntity>

    @Query("SELECT DISTINCT photoId FROM detected_faces WHERE identityId = :identityId")
    suspend fun getPhotoIdsForIdentity(identityId: String): List<String>

    @Query(
        """
        SELECT i.id AS identityId, i.kind, i.displayName, i.representativeFaceId,
               f.thumbnailPath, COUNT(DISTINCT f.photoId) AS photoCount
        FROM identities i
        INNER JOIN detected_faces f ON f.identityId = i.id
        WHERE i.kind = :kind
        GROUP BY i.id
        ORDER BY
            CASE WHEN i.displayName IS NULL THEN 1 ELSE 0 END,
            i.displayName COLLATE NOCASE,
            i.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getIdentitiesForPeopleTab(kind: String, limit: Int): List<IdentityTabRow>

    @Query(
        """
        SELECT i.id FROM identities i
        WHERE i.displayName IS NOT NULL
          AND LOWER(i.displayName) LIKE '%' || LOWER(:query) || '%'
        """
    )
    suspend fun findIdentityIdsByName(query: String): List<String>

    @Query("SELECT DISTINCT photoId FROM detected_faces WHERE identityId IN (:identityIds)")
    suspend fun getPhotoIdsForIdentities(identityIds: List<String>): List<String>

    @Query("DELETE FROM detected_faces WHERE photoId = :photoId")
    suspend fun deleteFacesForPhoto(photoId: String)

    @Query("UPDATE identities SET displayName = :displayName, kind = :kind WHERE id = :identityId")
    suspend fun setIdentityName(identityId: String, displayName: String, kind: String)

    @Query("UPDATE identities SET representativeFaceId = :faceId WHERE id = :identityId")
    suspend fun setRepresentativeFace(identityId: String, faceId: String)

    @Query("UPDATE detected_faces SET identityId = :toId WHERE identityId = :fromId")
    suspend fun reassignFaces(fromId: String, toId: String)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteIdentity(id: String)

    @Query("DELETE FROM detected_faces")
    suspend fun deleteAllFaces()

    @Query("DELETE FROM identities")
    suspend fun deleteAllIdentities()

    @Query("SELECT COUNT(*) FROM detected_faces")
    suspend fun countFaces(): Int

    @Transaction
    suspend fun clearAllIdentityData() {
        deleteAllFaces()
        deleteAllIdentities()
    }
}
