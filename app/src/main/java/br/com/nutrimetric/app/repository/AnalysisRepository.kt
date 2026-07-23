package br.com.nutrimetric.app.repository

import android.util.Base64
import android.util.Log
import br.com.nutrimetric.app.data.remote.FoodItem
import br.com.nutrimetric.app.data.remote.FoodResponse
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Limite diário de fotos atingido — a UI deve oferecer o Premium (paywall). */
class QuotaExceededException(message: String) : Exception(message)

/**
 * Análise de foto via Cloud Function `analyzePhoto`.
 *
 * Substitui a antiga chamada direta ao Gemini/NVIDIA: a chave de API agora
 * vive apenas no servidor e a quota diária (1 foto FREE / 15 PREMIUM) é
 * validada no Firestore — reinstalar o app não reseta o limite.
 */
class AnalysisRepository {

    // Lazy: sem google-services.json, FirebaseFunctions.getInstance() lança
    // IllegalStateException. Adiar pro primeiro uso real (dentro do try/catch
    // de analisarFoto) evita crashar a Home inteira ao só abrir o app em
    // "modo dev" (ver AuthRepository.isFirebaseConfigured).
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(REGION) }

    suspend fun analisarFoto(imageBytes: ByteArray): Result<FoodResponse> =
        withContext(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                val result = functions
                    .getHttpsCallable("analyzePhoto")
                    .call(mapOf("imageBase64" to base64Image))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()

                @Suppress("UNCHECKED_CAST")
                val rawItems = data["items"] as? List<Map<String, Any?>> ?: emptyList()

                val items = rawItems.mapNotNull { item ->
                    val name = item["name"] as? String
                    val preparo = item["preparo"] as? String
                    if (name != null && preparo != null) FoodItem(name, preparo) else null
                }
                Result.success(filtrarAlucinacoesLiquidos(FoodResponse(items)))
            } catch (e: FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                        Result.failure(
                            QuotaExceededException(
                                e.message ?: "Você já usou suas fotos de hoje."
                            )
                        )
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        Result.failure(Exception("Faça login para analisar fotos."))
                    else -> Result.failure(e)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Segunda camada de defesa contra alucinação de líquidos (a primeira é o
     * prompt no servidor): descarta bebidas quando nenhum recipiente foi
     * identificado na imagem.
     */
    private fun filtrarAlucinacoesLiquidos(response: FoodResponse): FoodResponse {
        val liquidKeywords = listOf("leite", "suco", "refrigerante", "bebida", "liquido", "líquido", "cha", "chá")
        val containerKeywords = listOf("copo", "garrafa", "xicara", "xícara", "caneca", "recipiente", "jarra", "vidro", "lata")

        val hasContainer = response.items.any { item ->
            val normalizedName = item.name.lowercase()
            containerKeywords.any { container -> normalizedName.contains(container) }
        }

        val filteredItems = response.items.filter { item ->
            val normalizedName = item.name.lowercase()
            val isLiquid = liquidKeywords.any { liquid -> normalizedName.contains(liquid) }

            if (isLiquid && !hasContainer) {
                Log.w(
                    "AnalysisRepository",
                    "Descartando líquido alucinado ('${item.name}') por falta de recipiente na imagem."
                )
                false
            } else {
                true
            }
        }

        return FoodResponse(items = filteredItems)
    }

    companion object {
        // Mesma região das Cloud Functions (São Paulo).
        const val REGION = "southamerica-east1"
    }
}
