package br.com.nutrimetric.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null) return emptyList()
        return value.split(",").map { it.trim() }
    }

    @TypeConverter
    fun toString(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
}
