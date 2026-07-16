package br.com.nutrimetric.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nutrimetric.app.data.local.AppDatabase
import br.com.nutrimetric.app.data.local.MealEntity
import br.com.nutrimetric.app.repository.DailyTotals
import br.com.nutrimetric.app.repository.MealRepository
import br.com.nutrimetric.app.ui.state.MealUiState
import br.com.nutrimetric.app.ui.state.AlimentoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

import br.com.nutrimetric.app.data.local.TacoEntity
import br.com.nutrimetric.app.utils.TacoMatcher
import br.com.nutrimetric.app.data.local.TacoDatabase
import br.com.nutrimetric.app.data.remote.Alimento

import br.com.nutrimetric.app.data.remote.RetrofitClient
import br.com.nutrimetric.app.repository.AnalysisRepository

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

    private val db = br.com.nutrimetric.app.data.local.DatabaseProvider.getAppDatabase(application)
    private val tacoDb = br.com.nutrimetric.app.data.local.DatabaseProvider.getTacoDatabase(application)
    private val dailyConsumptionDao = tacoDb.dailyConsumptionDao()
    private val waterDao = db.waterDao()
    private val weightDao = db.weightDao()
    private val mealRepository = MealRepository(application, db.mealDao(), dailyConsumptionDao)

    private val analysisRepository = AnalysisRepository()

    private val tfliteClassifier = br.com.nutrimetric.app.ml.TfliteFoodClassifier(application)
    private val goalsRepository = br.com.nutrimetric.app.repository.GoalsRepository(application)

    val nutritionGoals: StateFlow<br.com.nutrimetric.app.repository.NutritionGoals> = goalsRepository.goalsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = br.com.nutrimetric.app.repository.GoalsRepository.DEFAULT_GOALS
        )

    val dailyCalorieGoal: StateFlow<Int> = goalsRepository.goalsFlow
        .map { it.calories }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = br.com.nutrimetric.app.repository.GoalsRepository.DEFAULT_GOALS.calories
        )

    fun updateDailyCalorieGoal(goal: Int) {
        viewModelScope.launch {
            val current = nutritionGoals.value
            goalsRepository.saveGoals(current.copy(calories = goal))
        }
    }

    fun saveGoals(calories: Int, protein: Int, carbs: Int, fat: Int) {
        viewModelScope.launch {
            // Preserva a meta de água atual (editada em outra seção de Settings)
            val current = nutritionGoals.value
            goalsRepository.saveGoals(
                current.copy(calories = calories, protein = protein, carbs = carbs, fat = fat)
            )
        }
    }

    fun saveWaterGoal(waterMl: Int) {
        viewModelScope.launch {
            goalsRepository.saveGoals(nutritionGoals.value.copy(waterMl = waterMl))
        }
    }

    val reminderSettings: StateFlow<br.com.nutrimetric.app.repository.ReminderSettings> =
        goalsRepository.reminderFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = br.com.nutrimetric.app.repository.GoalsRepository.DEFAULT_REMINDER
        )

    fun saveReminder(settings: br.com.nutrimetric.app.repository.ReminderSettings) {
        viewModelScope.launch {
            goalsRepository.saveReminder(settings)
            val app = getApplication<Application>()
            if (settings.enabled) {
                br.com.nutrimetric.app.notifications.ReminderScheduler.schedule(app, settings.hour, settings.minute)
            } else {
                br.com.nutrimetric.app.notifications.ReminderScheduler.cancel(app)
            }
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
    val todayTotals: StateFlow<br.com.nutrimetric.app.repository.DailyTotals?> = _selectedDate
        .flatMapLatest { date ->
            dailyConsumptionDao.getDailyTotals(date)
        }
        .map { dbTotals ->
            br.com.nutrimetric.app.repository.DailyTotals(
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
            initialValue = br.com.nutrimetric.app.repository.DailyTotals(0L, 0L, 0L, 0L, 0)
        )

    val historyTotals: StateFlow<List<br.com.nutrimetric.app.data.local.DateDailyTotals>> = dailyConsumptionDao.getAllDailyTotalsGroupedByDate()
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ---------- Água ----------
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val waterTotalMl: StateFlow<Int> = _selectedDate
        .flatMapLatest { date -> waterDao.getTotalForDate(date) }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            waterDao.insert(
                br.com.nutrimetric.app.data.local.WaterEntity(
                    date = _selectedDate.value,
                    amountMl = amountMl
                )
            )
        }
    }

    fun undoLastWater() {
        viewModelScope.launch {
            waterDao.deleteLastForDate(_selectedDate.value)
        }
    }

    // ---------- Peso ----------
    val latestWeight: StateFlow<br.com.nutrimetric.app.data.local.WeightEntity?> = weightDao.getLatest()
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** Os dois registros mais recentes, para exibir a variação de peso. */
    val recentWeights: StateFlow<List<br.com.nutrimetric.app.data.local.WeightEntity>> = weightDao.getRecent()
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val weightHistory: StateFlow<List<br.com.nutrimetric.app.data.local.WeightEntity>> = weightDao.getAllForChart()
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveWeight(weightKg: Double) {
        viewModelScope.launch {
            weightDao.insert(
                br.com.nutrimetric.app.data.local.WeightEntity(
                    date = java.time.LocalDate.now().toString(),
                    weightKg = weightKg
                )
            )
        }
    }

    // ---------- Streak ----------
    /** Dias consecutivos com registro de refeição, derivado do histórico. */
    val currentStreak: StateFlow<Int> = historyTotals
        .map { totals -> br.com.nutrimetric.app.utils.StreakCalculator.calculate(totals.map { it.date }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
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
                    val tacoDao = br.com.nutrimetric.app.data.local.DatabaseProvider.getTacoDatabase(getApplication()).tacoDao()
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
                        android.util.Log.d("MainViewModel", "TFLite local com baixa confiança (${localClassifierResult.confidence}). Usando análise no backend.")
                        val result = analysisRepository.analisarFoto(imageBytes)
                        
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
                                val mismatch = br.com.nutrimetric.app.data.local.TacoMismatchEntity(
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
