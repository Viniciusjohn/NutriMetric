package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface TacoMismatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMismatch(mismatch: TacoMismatchEntity)
}
