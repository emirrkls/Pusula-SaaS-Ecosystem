package com.pusula.service.core

import com.pusula.service.data.model.QuotaDTO
import kotlin.math.roundToInt

private const val NEAR_PERCENT = 85

/**
 * Kota sınırına yaklaşma ya da dolma için kullanıcıya gösterilecek kısa mesaj.
 * [QuotaDTO.max*] \<= 0 sınırsız kabul edilir.
 */
fun quotaNearOrExceededMessage(q: QuotaDTO): String? {
    data class Slice(val labelTr: String, val current: Int, val max: Int)

    val slices = listOf(
        Slice("Teknisyen", q.currentTechnicians, q.maxTechnicians),
        Slice("Müşteri", q.currentCustomers, q.maxCustomers),
        Slice("Bu ay iş emri", q.currentMonthlyTickets, q.maxMonthlyTickets),
        Slice("Bu ay teklif", q.currentMonthlyProposals, q.maxMonthlyProposals),
        Slice("Stok kalemi", q.currentInventoryItems, q.maxInventoryItems),
        Slice("Dosya deposu (MB)", q.currentStorageMb, q.storageLimitMb)
    )

    val exceeded = slices.firstOrNull { it.max > 0 && it.current >= it.max }
    if (exceeded != null) {
        return "${exceeded.labelTr} kotanız doldu: ${exceeded.current}/${exceeded.max}"
    }

    val near = slices.firstOrNull { slice ->
        if (slice.max <= 0 || slice.max <= slice.current) return@firstOrNull false
        val pct = 100f * slice.current.toFloat() / slice.max.toFloat()
        pct >= NEAR_PERCENT
    }
    if (near != null) {
        val pct = (100f * near.current.toFloat() / near.max.toFloat()).roundToInt().coerceIn(0, 100)
        return "${near.labelTr} kotanıza yaklaşıyorsunuz: %${pct} (${near.current}/${near.max}). Paket yükseltmeyi düşünün."
    }

    return null
}
