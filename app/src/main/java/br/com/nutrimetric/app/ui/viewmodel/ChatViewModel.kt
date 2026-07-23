package br.com.nutrimetric.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nutrimetric.app.data.local.ChatMessageEntity
import br.com.nutrimetric.app.data.local.DatabaseProvider
import br.com.nutrimetric.app.repository.ChatPremiumRequiredException
import br.com.nutrimetric.app.repository.ChatRepository
import br.com.nutrimetric.app.repository.GoalsRepository
import br.com.nutrimetric.app.repository.MealRepository
import br.com.nutrimetric.app.repository.ProfileRepository
import br.com.nutrimetric.app.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.getAppDatabase(application)
    private val tacoDb = DatabaseProvider.getTacoDatabase(application)
    private val dailyConsumptionDao = tacoDb.dailyConsumptionDao()
    private val mealRepository = MealRepository(application, db.mealDao(), dailyConsumptionDao)
    private val goalsRepository = GoalsRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)
    private val chatRepository = ChatRepository(db.chatMessageDao())

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepository.getMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isPremium: StateFlow<Boolean> = subscriptionRepository.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _premiumRequired = MutableStateFlow(false)
    val premiumRequired: StateFlow<Boolean> = _premiumRequired.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun clearPremiumRequired() {
        _premiumRequired.value = false
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            chatRepository.saveMessage(role = "user", content = text)
            val context = buildContext(loggedMeal = null)
            val result = chatRepository.sendUserMessage(text, context)
            result.fold(
                onSuccess = { reply -> chatRepository.saveMessage(role = "assistant", content = reply) },
                onFailure = { e ->
                    if (e is ChatPremiumRequiredException) {
                        _premiumRequired.value = true
                    } else {
                        _error.value = e.message ?: "Não foi possível conversar com a Nutri agora."
                    }
                }
            )
            _isSending.value = false
        }
    }

    /** Chamado quando o chat detecta (via resultado de navegação) que uma refeição acabou de ser salva. */
    fun onMealLogged(mealId: Long) {
        viewModelScope.launch {
            val meal = mealRepository.getMealById(mealId) ?: return@launch
            val foods = mealRepository.parseFoodsFromJson(meal.foodsJson)

            val summary = if (foods.isEmpty()) {
                "Refeição registrada (${meal.totalMilliKcal / 1000} kcal)."
            } else {
                val itemsText = foods.joinToString(", ") { "${it.nome} (${it.gramas}g, ${it.kcal}kcal)" }
                "Você registrou: $itemsText. Total: ${meal.totalMilliKcal / 1000} kcal."
            }
            chatRepository.saveMessage(role = "system", content = summary, relatedMealId = mealId)

            _isSending.value = true
            val loggedMealContext = mapOf(
                "items" to foods.map { food ->
                    mapOf(
                        "name" to food.nome,
                        "grams" to food.gramas,
                        "kcal" to food.kcal,
                        "protein" to food.proteina,
                        "carbs" to food.carbo,
                        "fat" to food.gordura
                    )
                }
            )
            val context = buildContext(loggedMeal = loggedMealContext)
            val result = chatRepository.requestMealComment(context)
            result.onSuccess { reply -> chatRepository.saveMessage(role = "assistant", content = reply) }
            _isSending.value = false
        }
    }

    private suspend fun buildContext(loggedMeal: Map<String, Any?>?): Map<String, Any?> {
        val profile = profileRepository.profileFlow.first()
        val goals = goalsRepository.goalsFlow.first()
        val today = java.time.LocalDate.now().toString()
        val dailyTotals = dailyConsumptionDao.getDailyTotals(today).first()

        val contextMap = mutableMapOf<String, Any?>(
            "profile" to mapOf(
                "heightCm" to profile.heightCm,
                "age" to profile.age,
                "sex" to profile.sex,
                "activityLevel" to profile.activityLevel,
                "goal" to profile.goal
            ),
            "goals" to mapOf(
                "calories" to goals.calories,
                "protein" to goals.protein,
                "carbs" to goals.carbs,
                "fat" to goals.fat
            ),
            "dailyTotals" to mapOf(
                "kcal" to dailyTotals.kcal,
                "protein" to dailyTotals.protein,
                "carbs" to dailyTotals.carbohydrate,
                "fat" to dailyTotals.lipid
            )
        )
        if (loggedMeal != null) {
            contextMap["loggedMeal"] = loggedMeal
        }
        return contextMap
    }
}
