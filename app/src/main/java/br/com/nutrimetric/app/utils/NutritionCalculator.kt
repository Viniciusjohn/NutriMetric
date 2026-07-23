package br.com.nutrimetric.app.utils

import br.com.nutrimetric.app.repository.NutritionGoals
import kotlin.math.roundToInt

/**
 * Calcula metas nutricionais diárias a partir de dados do onboarding, usando
 * a equação de Mifflin-St Jeor (TMB) — a mais usada em contextos clínicos
 * modernos, mais precisa que Harris-Benedict para a maioria dos adultos.
 *
 * Estimativa, não prescrição — o disclaimer médico do app cobre isso.
 */
object NutritionCalculator {

    enum class Sex { MALE, FEMALE }

    enum class ActivityLevel(val factor: Double) {
        SEDENTARY(1.2),       // pouco ou nenhum exercício
        LIGHT(1.375),         // exercício leve 1-3x/semana
        MODERATE(1.55),       // exercício moderado 3-5x/semana
        ACTIVE(1.725),        // exercício intenso 6-7x/semana
        VERY_ACTIVE(1.9)      // exercício muito intenso + trabalho físico
    }

    enum class Goal { LOSE, MAINTAIN, GAIN }

    private const val KCAL_DEFICIT_OR_SURPLUS = 500
    private const val PROTEIN_G_PER_KG = 2.0
    private const val FAT_CALORIE_FRACTION = 0.25

    /** Taxa Metabólica Basal (kcal/dia), Mifflin-St Jeor. */
    fun calculateBmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
    }

    /** Gasto Energético Total Diário (kcal/dia) = TMB × fator de atividade. */
    fun calculateTdee(bmr: Double, activityLevel: ActivityLevel): Double =
        bmr * activityLevel.factor

    /**
     * Metas nutricionais completas a partir do perfil do onboarding.
     * Proteína ~2g/kg, gordura ~25% das calorias, carboidrato no restante.
     */
    fun calculateGoals(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        sex: Sex,
        activityLevel: ActivityLevel,
        goal: Goal
    ): NutritionGoals {
        val bmr = calculateBmr(weightKg, heightCm, age, sex)
        val tdee = calculateTdee(bmr, activityLevel)

        val targetCalories = when (goal) {
            Goal.LOSE -> tdee - KCAL_DEFICIT_OR_SURPLUS
            Goal.MAINTAIN -> tdee
            Goal.GAIN -> tdee + KCAL_DEFICIT_OR_SURPLUS
        }.coerceAtLeast(1200.0) // piso de segurança, evita metas irrealistas/perigosas

        val proteinG = weightKg * PROTEIN_G_PER_KG
        val fatKcal = targetCalories * FAT_CALORIE_FRACTION
        val fatG = fatKcal / 9.0
        val proteinKcal = proteinG * 4.0
        val carbsKcal = (targetCalories - proteinKcal - fatKcal).coerceAtLeast(0.0)
        val carbsG = carbsKcal / 4.0

        return NutritionGoals(
            calories = targetCalories.roundToInt(),
            protein = proteinG.roundToInt(),
            carbs = carbsG.roundToInt(),
            fat = fatG.roundToInt(),
            waterMl = (weightKg * 35).roundToInt().coerceIn(1500, 4000)
        )
    }
}
