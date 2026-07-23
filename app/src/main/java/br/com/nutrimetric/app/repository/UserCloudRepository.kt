package br.com.nutrimetric.app.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Perfil do cliente na nuvem (Firestore `users/{uid}`). É o "banco de clientes":
 * o onboarding grava aqui além do DataStore local, e no login o app hidrata o
 * local a partir daqui — então quem troca de aparelho (ou reinstala e loga de
 * novo) não refaz o questionário.
 *
 * Tudo é no-op silencioso sem Firebase configurado ou sem usuário logado, pra
 * não quebrar o "modo dev" (sem google-services.json). Os campos gravados aqui
 * estão na whitelist de `firestore.rules`; isPremium/quota NÃO são tocados pelo
 * cliente (só pelas Cloud Functions via Admin SDK).
 */
class UserCloudRepository(private val context: Context) {

    private fun isAvailable(): Boolean = FirebaseApp.getApps(context).isNotEmpty()
    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Espelha o perfil + metas + peso do onboarding no Firestore. */
    suspend fun saveProfile(profile: UserProfile, goals: NutritionGoals, weightKg: Double) {
        if (!isAvailable()) return
        val uid = currentUid() ?: return
        try {
            val data = mapOf(
                "heightCm" to profile.heightCm,
                "age" to profile.age,
                "sex" to profile.sex,
                "activityLevel" to profile.activityLevel,
                "goal" to profile.goal,
                "onboardingCompleted" to true,
                "weightKg" to weightKg,
                "goalCalories" to goals.calories,
                "goalProtein" to goals.protein,
                "goalCarbs" to goals.carbs,
                "goalFat" to goals.fat,
                "goalWaterMl" to goals.waterMl,
                "profileUpdatedAt" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance()
                .document("users/$uid")
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w("UserCloudRepository", "Falha ao salvar perfil na nuvem", e)
        }
    }

    data class CloudProfile(val profile: UserProfile, val goals: NutritionGoals)

    /** Lê o perfil da nuvem; null se não existe, incompleto ou sem Firebase. */
    suspend fun fetchProfile(): CloudProfile? {
        if (!isAvailable()) return null
        val uid = currentUid() ?: return null
        return try {
            val snap = FirebaseFirestore.getInstance().document("users/$uid").get().await()
            if (snap.getBoolean("onboardingCompleted") != true) return null
            val profile = UserProfile(
                heightCm = (snap.getLong("heightCm") ?: 170L).toInt(),
                age = (snap.getLong("age") ?: 30L).toInt(),
                sex = snap.getString("sex") ?: "F",
                activityLevel = snap.getString("activityLevel") ?: "moderate",
                goal = snap.getString("goal") ?: "maintain",
                onboardingCompleted = true
            )
            val goals = NutritionGoals(
                calories = (snap.getLong("goalCalories") ?: GoalsRepository.DEFAULT_GOALS.calories.toLong()).toInt(),
                protein = (snap.getLong("goalProtein") ?: GoalsRepository.DEFAULT_GOALS.protein.toLong()).toInt(),
                carbs = (snap.getLong("goalCarbs") ?: GoalsRepository.DEFAULT_GOALS.carbs.toLong()).toInt(),
                fat = (snap.getLong("goalFat") ?: GoalsRepository.DEFAULT_GOALS.fat.toLong()).toInt(),
                waterMl = (snap.getLong("goalWaterMl") ?: GoalsRepository.DEFAULT_GOALS.waterMl.toLong()).toInt()
            )
            CloudProfile(profile, goals)
        } catch (e: Exception) {
            Log.w("UserCloudRepository", "Falha ao ler perfil da nuvem", e)
            null
        }
    }
}
