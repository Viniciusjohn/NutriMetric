package br.com.nutrimetric.app.notifications

import br.com.nutrimetric.app.repository.PushRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe pushes de re-engajamento (enviados pela Cloud Function
 * `sendReengagementPush`) e mantém o token FCM atualizado no backend.
 */
class NutriFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { PushRepository().registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: return
        ReminderWorker.showPushNotification(applicationContext, title, body)
    }
}
