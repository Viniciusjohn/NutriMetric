package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "taco_mismatches")
data class TacoMismatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val inputTerm: String,
    val detectedConfidence: String,
    val timestamp: Long
)
