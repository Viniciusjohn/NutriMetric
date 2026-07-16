package br.com.nutrimetric.app.repository

import android.content.Context
import android.net.Uri
import br.com.nutrimetric.app.data.local.MealDao
import br.com.nutrimetric.app.data.local.MealEntity
import br.com.nutrimetric.app.data.remote.Alimento
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream

class MealRepository(
    private val context: Context,
    private val mealDao: MealDao,
    private val dailyConsumptionDao: br.com.nutrimetric.app.data.local.DailyConsumptionDao
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
            br.com.nutrimetric.app.data.local.DailyConsumptionEntity(
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

    suspend fun getMealById(mealId: Long): MealEntity? = mealDao.getMealById(mealId)

    /**
     * Atualiza uma refeição já salva (via PlateReviewScreen em modo edição).
     * Espelha [saveMeal], mas substitui o registro existente em vez de inserir
     * um novo, e recria a entrada de consumo diário correspondente.
     */
    suspend fun updateMeal(
        mealId: Long,
        alimentos: List<Alimento>,
        totalMilliKcal: Long,
        totalMilliProtein: Long,
        totalMilliCarbs: Long,
        totalMilliFat: Long
    ) {
        val existing = mealDao.getMealById(mealId) ?: return
        mealDao.updateMeal(
            existing.copy(
                totalMilliKcal = totalMilliKcal,
                totalMilliProtein = totalMilliProtein,
                totalMilliCarbs = totalMilliCarbs,
                totalMilliFat = totalMilliFat,
                foodsJson = convertFoodsToJson(alimentos)
            )
        )

        dailyConsumptionDao.deleteByMealId(mealId)
        dailyConsumptionDao.insert(
            br.com.nutrimetric.app.data.local.DailyConsumptionEntity(
                mealId = mealId,
                date = existing.date,
                kcal = totalMilliKcal / 1000.0,
                protein = totalMilliProtein / 1000.0,
                carbohydrate = totalMilliCarbs / 1000.0,
                lipid = totalMilliFat / 1000.0
            )
        )
    }

    /** Duplica uma refeição existente para hoje, sem precisar refotografar. */
    suspend fun repeatMealToday(mealId: Long): Long? {
        val existing = mealDao.getMealById(mealId) ?: return null
        val alimentos = parseFoodsFromJson(existing.foodsJson)
        return saveMeal(
            imagePath = "",
            imageBase64 = "",
            alimentos = alimentos,
            totalMilliKcal = existing.totalMilliKcal,
            totalMilliProtein = existing.totalMilliProtein,
            totalMilliCarbs = existing.totalMilliCarbs,
            totalMilliFat = existing.totalMilliFat
        )
    }

    suspend fun setFavorite(mealId: Long, favorite: Boolean) {
        mealDao.setFavorite(mealId, favorite)
    }

    fun getFavoritesFlow(): kotlinx.coroutines.flow.Flow<List<MealEntity>> = mealDao.getFavoritesFlow()

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
