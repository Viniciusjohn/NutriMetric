package br.com.nutrimetric.app

import br.com.nutrimetric.app.utils.NutritionCalculator
import br.com.nutrimetric.app.utils.NutritionCalculator.ActivityLevel
import br.com.nutrimetric.app.utils.NutritionCalculator.Goal
import br.com.nutrimetric.app.utils.NutritionCalculator.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class NutritionCalculatorTest {

    @Test
    fun `BMR masculino segue Mifflin-St Jeor`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        val bmr = NutritionCalculator.calculateBmr(weightKg = 80.0, heightCm = 180.0, age = 30, sex = Sex.MALE)
        assertEquals(1780.0, bmr, 0.01)
    }

    @Test
    fun `BMR feminino segue Mifflin-St Jeor`() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        val bmr = NutritionCalculator.calculateBmr(weightKg = 60.0, heightCm = 165.0, age = 25, sex = Sex.FEMALE)
        assertEquals(1345.25, bmr, 0.01)
    }

    @Test
    fun `TDEE aplica o fator de atividade sobre a TMB`() {
        val bmr = 1780.0
        assertEquals(1780.0 * 1.2, NutritionCalculator.calculateTdee(bmr, ActivityLevel.SEDENTARY), 0.01)
        assertEquals(1780.0 * 1.55, NutritionCalculator.calculateTdee(bmr, ActivityLevel.MODERATE), 0.01)
        assertEquals(1780.0 * 1.9, NutritionCalculator.calculateTdee(bmr, ActivityLevel.VERY_ACTIVE), 0.01)
    }

    @Test
    fun `manter peso usa o TDEE como meta calorica`() {
        val bmr = NutritionCalculator.calculateBmr(80.0, 180.0, 30, Sex.MALE)
        val tdee = NutritionCalculator.calculateTdee(bmr, ActivityLevel.MODERATE)
        val goals = NutritionCalculator.calculateGoals(80.0, 180.0, 30, Sex.MALE, ActivityLevel.MODERATE, Goal.MAINTAIN)
        assertEquals(tdee.roundToInt(), goals.calories)
    }

    @Test
    fun `perder peso reduz 500kcal do TDEE`() {
        val bmr = NutritionCalculator.calculateBmr(80.0, 180.0, 30, Sex.MALE)
        val tdee = NutritionCalculator.calculateTdee(bmr, ActivityLevel.MODERATE)
        val goals = NutritionCalculator.calculateGoals(80.0, 180.0, 30, Sex.MALE, ActivityLevel.MODERATE, Goal.LOSE)
        assertEquals((tdee - 500).roundToInt(), goals.calories)
    }

    @Test
    fun `ganhar peso soma 500kcal ao TDEE`() {
        val bmr = NutritionCalculator.calculateBmr(70.0, 175.0, 28, Sex.MALE)
        val tdee = NutritionCalculator.calculateTdee(bmr, ActivityLevel.ACTIVE)
        val goals = NutritionCalculator.calculateGoals(70.0, 175.0, 28, Sex.MALE, ActivityLevel.ACTIVE, Goal.GAIN)
        assertEquals((tdee + 500).roundToInt(), goals.calories)
    }

    @Test
    fun `meta calorica nunca fica abaixo do piso de seguranca`() {
        // Perfil pequeno + sedentário + déficit: TDEE - 500 ficaria bem abaixo de 1200
        val goals = NutritionCalculator.calculateGoals(
            weightKg = 60.0, heightCm = 165.0, age = 25, sex = Sex.FEMALE,
            activityLevel = ActivityLevel.SEDENTARY, goal = Goal.LOSE
        )
        assertTrue("Meta calórica não pode ficar abaixo de 1200 kcal", goals.calories >= 1200)
    }

    @Test
    fun `proteina segue 2g por kg de peso corporal`() {
        val goals = NutritionCalculator.calculateGoals(80.0, 180.0, 30, Sex.MALE, ActivityLevel.MODERATE, Goal.MAINTAIN)
        assertEquals((80.0 * 2.0).roundToInt(), goals.protein)
    }

    @Test
    fun `macros somam aproximadamente a meta calorica`() {
        val goals = NutritionCalculator.calculateGoals(80.0, 180.0, 30, Sex.MALE, ActivityLevel.MODERATE, Goal.MAINTAIN)
        val macroCalories = goals.protein * 4 + goals.carbs * 4 + goals.fat * 9
        assertTrue(
            "Soma dos macros (${macroCalories}) deveria ficar perto da meta calórica (${goals.calories})",
            Math.abs(macroCalories - goals.calories) <= 5
        )
    }

    @Test
    fun `meta de agua e proporcional ao peso e respeita limites`() {
        val leve = NutritionCalculator.calculateGoals(50.0, 160.0, 25, Sex.FEMALE, ActivityLevel.SEDENTARY, Goal.MAINTAIN)
        val pesado = NutritionCalculator.calculateGoals(120.0, 190.0, 30, Sex.MALE, ActivityLevel.MODERATE, Goal.MAINTAIN)
        assertTrue(leve.waterMl in 1500..4000)
        assertTrue(pesado.waterMl in 1500..4000)
        assertTrue(pesado.waterMl > leve.waterMl)
    }
}
