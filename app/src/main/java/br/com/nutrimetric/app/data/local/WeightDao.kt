package br.com.nutrimetric.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(weight: WeightEntity)

    /** Registro de peso mais recente (para o card de peso atual). */
    @Query("SELECT * FROM weight_log ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<WeightEntity?>

    /** Os dois registros mais recentes, para calcular a variação. */
    @Query("SELECT * FROM weight_log ORDER BY timestamp DESC LIMIT 2")
    fun getRecent(): Flow<List<WeightEntity>>

    /** Histórico completo em ordem cronológica (para o gráfico). */
    @Query("SELECT * FROM weight_log ORDER BY timestamp ASC")
    fun getAllForChart(): Flow<List<WeightEntity>>
}
