package com.srini.wheresthatphoto.data

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun floatArrayToString(value: FloatArray?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun stringToFloatArray(value: String?): FloatArray? {
        if (value.isNullOrBlank()) return null
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }
}
