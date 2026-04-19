package com.srini.wheresthatphoto.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

data class DevicePhoto(
    val id: String,
    val uri: String,
    val width: Int?,
    val height: Int?,
    val takenAt: Long?
)

class MediaStoreRepository(private val context: Context) {
    fun loadPhotos(limit: Int = 3000): List<DevicePhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val photos = mutableListOf<DevicePhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val takenAtIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext() && photos.size < limit) {
                val id = cursor.getLong(idIdx)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                photos.add(
                    DevicePhoto(
                        id = id.toString(),
                        uri = contentUri.toString(),
                        width = cursor.getInt(widthIdx),
                        height = cursor.getInt(heightIdx),
                        takenAt = cursor.getLong(takenAtIdx)
                    )
                )
            }
        }
        return photos
    }
}
