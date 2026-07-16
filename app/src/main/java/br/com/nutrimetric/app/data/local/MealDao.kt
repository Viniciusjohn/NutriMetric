package br.com.nutrimetric.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity): Long
    
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getMealsByDate(date: String): List<MealEntity>

    @Query("SELECT * FROM meals WHERE date = :date ORDER BY timestamp DESC")
    fun getMealsByDateFlow(date: String): Flow<List<MealEntity>>
    
    @Query("SELECT * FROM meals ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMeals(limit: Int = 50): List<MealEntity>
    
    @Delete
    suspend fun deleteMeal(meal: MealEntity)
    
    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMealById(mealId: Long)
    
    @Query("SELECT SUM(totalMilliKcal) FROM meals WHERE date = :date")
    suspend fun getTotalMilliKcalByDate(date: String): Long?
    
    @Query("SELECT SUM(totalMilliProtein) FROM meals WHERE date = :date")
    suspend fun getTotalMilliProteinByDate(date: String): Long?
    
    @Query("SELECT SUM(totalMilliCarbs) FROM meals WHERE date = :date")
    suspend fun getTotalMilliCarbsByDate(date: String): Long?
    
    @Query("SELECT SUM(totalMilliFat) FROM meals WHERE date = :date")
    suspend fun getTotalMilliFatByDate(date: String): Long?
    
    @Query("SELECT COUNT(*) FROM meals WHERE date = :date")
    suspend fun getMealCountByDate(date: String): Int
}
