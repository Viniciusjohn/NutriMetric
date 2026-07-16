package br.com.nutrimetric.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cada registro representa uma adição de água (um copo, uma garrafa, etc.). */
@Entity(tableName = "water_intake")
data class WaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Formato YYYY-MM-DD
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis()
)
