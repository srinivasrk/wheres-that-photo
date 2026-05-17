package com.srini.wheresthatphoto.indexing

import android.content.ContentResolver
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import java.util.UUID
import com.srini.wheresthatphoto.data.AppDatabase
import com.srini.wheresthatphoto.data.CaptionEmbeddingEntity
import com.srini.wheresthatphoto.data.CaptionEntity
import com.srini.wheresthatphoto.data.ClipEmbeddingEntity
import com.srini.wheresthatphoto.data.FaceThumbnailStore
import com.srini.wheresthatphoto.data.IdentityKind
import com.srini.wheresthatphoto.data.PhotoEntity
import com.srini.wheresthatphoto.media.MediaStoreRepository
import com.srini.wheresthatphoto.media.PhotoMetadataExtractor
import com.srini.wheresthatphoto.ml.ClipEncoder
import com.srini.wheresthatphoto.ml.FaceDetectorEngine
import com.srini.wheresthatphoto.ml.GemmaCaptioner
import com.srini.wheresthatphoto.ml.PetRegionDetector
import com.srini.wheresthatphoto.ml.TextEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private const val TAG = "WTP/Indexer"

class Indexer(
    private val appContext: Context,
    private val mediaStoreRepository: MediaStoreRepository,
    private val clipEncoder: ClipEncoder,
    private val textEncoder: TextEncoder,
    private val gemmaCaptioner: GemmaCaptioner,
    private val photoMetadataExtractor: PhotoMetadataExtractor,
    private val faceDetector: FaceDetectorEngine,
    private val petRegionDetector: PetRegionDetector,
    private val faceClusterer: FaceClusterer,
    private val database: AppDatabase
) {
    /**
     * Drop DB rows for photos that no longer exist in MediaStore (e.g., the user
     * deleted them from the gallery). Only considers rows whose ID is all digits —
     * that's how MediaStore IDs look. Picked-photos rows use UUIDs and are left
     * alone; [indexPickedPhotosFlow] only upserts the current picker batch and does not wipe them.
     *
     * Returns the number of rows removed. Safe to call when nothing is stale.
     */
    suspend fun reconcileWithMediaStore(): Int = withContext(Dispatchers.IO) {
        val liveIds = mediaStoreRepository.loadPhotos().map { it.id }.toHashSet()
        val dao = database.photoDao()
        val stale = dao.getAllPhotos()
            .filter { it.id.all(Char::isDigit) && it.id !in liveIds }
            .map { it.id }
        if (stale.isNotEmpty()) {
            Log.i(TAG, "Reconcile: removing ${stale.size} stale DB row(s) not in MediaStore")
            dao.deleteByIds(stale)
        } else {
            Log.d(TAG, "Reconcile: nothing stale, DB is clean")
        }
        stale.size
    }

    suspend fun runClipIndexing(contentResolver: ContentResolver): Int = withContext(Dispatchers.IO) {
        reconcileWithMediaStore()
        val photos = mediaStoreRepository.loadPhotos()
        val dao = database.photoDao()
        photos.forEach { photo ->
            val source = ImageDecoder.createSource(contentResolver, Uri.parse(photo.uri))
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // HARDWARE bitmaps are GPU-only; MediaPipe and ONNX Runtime both
                // need CPU-readable pixels, so force a software allocation.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val embedding = clipEncoder.embedImage(bitmap)
            dao.upsertPhoto(
                PhotoEntity(
                    id = photo.id,
                    uri = photo.uri,
                    takenAt = photo.takenAt,
                    lat = null,
                    lng = null,
                    width = photo.width,
                    height = photo.height,
                    indexedAt = System.currentTimeMillis(),
                    captionedAt = null
                )
            )
            dao.upsertClipEmbedding(ClipEmbeddingEntity(photo.id, embedding))
        }
        photos.size
    }

    suspend fun runCaptionIndexing(contentResolver: ContentResolver): Int = withContext(Dispatchers.IO) {
        reconcileWithMediaStore()
        val dao = database.photoDao()
        val photos = dao.getUncaptionedPhotos()
        photos.forEach { photo ->
            val photoUri = Uri.parse(photo.uri)
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // HARDWARE bitmaps are GPU-only; MediaPipe and ONNX Runtime both
                // need CPU-readable pixels, so force a software allocation.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val metadata = photoMetadataExtractor.extract(contentResolver, photoUri)
            val caption = gemmaCaptioner.caption(bitmap, metadata)
            val textEmbedding = textEncoder.embed(caption)
            dao.upsertCaptionBundle(
                CaptionEntity(photo.id, caption, "gemma-3n"),
                CaptionEmbeddingEntity(photo.id, textEmbedding)
            )
            dao.upsertPhoto(photo.copy(captionedAt = System.currentTimeMillis()))
        }
        photos.size
    }

    /**
     * For each picked [Uri] (max 50), upserts MobileCLIP image embedding, Gemma caption, and
     * MiniLM caption embedding. Existing DB rows for other photos are left unchanged.
     */
    fun indexPickedPhotosFlow(contentResolver: ContentResolver, uris: List<Uri>): Flow<IndexProgress> =
        flow {
            val dao = database.photoDao()
            val capped = uris.take(50)
            val total = capped.size
            Log.i(TAG, "indexPickedPhotosFlow: starting, total=$total")
            if (total == 0) return@flow
            for ((index, uri) in capped.withIndex()) {
                val current = index + 1
                val photoId = stablePhotoId(uri)
                val uriStr = uri.toString()
                Log.d(TAG, "[$current/$total] Processing URI: $uriStr  photoId=$photoId")
                emit(IndexProgress(current, total, uriStr, IndexPhase.Embedding, null))

                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = try {
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.also { Log.d(TAG, "[$current/$total] Decoded bitmap ${it.width}×${it.height}") }
                } catch (e: Exception) {
                    Log.e(TAG, "[$current/$total] Failed to decode bitmap for $uriStr", e)
                    throw e
                }
                val now = System.currentTimeMillis()
                val photo = PhotoEntity(
                    id = photoId,
                    uri = uriStr,
                    takenAt = null,
                    lat = null,
                    lng = null,
                    width = bitmap.width,
                    height = bitmap.height,
                    indexedAt = now,
                    captionedAt = null
                )
                dao.upsertPhoto(photo)
                Log.d(TAG, "[$current/$total] Upserted photo row, running CLIP embedding…")

                val clipEmbedding = try {
                    clipEncoder.embedImage(bitmap)
                        .also { Log.d(TAG, "[$current/$total] CLIP embedding done, dims=${it.size}") }
                } catch (e: Exception) {
                    Log.e(TAG, "[$current/$total] CLIP embedding failed", e)
                    throw e
                }
                dao.upsertClipEmbedding(ClipEmbeddingEntity(photoId, clipEmbedding))

                emit(IndexProgress(current, total, uriStr, IndexPhase.FaceDetection, null))
                val identityDao = database.identityDao()
                identityDao.deleteFacesForPhoto(photoId)
                val personRegions = faceDetector.detect(bitmap)
                val personCount = faceClusterer.indexRegions(
                    photoId, bitmap, personRegions, IdentityKind.PERSON
                )
                val petRegions = petRegionDetector.detect(bitmap)
                val petCount = faceClusterer.indexRegions(
                    photoId, bitmap, petRegions, IdentityKind.PET
                )
                val faceTotal = personCount + petCount
                val faceStatus = when (faceTotal) {
                    0 -> "No faces detected"
                    1 -> "1 face indexed"
                    else -> "$faceTotal faces indexed"
                }
                Log.d(TAG, "[$current/$total] $faceStatus (people=$personCount pets=$petCount)")
                dao.upsertPhoto(photo.copy(facesIndexedAt = now))
                emit(IndexProgress(current, total, uriStr, IndexPhase.FaceDetection, faceStatus))

                emit(IndexProgress(current, total, uriStr, IndexPhase.Captioning, null))
                Log.d(TAG, "[$current/$total] Extracting EXIF metadata…")
                val metadata = photoMetadataExtractor.extract(contentResolver, uri)
                // Persist GPS as soon as it is known so map/location features can query SQLite directly.
                dao.upsertPhoto(photo.copy(lat = metadata.lat, lng = metadata.lng))
                Log.d(
                    TAG,
                    "[$current/$total] Metadata indexed: lat=${metadata.lat} lng=${metadata.lng} location=${metadata.location}"
                )
                Log.d(TAG, "[$current/$total] Running Gemma caption…")

                val caption = try {
                    gemmaCaptioner.caption(bitmap, metadata)
                        .also { Log.d(TAG, "[$current/$total] Caption: \"$it\"") }
                } catch (e: Exception) {
                    Log.e(TAG, "[$current/$total] Gemma captioning failed", e)
                    throw e
                }

                Log.d(TAG, "[$current/$total] Running MiniLM caption embedding…")
                val textEmbedding = try {
                    textEncoder.embed(caption)
                        .also { Log.d(TAG, "[$current/$total] Caption embedding done, dims=${it.size}") }
                } catch (e: Exception) {
                    Log.e(TAG, "[$current/$total] Caption embedding failed", e)
                    throw e
                }

                dao.upsertCaptionBundle(
                    CaptionEntity(photoId, caption, "gemma-3n"),
                    CaptionEmbeddingEntity(photoId, textEmbedding)
                )
                dao.upsertPhoto(photo.copy(captionedAt = now))
                Log.i(TAG, "[$current/$total] Fully indexed photo $photoId")

                emit(IndexProgress(current, total, uriStr, IndexPhase.Captioning, caption))
            }
            Log.i(TAG, "indexPickedPhotosFlow: all $total photo(s) processed")
        }.flowOn(Dispatchers.IO)

    /**
     * Regenerate Gemma caption and MiniLM embedding for an existing indexed photo.
     */
    suspend fun recaptionPhoto(contentResolver: ContentResolver, photoId: String): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "recaptionPhoto: photoId=$photoId")
            val dao = database.photoDao()
            val photo = dao.getPhotoById(photoId)
                ?: error("Photo not found: $photoId")
            val photoUri = Uri.parse(photo.uri)
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val metadata = photoMetadataExtractor.extract(contentResolver, photoUri)
            val caption = gemmaCaptioner.caption(bitmap, metadata)
            Log.d(TAG, "recaptionPhoto: new caption=\"$caption\"")
            val textEmbedding = textEncoder.embed(caption)
            dao.upsertCaptionBundle(
                CaptionEntity(photoId, caption, "gemma-3n"),
                CaptionEmbeddingEntity(photoId, textEmbedding)
            )
            dao.upsertPhoto(photo.copy(captionedAt = System.currentTimeMillis()))
            Log.i(TAG, "recaptionPhoto: done for $photoId")
            caption
        }

    suspend fun countIndexedFaces(): Int = withContext(Dispatchers.IO) {
        database.identityDao().countFaces()
    }

    /** Wipe every table — photos, embeddings, captions, identities, face thumbnails. */
    suspend fun clearAllData(): Unit = withContext(Dispatchers.IO) {
        Log.i(TAG, "clearAllData: nuking DB")
        database.photoDao().clearAll(database.identityDao())
        FaceThumbnailStore.deleteAll(appContext)
        Log.i(TAG, "clearAllData: done")
    }

    private fun stablePhotoId(uri: Uri): String =
        UUID.nameUUIDFromBytes(uri.toString().toByteArray(Charsets.UTF_8)).toString()
}
