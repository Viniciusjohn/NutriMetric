package br.com.nutrimetric.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime

/**
 * Agenda (ou cancela) o lembrete diário via WorkManager.
 *
 * Usa um PeriodicWorkRequest de 24h com initialDelay calculado até o próximo
 * horário escolhido, garantindo que a primeira notificação caia na hora certa.
 */
object ReminderScheduler {

    private const val WORK_NAME = "nutrimetric_daily_reminder"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val initialDelay = Duration.between(now, next).toMillis()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .setInitialDelay(initialDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
