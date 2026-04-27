package com.pusula.service.core

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.Purchase
import com.pusula.service.data.remote.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val apiService: ApiService
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activityRef: WeakReference<Activity>? = null
    private val productIds = listOf("com.pusula.cirak", "com.pusula.usta", "com.pusula.patron")

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _activeSubscriptions = MutableStateFlow<Set<String>>(emptySet())
    val activeSubscriptions: StateFlow<Set<String>> = _activeSubscriptions.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases()
        .setListener(::onPurchasesUpdated)
        .build()

    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun queryProducts(onComplete: ((List<ProductDetails>) -> Unit)? = null) {
        connectIfNeeded {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(ProductType.SUBS)
                            .build()
                    }
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingResponseCode.OK) {
                    _products.value = details
                    onComplete?.invoke(details)
                } else {
                    _errorMessage.value = billingErrorToTurkish(result)
                    onComplete?.invoke(emptyList())
                }
            }
        }
    }

    fun purchase(planTier: String) {
        val activity = activityRef?.get()
        if (activity == null) {
            _errorMessage.value = "Satın alma için ekran etkin değil. Lütfen tekrar deneyin."
            return
        }
        val normalizedTier = planTier.trim().uppercase()
        if (normalizedTier.isBlank()) {
            _errorMessage.value = "Geçersiz paket seçimi."
            return
        }
        connectIfNeeded {
            val currentProducts = _products.value
            if (currentProducts.isEmpty()) {
                queryProducts { details -> launchBillingFlow(activity, details, normalizedTier) }
            } else {
                launchBillingFlow(activity, currentProducts, normalizedTier)
            }
        }
    }

    fun checkActiveSubscriptions() {
        connectIfNeeded {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingResponseCode.OK) {
                    _activeSubscriptions.value = purchases
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .flatMap { it.products }
                        .toSet()
                } else {
                    _errorMessage.value = billingErrorToTurkish(result)
                }
            }
        }
    }

    private fun launchBillingFlow(
        activity: Activity,
        details: List<ProductDetails>,
        planTier: String
    ) {
        val productId = planToProductId(planTier) ?: run {
            _errorMessage.value = "Bu paket için ürün bulunamadı."
            return
        }
        val productDetail = details.firstOrNull { it.productId == productId } ?: run {
            _errorMessage.value = "Seçilen paket şu an kullanılamıyor."
            return
        }
        val offerToken = productDetail.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
        if (offerToken.isNullOrBlank()) {
            _errorMessage.value = "Abonelik teklifi alınamadı. Lütfen daha sonra tekrar deneyin."
            return
        }

        val billingParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetail)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, billingParams)
        if (result.responseCode != BillingResponseCode.OK) {
            _errorMessage.value = billingErrorToTurkish(result)
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { handlePurchase(it) }
            }
            BillingResponseCode.USER_CANCELED -> {
                _errorMessage.value = "Satın alma işlemi iptal edildi."
            }
            else -> {
                _errorMessage.value = billingErrorToTurkish(result)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return
        val plan = productIdToPlan(productId) ?: return
        ioScope.launch {
            runCatching {
                apiService.googleVerify(
                    mapOf(
                        "purchaseToken" to purchase.purchaseToken,
                        "productId" to productId,
                        "plan" to plan
                    )
                )
            }.onFailure {
                _errorMessage.value = "Satın alma doğrulaması başarısız oldu. Lütfen tekrar deneyin."
            }
        }

        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgeParams) { ackResult ->
                if (ackResult.responseCode != BillingResponseCode.OK) {
                    _errorMessage.value = billingErrorToTurkish(ackResult)
                } else {
                    checkActiveSubscriptions()
                }
            }
        } else {
            checkActiveSubscriptions()
        }
    }

    private fun connectIfNeeded(onReady: () -> Unit) {
        if (billingClient.isReady) {
            onReady()
            return
        }
        billingClient.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    onReady()
                } else {
                    _errorMessage.value = billingErrorToTurkish(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                _errorMessage.value = "Google Play bağlantısı kesildi. Lütfen tekrar deneyin."
            }
        })
    }

    private fun planToProductId(planTier: String): String? = when (planTier.uppercase()) {
        "CIRAK" -> "com.pusula.cirak"
        "USTA" -> "com.pusula.usta"
        "PATRON" -> "com.pusula.patron"
        else -> null
    }

    private fun productIdToPlan(productId: String): String? = when (productId) {
        "com.pusula.cirak" -> "CIRAK"
        "com.pusula.usta" -> "USTA"
        "com.pusula.patron" -> "PATRON"
        else -> null
    }

    private fun billingErrorToTurkish(result: BillingResult): String {
        return when (result.responseCode) {
            BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play servisine ulaşılamıyor."
            BillingResponseCode.BILLING_UNAVAILABLE -> "Bu cihazda faturalandırma kullanılamıyor."
            BillingResponseCode.ITEM_UNAVAILABLE -> "Seçilen paket kullanılamıyor."
            BillingResponseCode.ITEM_ALREADY_OWNED -> "Bu pakete zaten sahipsiniz."
            BillingResponseCode.NETWORK_ERROR -> "Ağ hatası oluştu. İnternet bağlantınızı kontrol edin."
            BillingResponseCode.ERROR -> "Satın alma sırasında bir hata oluştu."
            BillingResponseCode.DEVELOPER_ERROR -> "Uygulama yapılandırmasında bir sorun var."
            else -> result.debugMessage.ifBlank { "Bilinmeyen bir ödeme hatası oluştu." }
        }
    }
}
