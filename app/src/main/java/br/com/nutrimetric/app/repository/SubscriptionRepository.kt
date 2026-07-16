package br.com.nutrimetric.app.repository

import android.app.Activity
import android.content.Context
import br.com.nutrimetric.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Integração com o SDK do RevenueCat.
 *
 * IMPORTANTE: o `app_user_id` do RevenueCat DEVE ser o uid do Firebase Auth
 * (a Cloud Function `revenuecatWebhook` já assume isso — ver functions/src/index.ts).
 * O entitlement checado é "premium", configurado no painel do RevenueCat.
 *
 * Nota: as assinaturas exatas de callback abaixo devem ser conferidas contra
 * a versão do SDK instalada no primeiro build real (ver docs/SETUP-REVENUECAT.md) —
 * este ambiente não tem acesso ao Maven Central para validar em tempo de escrita.
 */
class SubscriptionRepository(private val context: Context) {

    companion object {
        private const val ENTITLEMENT_PREMIUM = "premium"

        @Volatile
        private var configured = false
    }

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

    /** Status premium reativo, atualizado sempre que o RevenueCat notificar mudança de entitlement. */
    val isPremium: Flow<Boolean> = callbackFlow {
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

    suspend fun purchasePackage(activity: Activity, packageToPurchase: Package): Result<Unit> {
        ensureConfigured()
        return try {
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.purchase(
                    PurchaseParams.Builder(activity, packageToPurchase).build(),
                    object : PurchaseCallback {
                        override fun onCompleted(
                            storeTransaction: com.revenuecat.purchases.models.StoreTransaction,
                            customerInfo: CustomerInfo
                        ) {
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
