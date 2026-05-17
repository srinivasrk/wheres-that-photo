package com.srini.wheresthatphoto.indexing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.srini.wheresthatphoto.data.DetectedFaceEntity
import com.srini.wheresthatphoto.data.FaceThumbnailStore
import com.srini.wheresthatphoto.data.IdentityDao
import com.srini.wheresthatphoto.data.IdentityEntity
import com.srini.wheresthatphoto.data.IdentityKind
import com.srini.wheresthatphoto.ml.BitmapCropUtils
import com.srini.wheresthatphoto.ml.ClipEncoder
import com.srini.wheresthatphoto.ml.DetectedRegion
import com.srini.wheresthatphoto.ml.cosineSimilarity
import java.util.UUID

private const val TAG = "WTP/FaceClusterer"
const val CLUSTER_SIMILARITY_THRESHOLD = 0.88f

class FaceClusterer(
    private val context: Context,
    private val clipEncoder: ClipEncoder,
    private val identityDao: IdentityDao
) {
    /**
     * Detects regions, embeds crops, clusters into identities, persists rows.
     * @return number of faces stored for this photo
     */
    suspend fun indexRegions(
        photoId: String,
        bitmap: Bitmap,
        regions: List<DetectedRegion>,
        kind: IdentityKind
    ): Int {
        if (regions.isEmpty()) return 0
        val kindStr = kind.toStorage()
        val existing = identityDao.getAllFacesByKind(kindStr).toMutableList()
        var count = 0
        for (region in regions) {
            val crop = BitmapCropUtils.cropSquare(bitmap, region) ?: continue
            try {
                val embedding = clipEncoder.embedImage(crop)
                val faceId = UUID.randomUUID().toString()
                val identityId = resolveIdentityId(embedding, kindStr, existing)
                val thumbPath = FaceThumbnailStore.save(context, faceId, crop)
                val now = System.currentTimeMillis()
                val face = DetectedFaceEntity(
                    id = faceId,
                    photoId = photoId,
                    identityId = identityId,
                    kind = kindStr,
                    left = region.left,
                    top = region.top,
                    right = region.right,
                    bottom = region.bottom,
                    embedding = embedding,
                    thumbnailPath = thumbPath,
                    detectedAt = now
                )
                identityDao.upsertDetectedFace(face)
                val identity = identityDao.getIdentity(identityId)
                if (identity?.representativeFaceId == null) {
                    identityDao.setRepresentativeFace(identityId, faceId)
                }
                existing.toMutableList().add(face)
                count++
            } finally {
                crop.recycle()
            }
        }
        Log.d(TAG, "indexRegions: photoId=$photoId kind=$kindStr stored=$count")
        return count
    }

    private suspend fun resolveIdentityId(
        embedding: FloatArray,
        kind: String,
        existing: List<DetectedFaceEntity>
    ): String {
        var bestId: String? = null
        var bestSim = CLUSTER_SIMILARITY_THRESHOLD
        for (face in existing) {
            val sim = cosineSimilarity(embedding, face.embedding)
            if (sim >= bestSim) {
                bestSim = sim
                bestId = face.identityId
            }
        }
        if (bestId != null) return bestId
        val newId = UUID.randomUUID().toString()
        identityDao.upsertIdentity(
            IdentityEntity(
                id = newId,
                kind = kind,
                displayName = null,
                representativeFaceId = null,
                createdAt = System.currentTimeMillis()
            )
        )
        return newId
    }
}
