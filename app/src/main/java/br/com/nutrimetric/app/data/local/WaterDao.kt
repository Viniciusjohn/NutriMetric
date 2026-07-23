package br.com.nutrimetric.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(water: WaterEntity)

    /** Total de água (ml) consumido em um dia. */
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_intake WHERE date = :date")
    fun getTotalForDate(date: String): Flow<Int>

    /** Remove o registro mais recente do dia (desfazer o último copo). */
    @Query("""
        DELETE FROM water_intake
        WHERE id = (
            SELECT id FROM water_intake
            WHERE date = :date
            ORDER BY timestamp DESC
            LIMIT 1
        )
    """)
    suspend fun deleteLastForDate(date: String)
}
