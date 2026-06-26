package com.example.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
)

@Serializable
data class ResponseFormat(
    val type: String
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: List<ContentPart>
)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

@Serializable
data class FoodResponse(
    val items: List<FoodItem>
)

@Serializable
data class FoodItem(
    val name: String,
    val preparo: String
)

@Serializable
data class Alimento(
    val nome: String,
    val gramas: Int,
    val kcal: Int,
    val proteina: Int,
    val carbo: Int,
    val gordura: Int,
    val porcoes: Int = 1
)

@Serializable
data class MacroEstimationResponse(
    val alimentos: List<Alimento>
)
