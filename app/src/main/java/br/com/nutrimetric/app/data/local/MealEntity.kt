package br.com.nutrimetric.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val imagePath: String,
    val imageBase64: String,
    
    val mealType: String,
    
    val totalMilliKcal: Long,
    val totalMilliProtein: Long,
    val totalMilliCarbs: Long,
    val totalMilliFat: Long,
    
    val confidence: Double,
    
    val foodsJson: String,
    
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = java.time.LocalDate.now().toString()
)

