package br.com.nutrimetric.app.repository

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Funil de eventos do Fase 5.1 do ROADMAP: onboarding_complete, photo_analyzed,
 * paywall_view, trial_start, purchase. Wrapper fino sobre o FirebaseAnalytics
 * para não espalhar `Bundle` por toda a UI/ViewModels.
 */
class AnalyticsRepository(private val context: Context) {

    // Lazy + nulo sem Firebase configurado: sem google-services.json não há
    // FirebaseApp, e FirebaseAnalytics.getInstance() lançaria na hora de
    // construir o repositório (que acontece pra TODO ViewModel, direto ou via
    // SubscriptionRepository), crashando o app inteiro em "modo dev". Aqui
    // vira só um no-op silencioso em vez de crash.
    private val analytics: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val instance = analytics ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                null -> {}
                else -> bundle.putString(key, value.toString())
            }
        }
        instance.logEvent(name, bundle)
    }

    companion object {
        const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EVENT_PHOTO_ANALYZED = "photo_analyzed"
        const val EVENT_PAYWALL_VIEW = "paywall_view"
        const val EVENT_TRIAL_START = "trial_start"
        // Nome reconhecido pelo GA4/Firebase para relatórios automáticos de
        // receita — por isso os parâmetros seguem o schema recomendado
        // (value + currency) em vez de nomes arbitrários.
        const val EVENT_PURCHASE = FirebaseAnalytics.Event.PURCHASE

        const val PARAM_REASON = "reason"
        const val PARAM_ITEM_COUNT = "item_count"
        const val PARAM_VALUE = FirebaseAnalytics.Param.VALUE
        const val PARAM_CURRENCY = FirebaseAnalytics.Param.CURRENCY
    }
}
