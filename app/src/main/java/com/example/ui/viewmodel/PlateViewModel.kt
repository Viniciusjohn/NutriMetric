package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.remote.Alimento
import com.example.repository.MealRepository
import com.example.ui.state.PlateEvent
import com.example.ui.state.PlateItemUiState
import com.example.ui.state.PlateScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlateViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.example.data.local.DatabaseProvider.getAppDatabase(application)
    private val tacoDb = com.example.data.local.DatabaseProvider.getTacoDatabase(application)
    private val mealRepository = MealRepository(application, db.mealDao(), tacoDb.dailyConsumptionDao())

    private val _state = MutableStateFlow(PlateScreenState())
    val state: StateFlow<PlateScreenState> = _state.asStateFlow()

    fun initialize(imageBase64: String, imageUri: String, mappedItems: List<MappedFoodItem>) {
        val itemsList = mappedItems.mapIndexed { index, item ->
            val name = item.tacoFood?.description ?: item.genericName
            val grams = if (item.tacoFood != null) {
                100 * item.halfPortions / 2 // Defaulting base measure to 100g
            } else {
                item.manualGrams ?: 100
            }
            
            val milliKcalPer100g = item.tacoFood?.energyKcal?.let { (it * 1000).toLong() }
                ?: ((item.manualKcal ?: 0L) * 100000L / (item.manualGrams ?: 100).coerceAtLeast(1))
            val milliProteinPer100g = item.tacoFood?.protein?.let { (it * 1000).toLong() }
                ?: ((item.manualProtein ?: 0L) * 100000L / (item.manualGrams ?: 100).coerceAtLeast(1))
            val milliCarbsPer100g = item.tacoFood?.carbohydrate?.let { (it * 1000).toLong() }
                ?: ((item.manualCarbs ?: 0L) * 100000L / (item.manualGrams ?: 100).coerceAtLeast(1))
            val milliFatPer100g = item.tacoFood?.lipid?.let { (it * 1000).toLong() }
                ?: ((item.manualFat ?: 0L) * 100000L / (item.manualGrams ?: 100).coerceAtLeast(1))

            val milliKcal = milliKcalPer100g * grams / 100
            val milliProtein = milliProteinPer100g * grams / 100
            val milliCarbs = milliCarbsPer100g * grams / 100
            val milliFat = milliFatPer100g * grams / 100

            PlateItemUiState(
                id = "item_${index}_${System.currentTimeMillis()}",
                name = name,
                grams = grams,
                formattedGrams = "${grams}g",
                formattedProtein = "${milliProtein / 1000}g",
                formattedCarbs = "${milliCarbs / 1000}g",
                formattedFat = "${milliFat / 1000}g",
                formattedKcal = "${milliKcal / 1000} kcal",
                milliKcalPer100g = milliKcalPer100g,
                milliProteinPer100g = milliProteinPer100g,
                milliCarbsPer100g = milliCarbsPer100g,
                milliFatPer100g = milliFatPer100g
            )
        }
        
        _state.value = PlateScreenState(
            items = itemsList,
            imageUri = imageUri,
            imageBase64 = imageBase64,
            isLoading = false,
            isSaved = false,
            error = null
        ).recalculateTotals()
    }

    fun onEvent(event: PlateEvent) {
        when (event) {
            is PlateEvent.UpdateWeight -> {
                _state.update { current ->
                    val updatedItems = current.items.map { item ->
                        if (item.id == event.itemId) {
                            val newGrams = event.newGrams.coerceAtLeast(1)
                            val milliKcal = item.milliKcalPer100g * newGrams / 100
                            val milliProtein = item.milliProteinPer100g * newGrams / 100
                            val milliCarbs = item.milliCarbsPer100g * newGrams / 100
                            val milliFat = item.milliFatPer100g * newGrams / 100

                            item.copy(
                                grams = newGrams,
                                formattedGrams = "${newGrams}g",
                                formattedProtein = "${milliProtein / 1000}g",
                                formattedCarbs = "${milliCarbs / 1000}g",
                                formattedFat = "${milliFat / 1000}g",
                                formattedKcal = "${milliKcal / 1000} kcal"
                            )
                        } else {
                            item
                        }
                    }
                    current.copy(items = updatedItems).recalculateTotals()
                }
            }
            is PlateEvent.EditMacros -> {
                _state.update { current ->
                    val updatedItems = current.items.map { item ->
                        if (item.id == event.itemId) {
                            val newGrams = event.newGrams.coerceAtLeast(1)
                            // We calculate per 100g based on the new absolute values
                            val newMilliKcalPer100g = event.newKcal * 1000 * 100 / newGrams
                            val newMilliProteinPer100g = event.newProtein * 1000 * 100 / newGrams
                            val newMilliCarbsPer100g = event.newCarbs * 1000 * 100 / newGrams
                            val newMilliFatPer100g = event.newFat * 1000 * 100 / newGrams

                            item.copy(
                                grams = newGrams,
                                formattedGrams = "${newGrams}g",
                                formattedProtein = "${event.newProtein}g",
                                formattedCarbs = "${event.newCarbs}g",
                                formattedFat = "${event.newFat}g",
                                formattedKcal = "${event.newKcal} kcal",
                                milliKcalPer100g = newMilliKcalPer100g,
                                milliProteinPer100g = newMilliProteinPer100g,
                                milliCarbsPer100g = newMilliCarbsPer100g,
                                milliFatPer100g = newMilliFatPer100g
                            )
                        } else {
                            item
                        }
                    }
                    current.copy(items = updatedItems).recalculateTotals()
                }
            }
            is PlateEvent.RemoveItem -> {
                _state.update { current ->
                    val updatedItems = current.items.filter { it.id != event.itemId }
                    current.copy(items = updatedItems).recalculateTotals()
                }
            }
            is PlateEvent.UndoRemove -> {
                _state.update { current ->
                    val updatedItems = current.items.toMutableList()
                    val indexToRestore = event.index
                    val itemToRestore = event.item
                    if (indexToRestore in 0..updatedItems.size) {
                        updatedItems.add(indexToRestore, itemToRestore)
                    } else {
                        updatedItems.add(itemToRestore)
                    }
                    current.copy(items = updatedItems).recalculateTotals()
                }
            }
            is PlateEvent.ConfirmPlate -> {
                savePlateToDb()
            }
            is PlateEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun savePlateToDb() {
        val currentState = _state.value
        if (currentState.items.isEmpty()) {
            _state.update { it.copy(error = "O prato não pode estar vazio") }
            return
        }

        _state.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                var totalMilliKcal = 0L
                var totalMilliProtein = 0L
                var totalMilliCarbs = 0L
                var totalMilliFat = 0L

                val alimentos = currentState.items.map { item ->
                    val itemKcal = item.milliKcalPer100g * item.grams / 100
                    val itemProtein = item.milliProteinPer100g * item.grams / 100
                    val itemCarbs = item.milliCarbsPer100g * item.grams / 100
                    val itemFat = item.milliFatPer100g * item.grams / 100

                    totalMilliKcal += itemKcal
                    totalMilliProtein += itemProtein
                    totalMilliCarbs += itemCarbs
                    totalMilliFat += itemFat

                    Alimento(
                        nome = item.name,
                        gramas = item.grams,
                        kcal = (itemKcal / 1000).toInt(),
                        proteina = (itemProtein / 1000).toInt(),
                        carbo = (itemCarbs / 1000).toInt(),
                        gordura = (itemFat / 1000).toInt()
                    )
                }

                mealRepository.saveMeal(
                    imagePath = currentState.imageUri,
                    imageBase64 = currentState.imageBase64,
                    alimentos = alimentos,
                    totalMilliKcal = totalMilliKcal,
                    totalMilliProtein = totalMilliProtein,
                    totalMilliCarbs = totalMilliCarbs,
                    totalMilliFat = totalMilliFat
                )

                _state.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Erro ao salvar refeição: ${e.message}") }
            }
        }
    }

    private fun PlateScreenState.recalculateTotals(): PlateScreenState {
        var totalKcal = 0L
        var totalProtein = 0L
        var totalCarbs = 0L
        var totalFat = 0L

        items.forEach { item ->
            totalKcal += item.milliKcalPer100g * item.grams / 100
            totalProtein += item.milliProteinPer100g * item.grams / 100
            totalCarbs += item.milliCarbsPer100g * item.grams / 100
            totalFat += item.milliFatPer100g * item.grams / 100
        }

        return this.copy(
            totalProteinText = "${totalProtein / 1000}g",
            totalCarbsText = "${totalCarbs / 1000}g",
            totalFatText = "${totalFat / 1000}g",
            totalCaloriesText = "${totalKcal / 1000} kcal"
        )
    }
}
