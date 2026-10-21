package com.pusula.service.core

// Opaque purchase tokens stay in memory; these classes deliberately have no generated toString().
class OwnedPlaySubscription(val token: String, val products: List<String>, val pending: Boolean,
                            val purchased: Boolean, val accountBinding: String?)
enum class PlayChangeMode { NEW, TIME_CREDIT, DEFERRED, NEXT_RENEWAL }
class PlayChangeDecision(val allowed: Boolean, val reason: String? = null,
                         val oldToken: String? = null, val oldProduct: String? = null,
                         val mode: PlayChangeMode = PlayChangeMode.NEW)

object GoogleReplacementPolicy {
    fun decide(companyId: Int, status: GoogleBillingStatus, owned: List<OwnedPlaySubscription>,
               target: PlayPlanOption): PlayChangeDecision {
        if (!status.ready || !status.purchaseAllowed) return PlayChangeDecision(false,status.reason ?: "server_not_ready")
        if (owned.any { it.pending }) return PlayChangeDecision(false,"pending_purchase")
        val active=owned.filter { it.purchased }
        if (active.isEmpty()) return PlayChangeDecision(true)
        val old=active.singleOrNull() ?: return PlayChangeDecision(false,"ambiguous_purchases")
        val oldProduct=old.products.singleOrNull()?.takeIf { it in GooglePlayCatalog.productIds }
            ?: return PlayChangeDecision(false,"unsupported_purchase")
        if (old.token.isBlank() || GooglePlayCatalog.receiptHash(old.token)!=status.receiptHash
            || status.basePlanId.isNullOrBlank() || !oldProduct.equals(status.plan,true)
            || (old.accountBinding!=null && old.accountBinding!=GooglePlayCatalog.accountBinding(companyId)))
            return PlayChangeDecision(false,"restore_required")
        if (status.scheduledPlan!=null) return PlayChangeDecision(false,"change_already_scheduled")
        if (oldProduct==target.productId && status.basePlanId==target.basePlanId)
            return PlayChangeDecision(false,"already_active")
        val mode=when {
            oldProduct==target.productId -> PlayChangeMode.NEXT_RENEWAL
            oldProduct=="usta" && target.productId=="patron" -> PlayChangeMode.TIME_CREDIT
            oldProduct=="patron" && target.productId=="usta" -> PlayChangeMode.DEFERRED
            else -> return PlayChangeDecision(false,"unsupported_purchase")
        }
        return PlayChangeDecision(true,oldToken=old.token,oldProduct=oldProduct,mode=mode)
    }
}
