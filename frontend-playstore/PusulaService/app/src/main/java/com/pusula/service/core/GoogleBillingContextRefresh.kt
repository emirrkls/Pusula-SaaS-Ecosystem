package com.pusula.service.core

import com.pusula.service.data.model.SubscriptionContextDto
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Refresh access before publishing billing readiness, without applying another account's response. */
internal suspend fun refreshGoogleBillingContext(
    sameAccount: () -> Boolean,
    loadContext: suspend () -> SubscriptionContextDto,
    applyContext: (SubscriptionContextDto) -> Unit,
    loadStatus: suspend () -> GoogleBillingStatus,
    applyStatus: (GoogleBillingStatus) -> Unit
): GoogleBillingStatus {
    if (!sameAccount()) throw CancellationException("Billing account changed")
    val context = loadContext()
    if (!sameAccount()) throw CancellationException("Billing account changed")
    if (context.isReadOnly == null) throw IOException("SUBSCRIPTION_ACCESS_UNAVAILABLE")
    applyContext(context)
    val status = loadStatus()
    if (!sameAccount()) throw CancellationException("Billing account changed")
    applyStatus(status)
    return status
}
