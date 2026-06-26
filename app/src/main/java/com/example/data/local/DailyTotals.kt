package com.example.data.local

data class DailyTotals(
    val kcal: Double,
    val protein: Double,
    val carbohydrate: Double,
    val lipid: Double
)

data class DateDailyTotals(
    val date: String,
    val kcal: Double,
    val protein: Double,
    val carbohydrate: Double,
    val lipid: Double
)

