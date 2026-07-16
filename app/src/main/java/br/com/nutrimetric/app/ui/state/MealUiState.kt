package br.com.nutrimetric.app.ui.state

import androidx.compose.runtime.Immutable

@Immutable
data class AlimentoUiState(
    val nome: String,
    val formattedGramas: String,
    val formattedKcal: String,
    val formattedProtein: String,
    val formattedCarbo: String,
    val formattedGordura: String
)

/** Versão resumida de uma refeição favoritada, exibida na tira de "repetir refeição" da Home. */
@Immutable
data class FavoriteMealUiState(
    val id: Long,
    val label: String,
    val formattedKcal: String
)

@Immutable
data class MealUiState(
    val id: Long,
    val mealType: String,
    val formattedKcal: String,
    val formattedProtein: String,
    val formattedCarbo: String,
    val formattedGordura: String,
    val formattedTime: String,
    val imagePath: String,
    val imageBase64: String,
    val foods: List<AlimentoUiState>,
    val isFavorite: Boolean = false
)
