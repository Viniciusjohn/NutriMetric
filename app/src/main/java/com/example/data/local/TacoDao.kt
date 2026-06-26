package com.example.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TacoDao {
    @Query("SELECT * FROM taco")
    suspend fun getAllFoods(): List<TacoEntity>

    @Query("SELECT * FROM taco WHERE description LIKE '%' || :query || '%' COLLATE NOCASE LIMIT 10")
    suspend fun searchFoods(query: String): List<TacoEntity>
    
    @Query("SELECT description FROM taco")
    suspend fun getAllDescriptions(): List<String>
    
    @Query("SELECT * FROM taco WHERE description = :description LIMIT 1")
    suspend fun getFoodByDescription(description: String): TacoEntity?
}
