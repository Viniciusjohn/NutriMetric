package br.com.nutrimetric.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NutritionGoals(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nutrition_goals")

class GoalsRepository(private val context: Context) {

    companion object {
        private val KEY_CALORIES = intPreferencesKey("goal_calories")
        private val KEY_PROTEIN = intPreferencesKey("goal_protein")
        private val KEY_CARBS = intPreferencesKey("goal_carbs")
        private val KEY_FAT = intPreferencesKey("goal_fat")
        
        val DEFAULT_GOALS = NutritionGoals(
            calories = 2000,
            protein = 130,
            carbs = 220,
            fat = 65
        )
    }

    val goalsFlow: Flow<NutritionGoals> = context.dataStore.data
        .map { preferences ->
            NutritionGoals(
                calories = preferences[KEY_CALORIES] ?: DEFAULT_GOALS.calories,
                protein = preferences[KEY_PROTEIN] ?: DEFAULT_GOALS.protein,
                carbs = preferences[KEY_CARBS] ?: DEFAULT_GOALS.carbs,
                fat = preferences[KEY_FAT] ?: DEFAULT_GOALS.fat
            )
        }

    suspend fun saveGoals(goals: NutritionGoals) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CALORIES] = goals.calories
            preferences[KEY_PROTEIN] = goals.protein
            preferences[KEY_CARBS] = goals.carbs
            preferences[KEY_FAT] = goals.fat
        }
    }
}
