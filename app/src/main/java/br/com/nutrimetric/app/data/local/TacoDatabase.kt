package br.com.nutrimetric.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TacoEntity::class, DailyConsumptionEntity::class], version = 2, exportSchema = false)
abstract class TacoDatabase : RoomDatabase() {
    abstract fun tacoDao(): TacoDao
    abstract fun dailyConsumptionDao(): DailyConsumptionDao

    companion object {
        fun getDatabase(context: Context): TacoDatabase {
            return DatabaseProvider.getTacoDatabase(context)
        }
    }
}
