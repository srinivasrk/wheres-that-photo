package com.srini.wheresthatphoto.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object FaceThumbnailStore {
    private const val DIR_NAME = "faces"
    private const val THUMB_SIZE = 128

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun fileFor(context: Context, faceId: String): File =
        File(dir(context), "$faceId.jpg")

    fun save(context: Context, faceId: String, crop: Bitmap): String {
        val thumb = Bitmap.createScaledBitmap(crop, THUMB_SIZE, THUMB_SIZE, true)
        if (thumb != crop) crop.recycle()
        val out = fileFor(context, faceId)
        FileOutputStream(out).use { fos ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        thumb.recycle()
        return out.absolutePath
    }

    fun deleteAll(context: Context) {
        val d = dir(context)
        d.listFiles()?.forEach { it.delete() }
    }
}
