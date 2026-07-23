package br.com.nutrimetric.app

import br.com.nutrimetric.app.utils.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 7, 16)

    @Test
    fun `lista vazia retorna zero`() {
        assertEquals(0, StreakCalculator.calculate(emptyList(), today))
    }

    @Test
    fun `dias consecutivos terminando hoje contam todos`() {
        val dates = listOf("2026-07-14", "2026-07-15", "2026-07-16")
        assertEquals(3, StreakCalculator.calculate(dates, today))
    }

    @Test
    fun `sem registro hoje mas com registro ontem mantem streak`() {
        val dates = listOf("2026-07-14", "2026-07-15")
        assertEquals(2, StreakCalculator.calculate(dates, today))
    }

    @Test
    fun `intervalo maior que um dia zera o streak`() {
        // Último registro há 3 dias — quebrou a sequência
        val dates = listOf("2026-07-10", "2026-07-11", "2026-07-12")
        assertEquals(0, StreakCalculator.calculate(dates, today))
    }

    @Test
    fun `gap no meio conta apenas a sequencia mais recente`() {
        val dates = listOf("2026-07-10", "2026-07-11", "2026-07-15", "2026-07-16")
        assertEquals(2, StreakCalculator.calculate(dates, today))
    }

    @Test
    fun `registro unico de hoje conta um`() {
        assertEquals(1, StreakCalculator.calculate(listOf("2026-07-16"), today))
    }

    @Test
    fun `datas duplicadas nao inflam o streak`() {
        val dates = listOf("2026-07-16", "2026-07-16", "2026-07-15")
        assertEquals(2, StreakCalculator.calculate(dates, today))
    }

    @Test
    fun `datas invalidas sao ignoradas`() {
        val dates = listOf("data-invalida", "2026-07-16")
        assertEquals(1, StreakCalculator.calculate(dates, today))
    }
}
