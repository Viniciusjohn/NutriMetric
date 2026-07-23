package br.com.nutrimetric.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Registro de peso corporal do usuário em uma data. */
@Entity(tableName = "weight_log")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Formato YYYY-MM-DD
    val weightKg: Double,
    val timestamp: Long = System.currentTimeMillis()
)
