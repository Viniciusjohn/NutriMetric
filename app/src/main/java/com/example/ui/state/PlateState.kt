package com.example.ui.state

import androidx.compose.runtime.Immutable

@Immutable
data class PlateItemUiState(
    val id: String,
    val name: String,
    val grams: Int,
    val formattedGrams: String,
    val formattedProtein: String,
    val formattedCarbs: String,
    val formattedFat: String,
    val formattedKcal: String,
    val milliKcalPer100g: Long,
    val milliProteinPer100g: Long,
    val milliCarbsPer100g: Long,
    val milliFatPer100g: Long
)

@Immutable
data class PlateScreenState(
    val items: List<PlateItemUiState> = emptyList(),
    val totalProteinText: String = "0g",
    val totalCarbsText: String = "0g",
    val totalFatText: String = "0g",
    val totalCaloriesText: String = "0 kcal",
    val imageUri: String = "",
    val imageBase64: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

sealed class PlateEvent {
    data class UpdateWeight(val itemId: String, val newGrams: Int) : PlateEvent()
    data class EditMacros(
        val itemId: String,
        val newGrams: Int,
        val newKcal: Long,
        val newProtein: Long,
        val newCarbs: Long,
        val newFat: Long
    ) : PlateEvent()
    data class RemoveItem(val itemId: String) : PlateEvent()
    data class UndoRemove(val item: PlateItemUiState, val index: Int) : PlateEvent()
    object ConfirmPlate : PlateEvent()
    object ClearError : PlateEvent()
}
