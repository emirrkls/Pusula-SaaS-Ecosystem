package com.pusula.desktop.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CollectionPricingTest {
    private BigDecimal d(String value) { return new BigDecimal(value); }
    @Test void splitCollectionBalancesAfterDiscount() {
        var result = CollectionPricing.calculate(d("3000"), d("2000"), "AMOUNT", d("500"), d("2000"), d("1500"));
        assertEquals(d("4500.00"), result.net()); assertEquals(0, d("1000").compareTo(result.customerDebt()));
        assertEquals(0, result.net().compareTo(d("2000").add(d("1500")).add(result.customerDebt())));
    }
    @Test void fullDiscountAllowsZeroCollection() {
        var result = CollectionPricing.calculate(d("500"), d("0"), "PERCENT", d("100"), d("0"), d("0"));
        assertEquals(d("0.00"), result.net()); assertEquals(0, result.customerDebt().signum());
    }
    @Test void roundsHalfCentUp() {
        var result = CollectionPricing.calculate(d("0.05"), d("0"), "PERCENT", d("10"), d("0"), d("0"));
        assertEquals(d("0.01"), result.discount()); assertEquals(d("0.04"), result.net());
    }
    @Test void turkishAndPlainDecimalInputAgree() {
        assertEquals(d("1500.50"), CollectionPricing.parse("1.500,50"));
        assertEquals(d("1500.50"), CollectionPricing.parse("1500.50"));
        assertEquals(d("0.00"), CollectionPricing.parse("0"));
    }
    @Test void malformedAndNegativeInputsAreNotSilentlyZero() {
        for (String value : new String[]{"-1", "NaN", "1,2,3", "1.234", "1.00,50", "1000000000000"}) {
            assertThrows(IllegalArgumentException.class, () -> CollectionPricing.parse(value), value);
        }
    }
    @Test void validatesDiscountOrganizationAndCustomerLimits() {
        assertThrows(IllegalArgumentException.class, () -> CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("501"), d("0"), d("0")));
        assertThrows(IllegalArgumentException.class, () -> CollectionPricing.calculate(d("500"), d("0"), "PERCENT", d("101"), d("0"), d("0")));
        assertThrows(IllegalArgumentException.class, () -> CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("100"), d("401"), d("0")));
        assertThrows(IllegalArgumentException.class, () -> CollectionPricing.calculate(d("500"), d("0"), "AMOUNT", d("100"), d("300"), d("101")));
    }
}
