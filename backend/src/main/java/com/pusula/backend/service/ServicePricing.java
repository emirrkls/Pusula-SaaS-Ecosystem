package com.pusula.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One financial contract for all clients: discount the sale before allocating payers. */
public final class ServicePricing {
    private ServicePricing() {}
    public record Totals(BigDecimal subtotal, String discountType, BigDecimal discountValue,
                         BigDecimal discountAmount, BigDecimal invoiceTotal) {}

    public static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new IllegalArgumentException("Tutar sıfır veya pozitif ve geçerli para sınırları içinde olmalıdır.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals calculate(BigDecimal parts, BigDecimal labor, String type, BigDecimal value) {
        BigDecimal subtotal = money(money(parts).add(money(labor)));
        String effectiveType = type == null ? "AMOUNT" : type;
        if (!effectiveType.equals("AMOUNT") && !effectiveType.equals("PERCENT")) {
            throw new IllegalArgumentException("İskonto türü TL veya yüzde olmalıdır.");
        }
        if (value != null && value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("İskonto en fazla iki ondalık basamak içerebilir.");
        }
        BigDecimal entered = money(value == null ? BigDecimal.ZERO : value);
        if (effectiveType.equals("PERCENT") && entered.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("İskonto yüzdesi 0 ile 100 arasında olmalıdır.");
        }
        BigDecimal discount = effectiveType.equals("PERCENT")
                ? subtotal.multiply(entered).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : entered;
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("İskonto ara toplamı aşamaz.");
        }
        return new Totals(subtotal, effectiveType, entered, discount, subtotal.subtract(discount));
    }
}
