package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.MealEntity
import com.example.repository.DailyTotals
import com.example.repository.MealRepository
import com.example.ui.state.MealUiState
import com.example.ui.state.AlimentoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

import com.example.data.local.TacoEntity
import com.example.utils.TacoMatcher
import com.example.data.local.TacoDatabase
import com.example.data.remote.Alimento

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.remote.AiModelConfig
import com.example.data.remote.RetrofitClient
import com.example.repository.NvidiaRepository

data class MappedFoodItem(
    val genericName: String,
    val tacoFood: TacoEntity?,
    val confidence: String,
    val halfPortions: Int = 2,
    val manualKcal: Long? = null,
    val manualProtein: Long? = null,
    val manualCarbs: Long? = null,
    val manualFat: Long? = null,
    val manualGrams: Int? = null
) {
    val milliKcal: Long get() = if (tacoFood != null) {
        ((tacoFood.energyKcal ?: 0.0) * 1000).toLong() * 100 * halfPortions / 200 // 100g base * 100g per measure
    } else {
        (manualKcal ?: 0L) * 1000L
    }
    
    val milliProtein: Long get() = if (tacoFood != null) {
        ((tacoFood.protein ?: 0.0) * 1000).toLong() * 100 * halfPortions / 200
    } else {
        (manualProtein ?: 0L) * 1000L
    }
    
    val milliCarbs: Long get() = if (tacoFood != null) {
        ((tacoFood.carbohydrate ?: 0.0) * 1000).toLong() * 100 * halfPortions / 200
    } else {
        (manualCarbs ?: 0L) * 1000L
    }
    
    val milliFat: Long get() = if (tacoFood != null) {
        ((tacoFood.lipid ?: 0.0) * 1000).toLong() * 100 * halfPortions / 200
    } else {
        (manualFat ?: 0L) * 1000L
    }
    
    val grams: Int get() = if (tacoFood != null) {
        100 * halfPortions / 2 // assuming base 100g per portion
    } else {
        manualGrams ?: 0
    }
}

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val imageBase64: String, val mappedItems: List<MappedFoodItem>, val uri: String) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.example.data.local.DatabaseProvider.getAppDatabase(application)
    private val tacoDb = com.example.data.local.DatabaseProvider.getTacoDatabase(application)
    private val dailyConsumptionDao = tacoDb.dailyConsumptionDao()
    private val mealRepository = MealRepository(application, db.mealDao(), dailyConsumptionDao)

    private val nvidiaRepository = NvidiaRepository(RetrofitClient.service)
    var selectedModel by mutableStateOf(AiModelConfig.GEMINI_25_FLASH)

    private val tfliteClassifier = com.example.ml.TfliteFoodClassifier(application)
    private val goalsRepository = com.example.repository.GoalsRepository(application)

    val nutritionGoals: StateFlow<com.example.repository.NutritionGoals> = goalsRepository.goalsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.repository.GoalsRepository.DEFAULT_GOALS
        )

    val dailyCalorieGoal: StateFlow<Int> = goalsRepository.goalsFlow
        .map { it.calories }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.repository.GoalsRepository.DEFAULT_GOALS.calories
        )

    fun updateDailyCalorieGoal(goal: Int) {
        viewModelScope.launch {
            val current = nutritionGoals.value
            goalsRepository.saveGoals(current.copy(calories = goal))
        }
    }

    fun saveGoals(calories: Int, protein: Int, carbs: Int, fat: Int) {
        viewModelScope.launch {
            goalsRepository.saveGoals(
                com.example.repository.NutritionGoals(calories, protein, carbs, fat)
            )
        }
    }

    // Selected date state (defaults to today)
    private val _selectedDate = MutableStateFlow<String>(java.time.LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun selectDate(dateString: String) {
        _selectedDate.value = dateString
    }

    fun selectPreviousDay() {
        try {
            val date = java.time.LocalDate.parse(_selectedDate.value)
            _selectedDate.value = date.minusDays(1).toString()
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error decrementing date", e)
        }
    }

    fun selectNextDay() {
        try {
            val date = java.time.LocalDate.parse(_selectedDate.value)
            _selectedDate.value = date.plusDays(1).toString()
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error incrementing date", e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayMeals: StateFlow<List<MealEntity>> = _selectedDate
        .flatMapLatest { date ->
            mealRepository.getMealsFlowByDate(date)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayMealsUiState: StateFlow<List<MealUiState>> = _selectedDate
        .flatMapLatest { date ->
            mealRepository.getMealsFlowByDate(date)
        }
        .map { meals ->
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            meals.map { meal ->
                val foodsList = mealRepository.parseFoodsFromJson(meal.foodsJson)
                val foodsUi = foodsList.map { food ->
                    AlimentoUiState(
                        nome = food.nome,
                        formattedGramas = "${food.gramas}g",
                        formattedKcal = "${food.kcal} kcal",
                        formattedProtein = "${food.proteina}g",
                        formattedCarbo = "${food.carbo}g",
                        formattedGordura = "${food.gordura}g"
                    )
                }
                MealUiState(
                    id = meal.id,
                    mealType = meal.mealType,
                    formattedKcal = "${meal.totalMilliKcal / 1000} kcal",
                    formattedProtein = "${meal.totalMilliProtein / 1000}g",
                    formattedCarbo = "${meal.totalMilliCarbs / 1000}g",
                    formattedGordura = "${meal.totalMilliFat / 1000}g",
                    formattedTime = formatter.format(java.util.Date(meal.timestamp)),
                    imagePath = meal.imagePath,
                    imageBase64 = meal.imageBase64,
                    foods = foodsUi
                )
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayTotals: StateFlow<com.example.repository.DailyTotals?> = _selectedDate
        .flatMapLatest { date ->
            dailyConsumptionDao.getDailyTotals(date)
        }
        .map { dbTotals ->
            com.example.repository.DailyTotals(
                milliKcal = (dbTotals.kcal * 1000).toLong(),
                milliProtein = (dbTotals.protein * 1000).toLong(),
                milliCarbs = (dbTotals.carbohydrate * 1000).toLong(),
                milliFat = (dbTotals.lipid * 1000).toLong(),
                mealCount = 0
            )
        }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.repository.DailyTotals(0L, 0L, 0L, 0L, 0)
        )

    val historyTotals: StateFlow<List<com.example.data.local.DateDailyTotals>> = dailyConsumptionDao.getAllDailyTotalsGroupedByDate()
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState = _analysisState.asStateFlow()

    fun scanBarcode(barcode: String) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            try {
                val response = RetrofitClient.openFoodFactsApi.getProductByBarcode(barcode)
                val product = response.product
                if (response.status == 1 && product != null) {
                    val name = product.productName ?: "Produto Desconhecido"
                    val nutriments = product.nutriments
                    val mappedItem = MappedFoodItem(
                        genericName = name,
                        tacoFood = null,
                        confidence = "Alta (Código de Barras)",
                        halfPortions = 2,
                        manualKcal = (nutriments?.energyKcal100g ?: 0.0).toLong(),
                        manualProtein = (nutriments?.proteins100g ?: 0.0).toLong(),
                        manualCarbs = (nutriments?.carbohydrates100g ?: 0.0).toLong(),
                        manualFat = (nutriments?.fat100g ?: 0.0).toLong(),
                        manualGrams = 100
                    )
                    _analysisState.value = AnalysisState.Success(
                        imageBase64 = "",
                        mappedItems = listOf(mappedItem),
                        uri = ""
                    )
                } else {
                    _analysisState.value = AnalysisState.Error("Produto não encontrado na base de dados.")
                }
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error("Erro ao consultar código de barras: ${e.message}")
            }
        }
    }

    private val _currentImageUri = MutableStateFlow<Uri?>(null)
    val currentImageUri = _currentImageUri.asStateFlow()

    fun loadTodayData() {
        // Obsoleto: as atualizações agora são reativas e automáticas via Room Flows!
    }

    fun parseFoods(foodsJson: String): List<Alimento> {
        return mealRepository.parseFoodsFromJson(foodsJson)
    }

    fun setCurrentImageUri(uri: Uri?) {
        _currentImageUri.value = uri
    }

    fun analyzeImage(bitmap: Bitmap, uri: String) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            
            // Move bitmap conversion and network call to IO thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var base64Fallback = ""
                try {
                    val outputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    
                    val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                    base64Fallback = base64

                    // 1. TFLite Local Classifier check
                    val localClassifierResult = tfliteClassifier.classify(bitmap)
                    val tacoDao = com.example.data.local.DatabaseProvider.getTacoDatabase(getApplication()).tacoDao()
                    val allTacoFoods = tacoDao.getAllFoods()

                    val mappedItems: List<MappedFoodItem> = if (localClassifierResult.confidence >= 0.7f) {
                        android.util.Log.d("MainViewModel", "Classificador local TFLite ganhou com confiança >= 70%: ${localClassifierResult.label} (${localClassifierResult.confidence})")
                        val tacoFood = TacoMatcher.findBestMatch(localClassifierResult.label, allTacoFoods)
                        listOf(
                            MappedFoodItem(
                                genericName = localClassifierResult.label,
                                tacoFood = tacoFood,
                                confidence = "Local TFLite (${String.format(java.util.Locale.US, "%.0f", localClassifierResult.confidence * 100)}%)"
                            )
                        )
                    } else {
                        android.util.Log.d("MainViewModel", "TFLite local com baixa confiança (${localClassifierResult.confidence}). Usando API Nvidia de fallback.")
                        val result = nvidiaRepository.analisarFoto(imageBytes, selectedModel)
                        
                        result.getOrThrow().items.map { item ->
                            val tacoFood = TacoMatcher.findBestMatch(item.name, allTacoFoods)
                            val parsed = TacoMatcher.parsePreparo(item.preparo)
                            MappedFoodItem(
                                genericName = item.name,
                                tacoFood = tacoFood,
                                confidence = item.preparo,
                                halfPortions = if (tacoFood != null) 2 else (parsed?.grams?.let { it * 2 / 100 } ?: 2),
                                manualKcal = if (tacoFood == null) parsed?.kcal else null,
                                manualProtein = if (tacoFood == null) parsed?.protein else null,
                                manualCarbs = if (tacoFood == null) parsed?.carbs else null,
                                manualFat = if (tacoFood == null) parsed?.fat else null,
                                manualGrams = if (tacoFood == null) parsed?.grams else null
                            )
                        }
                    }

                    // Handle telemetries (local save) for mismatches outside the mapping loop
                    val failedItems = mappedItems.filter { it.tacoFood == null }
                    if (failedItems.isNotEmpty()) {
                        failedItems.forEach { item ->
                            try {
                                val mismatch = com.example.data.local.TacoMismatchEntity(
                                    inputTerm = item.genericName,
                                    detectedConfidence = item.confidence,
                                    timestamp = System.currentTimeMillis()
                                )
                                // Local Room persist
                                db.tacoMismatchDao().insertMismatch(mismatch)
                            } catch (t: Throwable) {
                                android.util.Log.e("MainViewModel", "Telemetry silence failure", t)
                            }
                        }
                    }
                    
                    // Update UI state on Main thread
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _analysisState.value = AnalysisState.Success(base64, mappedItems, uri)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Erro geral no pipeline de análise", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _analysisState.value = AnalysisState.Success(base64Fallback, emptyList(), uri)
                    }
                }
            }
        }
    }

    fun updateMappedItemQuantity(index: Int, newHalfPortions: Int) {
        val currentState = _analysisState.value
        if (currentState is AnalysisState.Success) {
            val updatedList = currentState.mappedItems.toMutableList()
            if (newHalfPortions >= 0) {
                updatedList[index] = updatedList[index].copy(halfPortions = newHalfPortions)
                _analysisState.value = currentState.copy(mappedItems = updatedList)
            }
        }
    }

    fun updateMappedItemManualValues(
        index: Int,
        kcal: Long,
        protein: Long,
        carbs: Long,
        fat: Long,
        grams: Int
    ) {
        val currentState = _analysisState.value
        if (currentState is AnalysisState.Success) {
            val updatedList = currentState.mappedItems.toMutableList()
            updatedList[index] = updatedList[index].copy(
                manualKcal = kcal,
                manualProtein = protein,
                manualCarbs = carbs,
                manualFat = fat,
                manualGrams = grams
            )
            _analysisState.value = currentState.copy(mappedItems = updatedList)
        }
    }

    fun addManualFoodItem(
        name: String,
        kcal: Long,
        protein: Long,
        carbs: Long,
        fat: Long,
        grams: Int
    ) {
        val currentState = _analysisState.value
        if (currentState is AnalysisState.Success) {
            val updatedList = currentState.mappedItems.toMutableList()
            updatedList.add(
                MappedFoodItem(
                    genericName = name,
                    tacoFood = null,
                    confidence = "100%",
                    halfPortions = 2,
                    manualKcal = kcal,
                    manualProtein = protein,
                    manualCarbs = carbs,
                    manualFat = fat,
                    manualGrams = grams
                )
            )
            _analysisState.value = currentState.copy(mappedItems = updatedList)
        }
    }

    fun saveManualMeal(
        mealType: String,
        foodName: String,
        kcal: Long,
        protein: Long,
        carbs: Long,
        fat: Long,
        grams: Int
    ) {
        viewModelScope.launch {
            val alimento = Alimento(
                nome = foodName,
                gramas = grams,
                kcal = kcal.toInt(),
                proteina = protein.toInt(),
                carbo = carbs.toInt(),
                gordura = fat.toInt(),
                porcoes = 1
            )
            
            val totalMilliKcal = kcal * 1000L
            val totalMilliProtein = protein * 1000L
            val totalMilliCarbs = carbs * 1000L
            val totalMilliFat = fat * 1000L
            
            mealRepository.saveMeal(
                imagePath = "", // No image for manual entry
                imageBase64 = "",
                alimentos = listOf(alimento),
                totalMilliKcal = totalMilliKcal,
                totalMilliProtein = totalMilliProtein,
                totalMilliCarbs = totalMilliCarbs,
                totalMilliFat = totalMilliFat,
                date = _selectedDate.value
            )

            loadTodayData()
        }
    }

    fun saveMeal(base64: String, uri: String, mappedItems: List<MappedFoodItem>) {
        viewModelScope.launch {
            val alimentos = mappedItems.map { mappedItem ->
                val tacoFood = mappedItem.tacoFood
                if (tacoFood != null) {
                    Alimento(
                        nome = tacoFood.description,
                        gramas = mappedItem.grams,
                        kcal = (mappedItem.milliKcal / 1000).toInt(),
                        proteina = (mappedItem.milliProtein / 1000).toInt(),
                        carbo = (mappedItem.milliCarbs / 1000).toInt(),
                        gordura = (mappedItem.milliFat / 1000).toInt(),
                        porcoes = mappedItem.halfPortions / 2
                    )
                } else {
                    Alimento(
                        nome = mappedItem.genericName,
                        gramas = mappedItem.grams,
                        kcal = (mappedItem.milliKcal / 1000).toInt(),
                        proteina = (mappedItem.milliProtein / 1000).toInt(),
                        carbo = (mappedItem.milliCarbs / 1000).toInt(),
                        gordura = (mappedItem.milliFat / 1000).toInt(),
                        porcoes = 1
                    )
                }
            }

            val totalMilliKcal = mappedItems.sumOf { it.milliKcal }
            val totalMilliProtein = mappedItems.sumOf { it.milliProtein }
            val totalMilliCarbs = mappedItems.sumOf { it.milliCarbs }
            val totalMilliFat = mappedItems.sumOf { it.milliFat }

            mealRepository.saveMeal(
                imagePath = uri,
                imageBase64 = base64,
                alimentos = alimentos,
                totalMilliKcal = totalMilliKcal,
                totalMilliProtein = totalMilliProtein,
                totalMilliCarbs = totalMilliCarbs,
                totalMilliFat = totalMilliFat,
                date = _selectedDate.value
            )

            _analysisState.value = AnalysisState.Idle
            _currentImageUri.value = null
            loadTodayData()
        }
    }

    fun resetAnalysis() {
        _analysisState.value = AnalysisState.Idle
        _currentImageUri.value = null
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            mealRepository.deleteMeal(id)
            loadTodayData()
        }
    }
}
