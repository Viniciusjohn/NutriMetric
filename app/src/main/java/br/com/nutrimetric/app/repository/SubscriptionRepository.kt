package br.com.nutrimetric.app.repository

import android.app.Activity
import android.content.Context
import br.com.nutrimetric.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Integração com o SDK do RevenueCat.
 *
 * IMPORTANTE: o `app_user_id` do RevenueCat DEVE ser o uid do Firebase Auth
 * (a Cloud Function `revenuecatWebhook` já assume isso — ver functions/src/index.ts).
 * O entitlement checado é "premium", configurado no painel do RevenueCat.
 */
class SubscriptionRepository(private val context: Context) {

    private val analyticsRepository = AnalyticsRepository(context)

    companion object {
        private const val ENTITLEMENT_PREMIUM = "premium"
        // Mesma região das Cloud Functions (São Paulo).
        private const val REGION = "southamerica-east1"
        // Contas autorizadas a ativar o "Premium de teste" pelo botão em
        // Configurações. A Cloud Function re-checa isso no servidor — esta lista
        // no cliente só controla a VISIBILIDADE do botão. Em minúsculas.
        val TEST_PREMIUM_EMAILS = setOf("vinijohn00@gmail.com")

        @Volatile
        private var configured = false
    }

    private fun isFirebaseAvailable(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** True se o e-mail logado pode ver o botão de Premium de teste. */
    fun isTestPremiumAllowed(email: String?): Boolean =
        email != null && email.lowercase() in TEST_PREMIUM_EMAILS

    /** Garante que o SDK está configurado e logado com o uid do Firebase atual. Idempotente. */
    fun ensureConfigured() {
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    val apiKey = BuildConfig.REVENUECAT_API_KEY
                    if (apiKey.isBlank()) {
                        android.util.Log.w(
                            "SubscriptionRepository",
                            "REVENUECAT_API_KEY ausente em local.properties — assinaturas desativadas. Veja docs/SETUP-REVENUECAT.md"
                        )
                        return
                    }
                    Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
                    configured = true
                }
            }
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && Purchases.sharedInstance.appUserID != uid) {
            Purchases.sharedInstance.logIn(uid, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {}
                override fun onError(error: PurchasesError) {
                    android.util.Log.e("SubscriptionRepository", "Falha ao logar no RevenueCat: ${error.message}")
                }
            })
        }
    }

    /** Status premium do RevenueCat (entitlement "premium"). */
    private val revenueCatPremiumFlow: Flow<Boolean> = callbackFlow {
        ensureConfigured()
        if (!Purchases.isConfigured) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : UpdatedCustomerInfoListener {
            override fun onReceived(customerInfo: CustomerInfo) {
                trySend(customerInfo.entitlements[ENTITLEMENT_PREMIUM]?.isActive == true)
            }
        }
        Purchases.sharedInstance.updatedCustomerInfoListener = listener
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                trySend(customerInfo.entitlements[ENTITLEMENT_PREMIUM]?.isActive == true)
            }
            override fun onError(error: PurchasesError) {
                trySend(false)
            }
        })
        awaitClose { }
    }

    /**
     * Status premium vindo do Firestore (`users/{uid}.isPremium`), que é escrito
     * pelo webhook do RevenueCat (compra real) OU pela function de Premium de
     * teste. Fonte da verdade do lado servidor; permite testar o Premium sem
     * uma compra real. No-op (false) sem Firebase configurado ou sem usuário.
     */
    private val firestorePremiumFlow: Flow<Boolean> = callbackFlow {
        if (!isFirebaseAvailable()) {
            trySend(false); awaitClose { }; return@callbackFlow
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            trySend(false); awaitClose { }; return@callbackFlow
        }
        val registration = FirebaseFirestore.getInstance()
            .document("users/$uid")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("isPremium") == true)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Premium efetivo = RevenueCat (compra na hora) OU Firestore (webhook /
     * Premium de teste). Qualquer uma das fontes ativa desbloqueia a UI premium.
     */
    val isPremium: Flow<Boolean> =
        combine(revenueCatPremiumFlow, firestorePremiumFlow) { rc, fs -> rc || fs }

    /**
     * Liga/desliga o Premium de teste chamando a Cloud Function correspondente
     * (que valida a allowlist no servidor). O resultado reflete no isPremium via
     * o snapshot do Firestore.
     */
    suspend fun setTestPremium(enable: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fn = if (enable) "setTestPremium" else "clearTestPremium"
            FirebaseFunctions.getInstance(REGION).getHttpsCallable(fn).call().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOffering(): Offering? {
        ensureConfigured()
        if (!Purchases.isConfigured) return null
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    cont.resume(offerings.current)
                }
                override fun onError(error: PurchasesError) {
                    cont.resumeWithException(Exception(error.message))
                }
            })
        }
    }

    /** trial_start quando o RevenueCat marca o período como trial, purchase (GA4) caso contrário. */
    private fun logPurchaseAnalytics(packageToPurchase: Package, customerInfo: CustomerInfo) {
        val isTrial = customerInfo.entitlements[ENTITLEMENT_PREMIUM]?.periodType == PeriodType.TRIAL
        val price = packageToPurchase.product.price
        val params = mapOf(
            AnalyticsRepository.PARAM_VALUE to price.amountMicros / 1_000_000.0,
            AnalyticsRepository.PARAM_CURRENCY to price.currencyCode
        )
        analyticsRepository.logEvent(
            if (isTrial) AnalyticsRepository.EVENT_TRIAL_START else AnalyticsRepository.EVENT_PURCHASE,
            params
        )
    }

    suspend fun purchasePackage(activity: Activity, packageToPurchase: Package): Result<Unit> {
        ensureConfigured()
        if (!Purchases.isConfigured) {
            return Result.failure(Exception("Assinaturas ainda não estão disponíveis. Tente mais tarde."))
        }
        return try {
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.purchase(
                    PurchaseParams.Builder(activity, packageToPurchase).build(),
                    object : PurchaseCallback {
                        override fun onCompleted(
                            storeTransaction: com.revenuecat.purchases.models.StoreTransaction,
                            customerInfo: CustomerInfo
                        ) {
                            logPurchaseAnalytics(packageToPurchase, customerInfo)
                            cont.resume(Unit)
                        }
                        override fun onError(error: PurchasesError, userCancelled: Boolean) {
                            if (userCancelled) {
                                cont.resume(Unit)
                            } else {
                                cont.resumeWithException(Exception(error.message))
                            }
                        }
                    }
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restorePurchases(): Result<Unit> {
        ensureConfigured()
        if (!Purchases.isConfigured) {
            return Result.failure(Exception("Assinaturas ainda não estão disponíveis. Tente mais tarde."))
        }
        return try {
            suspendCancellableCoroutine<Unit> { cont ->
                Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        cont.resume(Unit)
                    }
                    override fun onError(error: PurchasesError) {
                        cont.resumeWithException(Exception(error.message))
                    }
                })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Desloga o usuário do RevenueCat (volta para um app_user_id anônimo).
     * Chamar sempre no signOut/deleteAccount — sem isso, a próxima conta a
     * logar no mesmo aparelho herdaria o status premium da anterior.
     */
    fun logout() {
        if (Purchases.isConfigured) {
            try {
                Purchases.sharedInstance.logOut()
            } catch (e: Exception) {
                android.util.Log.w("SubscriptionRepository", "Falha ao deslogar do RevenueCat", e)
            }
        }
    }
}
