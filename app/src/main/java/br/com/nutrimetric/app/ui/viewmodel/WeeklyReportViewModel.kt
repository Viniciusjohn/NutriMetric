package br.com.nutrimetric.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nutrimetric.app.data.local.DatabaseProvider
import br.com.nutrimetric.app.data.local.DateDailyTotals
import br.com.nutrimetric.app.repository.ChatPremiumRequiredException
import br.com.nutrimetric.app.repository.ChatRepository
import br.com.nutrimetric.app.repository.GoalsRepository
import br.com.nutrimetric.app.repository.NutritionGoals
import br.com.nutrimetric.app.repository.SubscriptionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeeklyReportViewModel(application: Application) : AndroidViewModel(application) {

    private val tacoDb = DatabaseProvider.getTacoDatabase(application)
    private val dailyConsumptionDao = tacoDb.dailyConsumptionDao()
    private val goalsRepository = GoalsRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)
    private val chatRepository = ChatRepository(DatabaseProvider.getAppDatabase(application).chatMessageDao())

    /** Últimos 7 dias com registro, do mais antigo para o mais recente (ordem do gráfico). */
    val weeklyTotals: StateFlow<List<DateDailyTotals>> = dailyConsumptionDao.getAllDailyTotalsGroupedByDate()
        .map { all ->
            val since = LocalDate.now().minusDays(6).toString()
            all.filter { it.date >= since }.sortedBy { it.date }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val goals: StateFlow<NutritionGoals> = goalsRepository.goalsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoalsRepository.DEFAULT_GOALS
        )

    val isPremium: StateFlow<Boolean> = subscriptionRepository.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    private val _isLoadingSummary = MutableStateFlow(false)
    val isLoadingSummary: StateFlow<Boolean> = _isLoadingSummary.asStateFlow()

    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError.asStateFlow()

    private val _premiumRequired = MutableStateFlow(false)
    val premiumRequired: StateFlow<Boolean> = _premiumRequired.asStateFlow()

    fun clearPremiumRequired() {
        _premiumRequired.value = false
    }

    fun requestAiSummary() {
        if (_isLoadingSummary.value) return
        viewModelScope.launch {
            _isLoadingSummary.value = true
            _summaryError.value = null

            val totals = weeklyTotals.first()
            val currentGoals = goals.first()
            val context = mapOf(
                "goals" to mapOf(
                    "calories" to currentGoals.calories,
                    "protein" to currentGoals.protein,
                    "carbs" to currentGoals.carbs,
                    "fat" to currentGoals.fat
                ),
                "weeklyTotals" to totals.map { day ->
                    mapOf(
                        "date" to day.date,
                        "kcal" to day.kcal,
                        "protein" to day.protein,
                        "carbs" to day.carbohydrate,
                        "fat" to day.lipid
                    )
                }
            )

            val result = chatRepository.requestWeeklySummary(context)
            result.fold(
                onSuccess = { reply -> _aiSummary.value = reply },
                onFailure = { e ->
                    if (e is ChatPremiumRequiredException) {
                        _premiumRequired.value = true
                    } else {
                        _summaryError.value = e.message ?: "Não foi possível gerar a análise agora."
                    }
                }
            )
            _isLoadingSummary.value = false
        }
    }
}
