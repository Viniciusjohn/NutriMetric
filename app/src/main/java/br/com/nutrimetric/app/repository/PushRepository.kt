package br.com.nutrimetric.app.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Registra o token FCM do dispositivo no backend (`users/{uid}.fcmToken`),
 * usado pela Cloud Function `sendReengagementPush` para notificar usuários
 * inativos. Segue o mesmo padrão de chamada callable do [AnalysisRepository].
 */
class PushRepository {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(REGION)

    suspend fun registerToken(token: String) {
        try {
            functions.getHttpsCallable("registerFcmToken").call(mapOf("token" to token)).await()
        } catch (e: Exception) {
            android.util.Log.w("PushRepository", "Falha ao registrar token FCM", e)
        }
    }

    companion object {
        const val REGION = "southamerica-east1"
    }
}
