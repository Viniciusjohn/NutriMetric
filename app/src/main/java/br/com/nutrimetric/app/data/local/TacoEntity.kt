package br.com.nutrimetric.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "taco")
data class TacoEntity(
    @PrimaryKey val id: Int,
    val description: String,
    @ColumnInfo(name = "kcal") val energyKcal: Double?,
    @ColumnInfo(name = "protein") val protein: Double?,
    @ColumnInfo(name = "carbohydrate") val carbohydrate: Double?,
    @ColumnInfo(name = "lipid") val lipid: Double?
)
