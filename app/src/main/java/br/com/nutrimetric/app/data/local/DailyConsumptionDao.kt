package br.com.nutrimetric.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyConsumptionDao {
    @Insert
    suspend fun insert(consumption: DailyConsumptionEntity)

    @Query("DELETE FROM daily_consumption WHERE mealId = :mealId")
    suspend fun deleteByMealId(mealId: Long)

    @Query("""
        SELECT 
            COALESCE(SUM(kcal), 0.0) as kcal, 
            COALESCE(SUM(protein), 0.0) as protein, 
            COALESCE(SUM(carbohydrate), 0.0) as carbohydrate, 
            COALESCE(SUM(lipid), 0.0) as lipid 
        FROM daily_consumption 
        WHERE date = :date
    """)
    fun getDailyTotals(date: String): Flow<DailyTotals>

    @Query("""
        SELECT 
            date,
            COALESCE(SUM(kcal), 0.0) as kcal, 
            COALESCE(SUM(protein), 0.0) as protein, 
            COALESCE(SUM(carbohydrate), 0.0) as carbohydrate, 
            COALESCE(SUM(lipid), 0.0) as lipid 
        FROM daily_consumption 
        GROUP BY date 
        ORDER BY date DESC
    """)
    fun getAllDailyTotalsGroupedByDate(): Flow<List<DateDailyTotals>>
}
