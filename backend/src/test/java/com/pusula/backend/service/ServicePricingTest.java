package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ServicePricingTest {
    @ParameterizedTest
    @CsvSource({"3000,2000,AMOUNT,500,500.00,4500.00", "3000,2000,PERCENT,10,500.00,4500.00",
            "0,0,AMOUNT,0,0.00,0.00", "500,0,PERCENT,100,500.00,0.00",
            "0.05,0,PERCENT,10,0.01,0.04", "99.99,0,PERCENT,33.33,33.33,66.66",
            "0.005,0,AMOUNT,0,0.00,0.01", "250,0,AMOUNT,0,0.00,250.00"})
    void canonicalMoneyCases(String parts, String labor, String type, String value, String discount, String net) {
        var result = ServicePricing.calculate(new BigDecimal(parts), new BigDecimal(labor), type, new BigDecimal(value));
        assertEquals(new BigDecimal(discount), result.discountAmount());
        assertEquals(new BigDecimal(net), result.invoiceTotal());
        assertEquals(result.subtotal(), result.invoiceTotal().add(result.discountAmount()));
    }
    @ParameterizedTest @CsvSource({"AMOUNT,-1", "AMOUNT,501", "PERCENT,100.01", "INVALID,0"})
    void rejectsInvalidDiscounts(String type, String value) {
        assertThrows(IllegalArgumentException.class, () -> ServicePricing.calculate(new BigDecimal("500"), BigDecimal.ZERO, type, new BigDecimal(value)));
    }
    @Test void olderClientsDefaultToZeroDiscount() {
        var result = ServicePricing.calculate(new BigDecimal("500"), BigDecimal.ZERO, null, null);
        assertEquals(new BigDecimal("500.00"), result.invoiceTotal());
        assertEquals(new BigDecimal("0.00"), result.discountAmount());
    }
    @Test void rejectsNegativeAndOverflowAmounts() {
        assertThrows(IllegalArgumentException.class, () -> ServicePricing.calculate(BigDecimal.ONE, BigDecimal.ONE.negate(), null, null));
        assertThrows(IllegalArgumentException.class, () -> ServicePricing.calculate(new BigDecimal("999999999999.99"), BigDecimal.ONE, null, null));
        assertThrows(IllegalArgumentException.class, () -> ServicePricing.calculate(new BigDecimal("500"), BigDecimal.ZERO, "PERCENT", new BigDecimal("100.004")));
    }
}
