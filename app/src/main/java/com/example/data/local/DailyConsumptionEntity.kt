package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_consumption")
data class DailyConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long? = null,
    val date: String, // Formato YYYY-MM-DD
    val kcal: Double,
    val protein: Double,
    val carbohydrate: Double,
    val lipid: Double,
    val timestamp: Long = System.currentTimeMillis()
)
