package com.pusula.service.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Same decimal and allocation contract as backend ServicePricing and the other clients. */
object CollectionPricing {
    data class Totals(val subtotal: BigDecimal, val discount: BigDecimal, val net: BigDecimal,
                      val customerShare: BigDecimal, val customerDebt: BigDecimal)
    private val limit = BigDecimal("999999999999.99")
    fun money(value: BigDecimal): BigDecimal {
        require(value >= BigDecimal.ZERO && value <= limit) { "Tutar geçerli para sınırları içinde olmalıdır." }
        return value.setScale(2, RoundingMode.HALF_UP)
    }
    fun parse(text: String): BigDecimal {
        var value = text.trim()
        if (value.isEmpty()) return BigDecimal.ZERO
        if (value.contains(',')) {
            require(Regex("[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]{0,2})?").matches(value)) { "Tutarı en fazla iki ondalıkla girin. Örn. 1.500,00" }
            value = value.replace(".", "").replace(',', '.')
        }
        require(Regex("[0-9]+(?:\\.[0-9]{0,2})?").matches(value)) { "Geçerli, negatif olmayan bir tutar girin." }
        return money(value.toBigDecimal())
    }
    fun calculate(parts: BigDecimal, labor: BigDecimal, type: String, value: BigDecimal,
                  organization: BigDecimal, collected: BigDecimal): Totals {
        val subtotal = money(money(parts) + money(labor))
        money(value); money(organization); money(collected)
        require(type == "AMOUNT" || type == "PERCENT") { "İskonto türünü seçin." }
        require(type != "PERCENT" || value <= BigDecimal("100")) { "İskonto yüzdesi 100'ü aşamaz." }
        val discount = if (type == "PERCENT") (subtotal * value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP) else money(value)
        require(discount <= subtotal) { "İskonto ara toplamı aşamaz." }
        val net = subtotal - discount
        require(organization <= net) { "Kurum payı net fişi aşamaz." }
        val share = net - organization
        require(collected <= share) { "Tahsilat müşteri payını aşamaz." }
        return Totals(subtotal, discount, net, share, share - collected)
    }
}
