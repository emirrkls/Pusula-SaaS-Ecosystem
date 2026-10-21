package com.pusula.service.core

import androidx.annotation.Keep

enum class BillingPeriod(val iso: String, val title: String, val suffix: String) {
    MONTHLY("P1M", "Aylık", "/ay"), YEARLY("P1Y", "Yıllık", "/yıl")
}

data class PlayPricePhase(val formattedPrice: String, val priceMicros: Long, val period: String,
                          val currency: String, val infiniteRecurring: Boolean)
data class PlayOffer(val productId: String, val basePlanId: String, val offerId: String?,
                     val offerToken: String, val phases: List<PlayPricePhase>)
data class PlayPlanOption(val productId: String, val basePlanId: String, val offerToken: String,
                          val period: BillingPeriod, val formattedPrice: String, val currency: String)
data class PlayBillingUiState(val options: List<PlayPlanOption> = emptyList(), val loading: Boolean = false,
                              val busy: Boolean = false, val error: String? = null, val message: String? = null,
                              val server: GoogleBillingStatus = GoogleBillingStatus(), val ownershipReady: Boolean = false)

@Keep
data class GoogleBillingStatus(val ready: Boolean = false, val purchaseAllowed: Boolean = false,
    val reason: String? = null, val plan: String? = null, val receiptHash: String? = null,
    val basePlanId: String? = null, val scheduledPlan: String? = null)

object GooglePlayCatalog {
    val productIds = setOf("usta", "patron")
    fun options(offers: List<PlayOffer>): List<PlayPlanOption> = offers.mapNotNull { offer ->
        // Only show the standard recurring base plan. Do not silently choose an intro/trial offer,
        // price from our backend, or one of multiple ambiguous base plans for the same period.
        val phase = offer.phases.singleOrNull() ?: return@mapNotNull null
        val period = BillingPeriod.entries.singleOrNull { it.iso == phase.period } ?: return@mapNotNull null
        if (offer.productId !in productIds || offer.offerId != null || offer.offerToken.isBlank()
            || offer.basePlanId.isBlank() || !phase.infiniteRecurring || phase.priceMicros <= 0
            || phase.formattedPrice.isBlank() || phase.currency.isBlank()) return@mapNotNull null
        PlayPlanOption(offer.productId, offer.basePlanId, offer.offerToken, period, phase.formattedPrice, phase.currency)
    }.groupBy { it.productId to it.period }.values.mapNotNull { it.singleOrNull() }

    fun accountBinding(companyId: Int): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("pusula:company:$companyId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun receiptHash(token: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

@Keep
data class GooglePurchaseVerification(val verified: Boolean = false, val idempotentReplay: Boolean = false,
                                      val plan: String? = null, val subscriptionId: String? = null,
                                      val status: String? = null, val scheduledPlan: String? = null,
                                      val basePlanId: String? = null, val receiptHash: String? = null)
enum class ReceiptProcessingResult { VERIFIED, REJECTED, ACCOUNT_CHANGED, ACK_PENDING }

/** Platform-free ordering policy, tested with failed HTTP-200 verification and account switches. */
class GooglePurchaseProcessor {
    suspend fun process(expectedPlan: String, alreadyAcknowledged: Boolean,
                        sameAccount: () -> Boolean, verify: suspend () -> GooglePurchaseVerification,
                        entitlement: suspend () -> Unit, acknowledge: suspend () -> Boolean): ReceiptProcessingResult {
        if (!sameAccount()) return ReceiptProcessingResult.ACCOUNT_CHANGED
        val result = verify()
        if (!sameAccount()) return ReceiptProcessingResult.ACCOUNT_CHANGED
        if (!result.verified || (result.plan != expectedPlan && result.scheduledPlan != expectedPlan)
            || result.plan !in setOf("USTA", "PATRON") || result.subscriptionId.isNullOrBlank()
            || result.status != "processed") return ReceiptProcessingResult.REJECTED
        entitlement()
        if (!sameAccount()) return ReceiptProcessingResult.ACCOUNT_CHANGED
        if (alreadyAcknowledged) return ReceiptProcessingResult.VERIFIED
        return if (acknowledge()) ReceiptProcessingResult.VERIFIED else ReceiptProcessingResult.ACK_PENDING
    }
}
