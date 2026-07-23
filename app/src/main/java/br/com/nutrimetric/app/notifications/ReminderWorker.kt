package br.com.nutrimetric.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.nutrimetric.app.MainActivity
import br.com.nutrimetric.app.R

/**
 * Dispara a notificação diária de lembrete de registro de refeição.
 * O agendamento (horário) é feito pelo [ReminderScheduler].
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        showReminderNotification(applicationContext)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "nutrimetric_reminders"
        private const val NOTIFICATION_ID_REMINDER = 1001
        private const val NOTIFICATION_ID_PUSH = 1002

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lembretes",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Lembretes diários para registrar suas refeições"
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }

        fun showReminderNotification(context: Context) {
            showNotification(
                context = context,
                notificationId = NOTIFICATION_ID_REMINDER,
                title = "Hora de registrar sua refeição 🍽️",
                body = "Já anotou o que você comeu hoje? Mantenha sua sequência!"
            )
        }

        /** Exibe uma notificação de re-engajamento recebida via FCM (mesmo canal do lembrete local). */
        fun showPushNotification(context: Context, title: String, body: String) {
            showNotification(context = context, notificationId = NOTIFICATION_ID_PUSH, title = title, body = body)
        }

        private fun showNotification(context: Context, notificationId: Int, title: String, body: String) {
            ensureChannel(context)

            // Em Android 13+ a notificação só aparece com POST_NOTIFICATIONS concedida.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent ?: android.content.Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
