package br.com.nutrimetric.app.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserProfile(
    val heightCm: Int,
    val age: Int,
    val sex: String,           // "M" | "F"
    val activityLevel: String, // sedentary | light | moderate | active | very_active
    val goal: String,          // lose | maintain | gain
    val onboardingCompleted: Boolean
)

/**
 * Perfil físico usado pelo onboarding para calcular metas (ver
 * utils/NutritionCalculator.kt) e como contexto do chat com a Nutri IA.
 * O peso vive à parte, em WeightEntity/WeightDao (Room) — já é a fonte
 * usada pelo card de peso, não duplicamos aqui.
 *
 * Reusa o mesmo DataStore de GoalsRepository (Context.dataStore), já que
 * ambos guardam preferências simples do usuário, sem necessidade de tabelas
 * separadas.
 */
class ProfileRepository(private val context: Context) {

    companion object {
        private val KEY_HEIGHT_CM = intPreferencesKey("profile_height_cm")
        private val KEY_AGE = intPreferencesKey("profile_age")
        private val KEY_SEX = stringPreferencesKey("profile_sex")
        private val KEY_ACTIVITY_LEVEL = stringPreferencesKey("profile_activity_level")
        private val KEY_GOAL = stringPreferencesKey("profile_goal")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("profile_onboarding_completed")

        val DEFAULT_PROFILE = UserProfile(
            heightCm = 170,
            age = 30,
            sex = "F",
            activityLevel = "moderate",
            goal = "maintain",
            onboardingCompleted = false
        )
    }

    val profileFlow: Flow<UserProfile> = context.dataStore.data
        .map { preferences ->
            UserProfile(
                heightCm = preferences[KEY_HEIGHT_CM] ?: DEFAULT_PROFILE.heightCm,
                age = preferences[KEY_AGE] ?: DEFAULT_PROFILE.age,
                sex = preferences[KEY_SEX] ?: DEFAULT_PROFILE.sex,
                activityLevel = preferences[KEY_ACTIVITY_LEVEL] ?: DEFAULT_PROFILE.activityLevel,
                goal = preferences[KEY_GOAL] ?: DEFAULT_PROFILE.goal,
                onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: DEFAULT_PROFILE.onboardingCompleted
            )
        }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HEIGHT_CM] = profile.heightCm
            preferences[KEY_AGE] = profile.age
            preferences[KEY_SEX] = profile.sex
            preferences[KEY_ACTIVITY_LEVEL] = profile.activityLevel
            preferences[KEY_GOAL] = profile.goal
            preferences[KEY_ONBOARDING_COMPLETED] = profile.onboardingCompleted
        }
    }
}
