package com.pusula.service.core

import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal

class CollectionPricingTest {
    private fun d(value: String) = BigDecimal(value)
    @Test fun discountPrecedesSplitAllocation() {
        val value = CollectionPricing.calculate(d("3000"), d("2000"), "AMOUNT", d("500"), d("2000"), d("1500"))
        assertEquals(d("4500.00"), value.net); assertEquals(0, d("1000").compareTo(value.customerDebt))
        assertEquals(0, value.net.compareTo(d("2000") + d("1500") + value.customerDebt))
    }
    @Test fun fullDiscountAndZeroCollection() {
        val value = CollectionPricing.calculate(d("500"), d("0"), "PERCENT", d("100"), d("0"), d("0"))
        assertEquals(d("0.00"), value.net); assertEquals(0, value.customerDebt.signum())
    }
    @Test fun roundsHalfCentUpAndMatchesCanonicalPercentage() {
        val value = CollectionPricing.calculate(d("0.05"), d("0"), "PERCENT", d("10"), d("0"), d("0"))
        assertEquals(d("0.01"), value.discount); assertEquals(d("0.04"), value.net)
        val other = CollectionPricing.calculate(d("99.99"), d("0"), "PERCENT", d("33.33"), d("0"), d("0"))
        assertEquals(d("33.33"), other.discount); assertEquals(d("66.66"), other.net)
    }
    @Test fun acceptsLocalizedInput() {
        assertEquals(d("1500.50"), CollectionPricing.parse("1.500,50"))
        assertEquals(d("1500.50"), CollectionPricing.parse("1500.50"))
    }
    @Test fun rejectsMalformedInputRatherThanReplacingItWithZero() {
        listOf("-1", "NaN", "1,2,3", "1.234", "1.00,50", "1000000000000").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CollectionPricing.parse(value) }
        }
    }
    @Test fun validatesDiscountAndPayerLimits() {
        assertThrows(IllegalArgumentException::class.java) { CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("501"), d("0"), d("0")) }
        assertThrows(IllegalArgumentException::class.java) { CollectionPricing.calculate(d("500"), d("0"), "PERCENT", d("101"), d("0"), d("0")) }
        assertThrows(IllegalArgumentException::class.java) { CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("100"), d("401"), d("0")) }
        assertThrows(IllegalArgumentException::class.java) { CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("100"), d("300"), d("101")) }
    }
}
