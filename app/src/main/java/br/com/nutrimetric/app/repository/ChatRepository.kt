package br.com.nutrimetric.app.repository

import br.com.nutrimetric.app.data.local.ChatMessageDao
import br.com.nutrimetric.app.data.local.ChatMessageEntity
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Pergunta livre sem assinatura Premium — a UI deve oferecer o paywall. */
class ChatPremiumRequiredException(message: String) : Exception(message)

/**
 * Chat com a Nutri IA via Cloud Function `chat`.
 *
 * Segue o mesmo padrão de [AnalysisRepository] para chamar a function
 * callable. Sem persistência de histórico no servidor: as mensagens vivem
 * só no Room local ([ChatMessageDao]), e as últimas mensagens são enviadas
 * no payload a cada chamada para dar memória conversacional à IA.
 */
class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    // Lazy: ver comentário equivalente em AnalysisRepository.
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(REGION) }

    fun getMessagesFlow(): Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()

    suspend fun saveMessage(role: String, content: String, relatedMealId: Long? = null): Long =
        chatMessageDao.insert(ChatMessageEntity(role = role, content = content, relatedMealId = relatedMealId))

    suspend fun sendUserMessage(message: String, context: Map<String, Any?>): Result<String> =
        callChat(trigger = "user_message", message = message, context = context)

    suspend fun requestMealComment(context: Map<String, Any?>): Result<String> =
        callChat(trigger = "meal_logged", message = null, context = context)

    suspend fun requestDailySummary(context: Map<String, Any?>): Result<String> =
        callChat(trigger = "daily_summary", message = null, context = context)

    suspend fun requestWeeklySummary(context: Map<String, Any?>): Result<String> =
        callChat(trigger = "weekly_summary", message = null, context = context)

    private suspend fun callChat(
        trigger: String,
        message: String?,
        context: Map<String, Any?>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = mutableMapOf<String, Any?>(
                "trigger" to trigger,
                "context" to context
            )
            if (message != null) {
                payload["message"] = message
                // Histórico só é relevante (e enviado) para perguntas livres.
                payload["history"] = chatMessageDao.getRecentMessages(10)
                    .asReversed()
                    .filter { it.role == "user" || it.role == "assistant" }
                    .map {
                        mapOf(
                            "role" to if (it.role == "assistant") "model" else "user",
                            "content" to it.content
                        )
                    }
            }

            val result = functions.getHttpsCallable("chat").call(payload).await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: emptyMap()
            val reply = data["reply"] as? String

            if (reply.isNullOrBlank()) {
                Result.failure(Exception("A Nutri não respondeu. Tente novamente."))
            } else {
                Result.success(reply)
            }
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    Result.failure(
                        ChatPremiumRequiredException(e.message ?: "Assine o Premium para conversar com a Nutri.")
                    )
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    Result.failure(Exception("Faça login para conversar com a Nutri."))
                else -> Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val REGION = "southamerica-east1"
    }
}
