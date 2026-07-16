package br.com.nutrimetric.app.utils

import java.time.LocalDate

/**
 * Calcula a sequência (streak) de dias consecutivos com registro de refeição.
 *
 * Função pura para facilitar testes. A contagem parte de hoje: se hoje ainda
 * não tem registro mas ontem tem, o streak continua válido (o usuário ainda
 * pode registrar hoje). Um intervalo maior que um dia zera a sequência.
 */
object StreakCalculator {

    /**
     * @param datesWithRecord datas com registro no formato "YYYY-MM-DD" (ordem irrelevante)
     * @param today data de referência (default: hoje)
     * @return número de dias consecutivos até hoje (ou ontem, com tolerância de 1 dia)
     */
    fun calculate(datesWithRecord: List<String>, today: LocalDate = LocalDate.now()): Int {
        if (datesWithRecord.isEmpty()) return 0

        val days = datesWithRecord.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        if (days.isEmpty()) return 0

        // O streak "ancora" em hoje se houver registro hoje; senão em ontem
        // (tolerância para quem ainda não registrou hoje). Caso contrário, 0.
        var anchor = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (days.contains(anchor)) {
            streak++
            anchor = anchor.minusDays(1)
        }
        return streak
    }
}
