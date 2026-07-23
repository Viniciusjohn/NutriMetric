package br.com.nutrimetric.app.data.remote

import kotlinx.serialization.Serializable

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
