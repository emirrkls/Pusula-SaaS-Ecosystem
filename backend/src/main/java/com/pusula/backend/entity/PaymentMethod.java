package com.pusula.backend.entity;

/**
 * Payment method for service tickets
 * CASH and CREDIT_CARD are liquid payments that go into the safe
 * CURRENT_ACCOUNT creates a debt record, not liquid cash
 */
public enum PaymentMethod {
    CASH, // Nakit - goes to safe
    CREDIT_CARD, // Kredi Kartı - goes to safe
    CURRENT_ACCOUNT // Cari Hesap - creates debt, NOT liquid cash
}
