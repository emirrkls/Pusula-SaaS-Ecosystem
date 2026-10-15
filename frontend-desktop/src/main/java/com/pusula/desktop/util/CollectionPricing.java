package com.pusula.desktop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Client preview of the authoritative server pricing contract. */
public final class CollectionPricing {
    private CollectionPricing() {}
    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal net,
                         BigDecimal customerShare, BigDecimal customerDebt) {}
    public static BigDecimal parse(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return BigDecimal.ZERO;
        if (value.contains(",")) {
            if (!value.matches("[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]{0,2})?")) {
                throw new IllegalArgumentException("Tutarı en fazla iki ondalıkla girin. Örn. 1.500,00");
            }
            value = value.replace(".", "").replace(',', '.');
        }
        if (!value.matches("[0-9]+(?:\\.[0-9]{0,2})?")) {
            throw new IllegalArgumentException("Geçerli, negatif olmayan bir tutar girin.");
        }
        return money(new BigDecimal(value));
    }
    public static BigDecimal money(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new IllegalArgumentException("Tutar geçerli para sınırları içinde olmalıdır.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    public static Totals calculate(BigDecimal parts, BigDecimal labor, String type, BigDecimal value,
                                   BigDecimal organization, BigDecimal collected) {
        BigDecimal subtotal = money(money(parts).add(money(labor)));
        money(value); money(organization); money(collected);
        if (!"AMOUNT".equals(type) && !"PERCENT".equals(type)) throw new IllegalArgumentException("İskonto türünü seçin.");
        if ("PERCENT".equals(type) && value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("İskonto yüzdesi 100'ü aşamaz.");
        }
        BigDecimal discount = "PERCENT".equals(type)
                ? subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP) : money(value);
        if (discount.compareTo(subtotal) > 0) throw new IllegalArgumentException("İskonto ara toplamı aşamaz.");
        BigDecimal net = subtotal.subtract(discount);
        if (organization.compareTo(net) > 0) throw new IllegalArgumentException("Kurum payı net fişi aşamaz.");
        BigDecimal share = net.subtract(organization);
        if (collected.compareTo(share) > 0) throw new IllegalArgumentException("Tahsilat müşteri payını aşamaz.");
        return new Totals(subtotal, discount, net, share, share.subtract(collected));
    }
}
