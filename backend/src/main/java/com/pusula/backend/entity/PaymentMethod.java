package com.pusula.backend.entity;

/**
 * Payment method for service tickets
 * CASH and CREDIT_CARD are liquid payments that go into the safe
 * CURRENT_ACCOUNT creates a debt record, not liquid cash.
 * WARRANTY closes the work free of charge while preserving its real service cost.
 */
public enum PaymentMethod {
    CASH, // Nakit - goes to safe
    CREDIT_CARD, // Kredi Kartı - goes to safe
    CURRENT_ACCOUNT, // Cari Hesap - creates debt, NOT liquid cash
    WARRANTY // Garanti kapsaminda - no revenue, collection or customer debt
}
