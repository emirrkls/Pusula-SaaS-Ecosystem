package com.pusula.service.core

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.pusula.service.data.remote.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

@Singleton
class BillingManager @Inject constructor(@ApplicationContext context: Context,
                                        private val api: ApiService,
                                        private val sessions: SessionManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val processor = GooglePurchaseProcessor()
    private var activity = WeakReference<Activity>(null)
    private var details: List<ProductDetails> = emptyList()
    private var connected = false
    private var connecting = false
    private val ready = mutableListOf<() -> Unit>()
    private var connectionTimeout: Job? = null
    private var restoreJob: Job? = null
    private var purchaseAccount: SessionState? = null
    private var ownedPurchases: List<Purchase> = emptyList()
    private val _uiState = MutableStateFlow(PlayBillingUiState())
    val uiState = _uiState.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener(::onPurchasesUpdated).build()

    init {
        scope.launch {
            sessions.state.map { it.supportRevision }.distinctUntilChanged().collect {
                restoreJob?.cancel()
                details = emptyList()
                ownedPurchases = emptyList()
                purchaseAccount = null
                _uiState.value = PlayBillingUiState()
                if (account() != null && !GuideReadOnlyGate.active.value) onForeground()
            }
        }
    }

    private fun account(): SessionState? = sessions.state.value.takeIf {
        it.isAuthenticated && it.isAdmin && it.companyId != null && !it.token.isNullOrBlank()
    }
    private fun same(snapshot: SessionState): Boolean = account()?.let {
        it.supportRevision == snapshot.supportRevision && it.token == snapshot.token && it.companyId == snapshot.companyId
    } == true && !GuideReadOnlyGate.active.value

    fun bindActivity(value: Activity) { activity = WeakReference(value) }
    fun onForeground() {
        if (account() == null || GuideReadOnlyGate.active.value) return
        queryProducts()
        checkActiveSubscriptions()
    }

    private suspend fun serverStatus(snapshot: SessionState): GoogleBillingStatus {
        val status=withTimeoutOrNull(35000) { api.googleBillingStatus("Bearer ${snapshot.token}") }
            ?: throw java.io.IOException("PLAY_STATUS_TIMEOUT")
        if (same(snapshot)) _uiState.update { it.copy(server=status) }
        return status
    }
    fun queryProducts() = scope.launch {
        val snapshot = account() ?: return@launch
        if (!same(snapshot) || _uiState.value.loading) return@launch
        _uiState.update { it.copy(loading = true, error = null) }
        connectIfNeeded {
            val params = QueryProductDetailsParams.newBuilder().setProductList(GooglePlayCatalog.productIds.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS).build()
            }).build()
            client.queryProductDetailsAsync(params) { result, response ->
                if (!same(snapshot)) return@queryProductDetailsAsync
                if (result.responseCode != BillingResponseCode.OK) {
                    _uiState.update { it.copy(loading = false, options = emptyList(), error = message(result)) }
                } else {
                    details = response.productDetailsList
                    val options = GooglePlayCatalog.options(details.flatMap { product ->
                        product.subscriptionOfferDetails.orEmpty().map { offer ->
                            PlayOffer(product.productId, offer.basePlanId, offer.offerId, offer.offerToken,
                                offer.pricingPhases.pricingPhaseList.map { phase ->
                                    PlayPricePhase(phase.formattedPrice, phase.priceAmountMicros, phase.billingPeriod,
                                        phase.priceCurrencyCode, phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING)
                                })
                        }
                    })
                    _uiState.update { it.copy(loading = false, options = options,
                        error = if (options.isEmpty()) "Google Play satın alma seçeneklerini döndürmedi. Daha sonra tekrar deneyin." else null) }
                }
            }
        }
    }

    fun purchase(planTier: String, period: BillingPeriod) = scope.launch {
        val snapshot = account() ?: return@launch
        if (!same(snapshot) || _uiState.value.busy || restoreJob?.isActive == true) return@launch
        val host = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: run {
            _uiState.update { it.copy(error = "Satın alma için ekran etkin değil. Lütfen tekrar deneyin.") }; return@launch
        }
        val id = planTier.lowercase(java.util.Locale.ROOT)
        val option = _uiState.value.options.singleOrNull { it.productId == id && it.period == period }
        val product = details.singleOrNull { it.productId == id }
        if (option == null || product == null) {
            _uiState.update { it.copy(error = "Bu dönem için Google Play fiyatı alınamadı. Satın alma başlatılmadı.") }
            return@launch
        }
        _uiState.update { it.copy(busy=true,error=null,message=null) }
        connectIfNeeded {
            scope.launch billing@ {
                try {
                    val status=serverStatus(snapshot)
                    val owned=queryOwned() ?: throw java.io.IOException("PLAY_QUERY_UNAVAILABLE")
                    if (!same(snapshot)) return@billing
                    ownedPurchases=owned
                    val change=GoogleReplacementPolicy.decide(snapshot.companyId!!,status,owned.map(::ownedSnapshot),option)
                    if (!change.allowed) {
                        _uiState.update { it.copy(busy=false,error=changeMessage(change.reason)) }; return@billing
                    }
                    val item=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(option.offerToken)
                    val params=BillingFlowParams.newBuilder().setObfuscatedAccountId(GooglePlayCatalog.accountBinding(snapshot.companyId!!))
                    if (change.oldToken!=null) {
                        val mode=when(change.mode) {
                            PlayChangeMode.TIME_CREDIT -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION
                            PlayChangeMode.DEFERRED -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.DEFERRED
                            else -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION
                        }
                        item.setSubscriptionProductReplacementParams(BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
                            .newBuilder().setOldProductId(change.oldProduct!!).setReplacementMode(mode).build())
                        params.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(change.oldToken).build())
                    }
                    params.setProductDetailsParamsList(listOf(item.build()))
                    purchaseAccount=snapshot
                    val result=client.launchBillingFlow(host,params.build())
                    if (result.responseCode!=BillingResponseCode.OK) {
                        purchaseAccount=null; _uiState.update { it.copy(busy=false,error=message(result)) }
                        if (result.responseCode==BillingResponseCode.ITEM_ALREADY_OWNED) checkActiveSubscriptions(true)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (same(snapshot)) _uiState.update { it.copy(busy=false,error="Satın alma hazırlığı doğrulanamadı. İşlem başlatılmadı; tekrar deneyin.") }
                }
            }
        }
    }

    private fun ownedSnapshot(p: Purchase)=OwnedPlaySubscription(p.purchaseToken,p.products,
        p.purchaseState==Purchase.PurchaseState.PENDING,p.purchaseState==Purchase.PurchaseState.PURCHASED,
        p.accountIdentifiers?.obfuscatedAccountId)

    private suspend fun queryOwned(): List<Purchase>? = withTimeoutOrNull(15000) {
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result,purchases ->
                if (continuation.isActive) continuation.resume(if(result.responseCode==BillingResponseCode.OK) purchases else null)
            }
        }
    }

    fun checkActiveSubscriptions(manual: Boolean = false) = scope.launch {
        val snapshot = account() ?: return@launch
        if (!same(snapshot) || _uiState.value.busy || restoreJob?.isActive == true) return@launch
        _uiState.update { it.copy(ownershipReady=false) }
        if (manual) _uiState.update { it.copy(busy = true, error = null, message = "Satın alımlar kontrol ediliyor…") }
        connectIfNeeded {
            if (!same(snapshot) || restoreJob?.isActive == true || purchaseAccount != null) return@connectIfNeeded
            restoreJob = scope.launch restore@ {
              try {
                val purchases=queryOwned()
                if (!same(snapshot)) return@restore
                if (purchases==null) {
                    _uiState.update { it.copy(busy=false,ownershipReady=false,error="Google Play satın alımları kontrol edilemedi. Tekrar deneyin.",message=null) }
                    return@restore
                }
                ownedPurchases = purchases
                    val completed = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED
                        && it.products.any { id -> id in GooglePlayCatalog.productIds } }
                    if (completed.isEmpty()) _uiState.update { it.copy(busy = false,
                        message = if (purchases.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING })
                            "Ödeme Google Play onayı bekliyor. Paket henüz değiştirilmedi." else if (manual) "Geri yüklenecek etkin Google Play aboneliği bulunamadı." else null) }
                    for (receipt in completed) process(receipt, snapshot)
                    try { serverStatus(snapshot) }
                    catch (_: Exception) { if(same(snapshot)) _uiState.update { it.copy(server=GoogleBillingStatus(),error="Satın alma sunucusu henüz hazır değil veya ulaşılamıyor.") } }
                    if(same(snapshot)) _uiState.update { it.copy(ownershipReady=true) }
              } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                  if (same(snapshot)) _uiState.update { it.copy(ownershipReady=false,
                      error="Satın alımlar kontrol edilemedi. Lütfen tekrar deneyin.") }
              } finally {
                  if (same(snapshot)) _uiState.update { it.copy(busy=false) }
              }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val snapshot = purchaseAccount ?: account() ?: return
        purchaseAccount = null
        if (!same(snapshot)) return
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _uiState.update { it.copy(busy = false, error = "Google Play işlem sonucu alınamadı. Satın alımları geri yükleyin.") }
                } else scope.launch {
                    for (receipt in purchases) {
                        if (receipt.purchaseState == Purchase.PurchaseState.PENDING) {
                            _uiState.update { it.copy(busy = false, message = "Ödeme Google Play onayı bekliyor. Paket henüz değiştirilmedi.") }
                        } else process(receipt, snapshot)
                    }
                }
            }
            BillingResponseCode.USER_CANCELED -> _uiState.update { it.copy(busy = false, message = "Satın alma iptal edildi.") }
            else -> _uiState.update { it.copy(busy = false, error = message(result)) }
        }
    }

    private suspend fun process(receipt: Purchase, snapshot: SessionState) = mutex.withLock {
        if (!same(snapshot) || receipt.purchaseState != Purchase.PurchaseState.PURCHASED) return@withLock
        val product = receipt.products.singleOrNull { it in GooglePlayCatalog.productIds } ?: run {
            _uiState.update { it.copy(busy=false,error="Desteklenmeyen satın alma bulundu. Google Play aboneliklerini kontrol edin.") }; return@withLock
        }
        _uiState.update { it.copy(busy = true, error = null, message = "Satın alma sunucuda doğrulanıyor…") }
        try {
            val outcome = processor.process(product.uppercase(java.util.Locale.ROOT), receipt.isAcknowledged,
                sameAccount = { same(snapshot) },
                verify = { withTimeoutOrNull(45000) { api.googleVerify("Bearer ${snapshot.token}", mapOf("purchaseToken" to receipt.purchaseToken,
                    "productId" to product, "plan" to product.uppercase(java.util.Locale.ROOT))) }
                    ?: throw java.io.IOException("PLAY_VERIFICATION_TIMEOUT") },
                entitlement = {
                    ownedPurchases = ownedPurchases.filterNot { it.purchaseToken == receipt.purchaseToken } + receipt
                    runCatching { api.subscriptionMyContext("Bearer ${snapshot.token}") }.onSuccess {
                        if (same(snapshot)) sessions.mergeSubscription(it)
                    }
                },
                acknowledge = {
                    withTimeoutOrNull(15000) { suspendCancellableCoroutine { continuation ->
                        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(receipt.purchaseToken).build()) { result ->
                            if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                        }
                    } } ?: false
                })
            if(same(snapshot) && outcome!=ReceiptProcessingResult.REJECTED) runCatching { serverStatus(snapshot) }
            if (same(snapshot)) _uiState.update { state -> state.copy(busy = false, message = when (outcome) {
                ReceiptProcessingResult.VERIFIED -> if(state.server.scheduledPlan!=null)
                    "Paket değişikliğiniz planlandı. Ödenmiş dönemin sonuna kadar mevcut haklarınız korunur." else "Paketiniz doğrulandı ve güncellendi."
                ReceiptProcessingResult.ACK_PENDING -> "Paketiniz doğrulandı; Google Play işlem onayı yeniden denenecek."
                else -> null
            }, error = if (outcome == ReceiptProcessingResult.REJECTED)
                "Satın alma sunucuda doğrulanamadı. Paket güncellenmedi; satın alımları geri yükleyerek yeniden deneyin." else null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (same(snapshot)) _uiState.update { it.copy(busy = false, message = null,
                error = "Satın alma doğrulamasına ulaşılamadı. İşlemi tekrar satın almayın; geri yüklemeyi deneyin.") }
        }
    }

    private fun connectIfNeeded(action: () -> Unit) {
        if (client.isReady) { action(); return }
        ready += action
        if (connecting) return
        connecting = true
        connectionTimeout?.cancel()
        connectionTimeout=scope.launch { delay(15000); if(connecting) {
            connecting=false; ready.clear(); _uiState.update { it.copy(loading=false,busy=false,error="Google Play bağlantısı zaman aşımına uğradı. Tekrar deneyin.") }
        } }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connectionTimeout?.cancel()
                connecting = false
                connected = result.responseCode == BillingResponseCode.OK
                val callbacks = ready.toList(); ready.clear()
                if (connected) callbacks.forEach { it() }
                else _uiState.update { it.copy(loading = false, busy = false, error = message(result)) }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false; connected = false; ready.clear()
                _uiState.update { it.copy(loading = false, busy = false, error = "Google Play bağlantısı kesildi. Tekrar deneyin.") }
            }
        })
    }
    private fun changeMessage(reason: String?): String = when(reason) {
        "already_active" -> "Seçilen paket ve dönem zaten etkin."
        "pending_purchase" -> "Bekleyen bir Google Play ödemeniz var. Yeni bir satın alma başlatılmadı."
        "another_provider_active" -> "Etkin paketiniz başka bir ödeme kanalıyla yönetiliyor. Çift ödeme oluşmaması için yeni abonelik başlatılmadı."
        "account_restricted" -> "Hesabınız için satın alma şu anda kapalı. Destek ile iletişime geçin."
        "server_not_ready" -> "Satın alma sunucusu henüz hazır değil. İşlem başlatılmadı."
        "change_already_scheduled" -> "Zaten planlanmış bir paket değişikliği var. Google Play abonelik ayarlarından kontrol edin."
        else -> "Etkin abonelik güvenle eşleştirilemedi. Satın alımları geri yükleyip tekrar deneyin."
    }
    private fun message(result: BillingResult): String = when (result.responseCode) {
        BillingResponseCode.ITEM_ALREADY_OWNED -> "Etkin abonelik bulundu. Satın alımları geri yükleyin."
        BillingResponseCode.BILLING_UNAVAILABLE -> "Bu cihazda Google Play satın alma kullanılamıyor."
        BillingResponseCode.ITEM_UNAVAILABLE -> "Seçilen paket veya dönem Google Play'de kullanılamıyor."
        BillingResponseCode.NETWORK_ERROR, BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play'e ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin."
        else -> "Google Play işlemi tamamlanamadı. Daha sonra tekrar deneyin."
    }
}
