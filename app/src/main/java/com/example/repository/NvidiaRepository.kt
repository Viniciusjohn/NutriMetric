package com.example.repository

import android.util.Base64
import com.example.data.remote.AiModelConfig
import com.example.data.remote.ContentPart
import com.example.data.remote.FoodResponse
import com.example.data.remote.ImageUrl
import com.example.data.remote.NvidiaApiService
import com.example.data.remote.OpenAiChatRequest
import com.example.data.remote.OpenAiMessage
import com.example.data.remote.ResponseFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class NvidiaRepository(private val apiService: NvidiaApiService) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun analisarFoto(imageBytes: ByteArray, config: AiModelConfig): Result<FoodResponse> = withContext(Dispatchers.IO) {
        try {
            val isGemini = config == AiModelConfig.GEMINI_25_FLASH || config.baseUrl.contains("generativelanguage")
            val apiKey = if (isGemini) {
                com.example.BuildConfig.GEMINI_API_KEY
            } else {
                if (com.example.BuildConfig.NVIDIA_API_KEY.isNotEmpty() && com.example.BuildConfig.NVIDIA_API_KEY != "MY_NVIDIA_API_KEY") {
                    com.example.BuildConfig.NVIDIA_API_KEY
                } else {
                    com.example.BuildConfig.NV_API_KEY
                }
            }
            
            if (isGemini) {
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    return@withContext Result.failure(Exception("Gemini API Key não configurada. Configure a variável GEMINI_API_KEY nos segredos."))
                }
            } else {
                if (apiKey.isBlank() || apiKey == "MY_NV_API_KEY" || apiKey == "MY_NVIDIA_API_KEY") {
                    return@withContext Result.failure(Exception("NVIDIA API Key não configurada. Configure a variável NVIDIA_API_KEY nos segredos."))
                }
            }

            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64Image"

            val prompt = if (isGemini) {
                "Você é um especialista em culinária brasileira e nutrição. Identifique todos os pratos brasileiros e ingredientes individuais presentes nesta imagem. " +
                "Por favor, liste cada alimento separadamente em Português do Brasil, sem parênteses ou termos genéricos em inglês (por exemplo, use 'Feijão carioca', 'Arroz branco', 'Farofa de mandioca', 'Carne de sol', 'Bife acebolado', 'Couve refogada'). " +
                "IMPORTANTE: Decomponha pratos compostos, marmitas, PFs (Prato Feito) ou acompanhamentos combinados em seus ingredientes individuais separados (por exemplo, em vez de 'Salada Completa', retorne itens separados para 'Alface', 'Tomate', 'Cebola'). Isso garante que o mapeador offline local encontre a correspondência exata. " +
                "REGRA CRÍTICA CONTRA ALUCINAÇÕES DE LÍQUIDOS: Proibido classificar reflexos, molhos cremosos, caldos de carne ou brilho na comida como 'leite', 'suco', 'refrigerante' ou 'bebidas'. Se identificar algo 'branco' ou brilhoso que não seja um alimento sólido evidente (como arroz, mandioca), ignore ou classifique como parte do molho/preparo do prato principal. Só liste líquidos se houver um recipiente (copo, garrafa, xícara, lata) claramente visível na imagem. " +
                "Para cada item identificado, você DEVE estimar o peso da porção típica servida, as calorias e os macronutrientes (carboidratos, proteínas e gorduras). " +
                "Retorne os dados estritamente em formato JSON com a seguinte estrutura: " +
                "{\n  \"items\": [\n    {\n      \"name\": \"Nome do Alimento\",\n      \"preparo\": \"100g, 150kcal, carb: 20g, prot: 10g, gord: 5g\"\n    }\n  ]\n}\n" +
                "Sendo que no campo 'preparo', você DEVE colocar estritamente o peso estimado em gramas (ex: 100g), o valor calórico em kcal (ex: 150kcal), o carboidrato em gramas (ex: carb: 20g), a proteína em gramas (ex: prot: 10g) e a gordura em gramas (ex: gord: 5g). Siga este formato exatamente para permitir o processamento automático dos dados."
            } else {
                "Identify Brazilian dishes and food ingredients in this image. Proibido usar traduções ou inglês. Retorne estritamente o nome em Português do Brasil, sem parênteses (exemplo: apenas 'Feijão preto' ou 'Carne moída'). REGRA CRÍTICA CONTRA ALUCINAÇÕES DE LÍQUIDOS: Proibido classificar reflexos, molhos cremosos, caldos de carne ou brilho na comida como 'leite', 'suco', 'refrigerante' ou 'bebidas'. Se identificar algo 'branco' ou brilhoso que não seja um alimento sólido evidente (como arroz, mandioca), ignore ou classifique como parte do molho/preparo do prato principal. Só liste líquidos se houver um recipiente (copo, garrafa, xícara, lata) claramente visível na imagem. IMPORTANTE: Decomponha pratos compostos, saladas mistas ou acompanhamentos combinados em seus ingredientes individuais (por exemplo, em vez de 'Salada de alface e tomate', retorne itens separados para 'Alface' e 'Tomate'). Isso garante que nosso mapeador offline de alimentos encontre correspondências precisas. Give me the output strictly in JSON format matching this structure: {\"items\": [{\"name\": \"Nome do Prato\", \"preparo\": \"100g, 150kcal, carb: 20g, prot: 10g, gord: 5g\"}]}"
            }

            val request = OpenAiChatRequest(
                model = config.modelName,
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = listOf(
                            ContentPart(type = "text", text = prompt),
                            ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl))
                        )
                    )
                ),
                responseFormat = ResponseFormat(type = "json_object")
            )

            val url = "${config.baseUrl}v1/chat/completions"
            val response = apiService.analisarPrato(url, request)

            if (response.isSuccessful) {
                val bodyString = response.body()?.string() ?: ""
                
                val openAiResponse = json.parseToJsonElement(bodyString).jsonObject
                val choices = openAiResponse["choices"]?.jsonArray
                val contentString = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                
                val cleanJson = contentString.replace("```json", "").replace("```", "").trim()
                val foodResponse = json.decodeFromString<FoodResponse>(cleanJson)
                val filteredResponse = filtrarAlucinacoesLiquidos(foodResponse)
                Result.success(filteredResponse)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun filtrarAlucinacoesLiquidos(response: FoodResponse): FoodResponse {
        val liquidKeywords = listOf("leite", "suco", "refrigerante", "bebida", "liquido", "líquido", "cha", "chá")
        val containerKeywords = listOf("copo", "garrafa", "xicara", "xícara", "caneca", "recipiente", "jarra", "vidro", "lata")

        // Verificamos se há algum recipiente explicitly mencionado
        val hasContainer = response.items.any { item ->
            val normalizedName = item.name.lowercase()
            containerKeywords.any { container -> normalizedName.contains(container) }
        }

        val filteredItems = response.items.filter { item ->
            val normalizedName = item.name.lowercase()
            val isLiquid = liquidKeywords.any { liquid -> normalizedName.contains(liquid) }
            
            if (isLiquid) {
                // Se for leite ou líquidos e não houver um recipiente correspondente na lista, descartamos automaticamente
                if (hasContainer) {
                    true
                } else {
                    android.util.Log.w("NvidiaRepository", "Descartando ingrediente líquido alucinado ('${item.name}') por falta de recipiente correspondente na imagem.")
                    false
                }
            } else {
                true
            }
        }

        return FoodResponse(items = filteredItems)
    }
}
