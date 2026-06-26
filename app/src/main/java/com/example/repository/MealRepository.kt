package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.MealDao
import com.example.data.local.MealEntity
import com.example.data.remote.Alimento
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream

class MealRepository(
    private val context: Context,
    private val mealDao: MealDao,
    private val dailyConsumptionDao: com.example.data.local.DailyConsumptionDao
) {

    suspend fun saveMeal(
        imagePath: String,
        imageBase64: String, // Kept for interface compatibility but we clear it in db
        alimentos: List<Alimento>,
        totalMilliKcal: Long,
        totalMilliProtein: Long,
        totalMilliCarbs: Long,
        totalMilliFat: Long,
        date: String = java.time.LocalDate.now().toString()
    ): Long {
        // Grava o arquivo físico de imagem no disco interno do dispositivo
        val finalImagePath = if (imagePath.isNotEmpty()) {
            saveImageToInternalStorage(imagePath)
        } else {
            ""
        }

        val mealEntity = MealEntity(
            imagePath = finalImagePath,
            imageBase64 = "", // ALWAYS save empty string to prevent CursorWindow memory limit crash (2MB)
            mealType = "Refeição",
            totalMilliKcal = totalMilliKcal,
            totalMilliProtein = totalMilliProtein,
            totalMilliCarbs = totalMilliCarbs,
            totalMilliFat = totalMilliFat,
            confidence = 1.0,
            foodsJson = convertFoodsToJson(alimentos),
            date = date
        )
        val mealId = mealDao.insertMeal(mealEntity)

        // Sincroniza e insere na tabela de consumo diário (DailyConsumptionEntity)
        dailyConsumptionDao.insert(
            com.example.data.local.DailyConsumptionEntity(
                mealId = mealId,
                date = date,
                kcal = totalMilliKcal / 1000.0,
                protein = totalMilliProtein / 1000.0,
                carbohydrate = totalMilliCarbs / 1000.0,
                lipid = totalMilliFat / 1000.0
            )
        )

        return mealId
    }

    private fun saveImageToInternalStorage(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return uriString
            val mealsDir = File(context.filesDir, "meals").apply { if (!exists()) mkdirs() }
            val permanentFile = File(mealsDir, "meal_${System.currentTimeMillis()}.jpg")
            
            FileOutputStream(permanentFile).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            Uri.fromFile(permanentFile).toString()
        } catch (e: Exception) {
            android.util.Log.e("MealRepository", "Erro ao copiar arquivo físico da imagem", e)
            uriString
        }
    }

    suspend fun getMealsByDate(date: String): List<MealEntity> {
        return mealDao.getMealsByDate(date)
    }

    suspend fun getTodayMeals(): List<MealEntity> {
        val today = java.time.LocalDate.now().toString()
        return mealDao.getMealsByDate(today)
    }

    fun getTodayMealsFlow(): kotlinx.coroutines.flow.Flow<List<MealEntity>> {
            val today = java.time.LocalDate.now().toString()
            return mealDao.getMealsByDateFlow(today)
        }

        fun getMealsFlowByDate(date: String): kotlinx.coroutines.flow.Flow<List<MealEntity>> {
            return mealDao.getMealsByDateFlow(date)
        }

    suspend fun deleteMeal(mealId: Long) {
        mealDao.deleteMealById(mealId)
        dailyConsumptionDao.deleteByMealId(mealId)
    }

    suspend fun getTodayTotals(): DailyTotals {
        val today = java.time.LocalDate.now().toString()
        return DailyTotals(
            milliKcal = mealDao.getTotalMilliKcalByDate(today) ?: 0L,
            milliProtein = mealDao.getTotalMilliProteinByDate(today) ?: 0L,
            milliCarbs = mealDao.getTotalMilliCarbsByDate(today) ?: 0L,
            milliFat = mealDao.getTotalMilliFatByDate(today) ?: 0L,
            mealCount = mealDao.getMealCountByDate(today)
        )
    }

    private fun convertFoodsToJson(foods: List<Alimento>): String {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter<List<Alimento>>(
            Types.newParameterizedType(List::class.java, Alimento::class.java)
        )
        return adapter.toJson(foods)
    }

    fun parseFoodsFromJson(json: String): List<Alimento> {
        return try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter<List<Alimento>>(
                Types.newParameterizedType(List::class.java, Alimento::class.java)
            )
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("MealRepository", "Error parsing foods json", e)
            emptyList()
        }
    }
}

data class DailyTotals(
    val milliKcal: Long,
    val milliProtein: Long,
    val milliCarbs: Long,
    val milliFat: Long,
    val mealCount: Int
)
