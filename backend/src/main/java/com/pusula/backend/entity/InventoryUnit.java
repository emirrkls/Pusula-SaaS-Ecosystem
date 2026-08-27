package com.pusula.backend.entity;

import java.util.Locale;

/** Unit used for inventory stock, service usage and unit pricing. */
public enum InventoryUnit {
    ADET(false),
    KG(true),
    GRAM(true),
    METRE(true),
    LITRE(true);

    private final boolean fractional;

    InventoryUnit(boolean fractional) {
        this.fractional = fractional;
    }

    public boolean allowsFractionalQuantity() {
        return fractional;
    }

    public static InventoryUnit fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return ADET;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Geçersiz ölçü birimi: " + value);
        }
    }
}
