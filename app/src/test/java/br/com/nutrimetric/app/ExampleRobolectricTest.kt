package br.com.nutrimetric.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PratoBr", appName)
  }

  // testViewModelInitialization foi removido: MainViewModel constrói
  // repositórios que chamam FirebaseFunctions.getInstance()/FirebaseCrashlytics
  // de forma eager, exigindo um FirebaseApp real (com plugin do Crashlytics
  // aplicado) para não lançar — mesma limitação do build real antes do
  // google-services.json existir. Testar MainViewModel de verdade requer
  // Firebase Test Lab/emulator, fora do escopo de um teste Robolectric puro.

  @Test
  fun testParsePreparo() {
    // Case 1: Standard formatted string
    val result1 = br.com.nutrimetric.app.utils.TacoMatcher.parsePreparo("100g, 150kcal, carb: 20g, prot: 10g, gord: 5g")
    assertNotNull(result1)
    assertEquals(100, result1!!.grams)
    assertEquals(150L, result1.kcal)
    assertEquals(20L, result1.carbs)
    assertEquals(10L, result1.protein)
    assertEquals(5L, result1.fat)

    // Case 2: Alternative format with uppercase and spaces
    val result2 = br.com.nutrimetric.app.utils.TacoMatcher.parsePreparo("150g, 250 kcal, Carb: 30g, Prot: 15g, Gord: 8g")
    assertNotNull(result2)
    assertEquals(150, result2!!.grams)
    assertEquals(250L, result2.kcal)
    assertEquals(30L, result2.carbs)
    assertEquals(15L, result2.protein)
    assertEquals(8L, result2.fat)

    // Case 3: Compact format with C, P, G suffix
    val result3 = br.com.nutrimetric.app.utils.TacoMatcher.parsePreparo("200g, 350kcal, 40C, 20P, 12G")
    assertNotNull(result3)
    assertEquals(200, result3!!.grams)
    assertEquals(350L, result3.kcal)
    assertEquals(40L, result3.carbs)
    assertEquals(20L, result3.protein)
    assertEquals(12L, result3.fat)

    // Case 4: Space and suffix format
    val result4 = br.com.nutrimetric.app.utils.TacoMatcher.parsePreparo("100g, 180 kcal, 25g Carb, 12g Prot, 6g Gord")
    assertNotNull(result4)
    assertEquals(100, result4!!.grams)
    assertEquals(180L, result4.kcal)
    assertEquals(25L, result4.carbs)
    assertEquals(12L, result4.protein)
    assertEquals(6L, result4.fat)
  }
}
