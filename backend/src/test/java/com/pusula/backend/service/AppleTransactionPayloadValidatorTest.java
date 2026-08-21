package com.pusula.backend.service;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.pusula.backend.entity.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleTransactionPayloadValidatorTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private AppleTransactionPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AppleTransactionPayloadValidator(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validate_acceptsAllowedSubscriptionPayload() {
        AppleAppStoreVerificationService.AppleVerificationResult result =
                validator.validate(validPayload(), Environment.SANDBOX);

        assertEquals(PlanType.USTA, result.planType());
        assertEquals("tx-1", result.transactionId());
        assertEquals("orig-1", result.originalTransactionId());
    }

    @Test
    void validate_acceptsYearlySubscriptionPayload() {
        JWSTransactionDecodedPayload payload = validPayload()
                .productId("com.pusula.patron.yearly");

        AppleAppStoreVerificationService.AppleVerificationResult result =
                validator.validate(payload, Environment.SANDBOX);

        assertEquals(PlanType.PATRON, result.planType());
        assertEquals("com.pusula.patron.yearly", result.productId());
    }

    @Test
    void validate_rejectsUnknownProduct() {
        JWSTransactionDecodedPayload payload = validPayload().productId("com.pusula.cirak");

        assertReason(AppStoreVerificationException.Reason.PRODUCT_NOT_ALLOWED, payload);
    }

    @Test
    void validate_rejectsWrongBundleId() {
        JWSTransactionDecodedPayload payload = validPayload().bundleId("com.example.other");

        assertReason(AppStoreVerificationException.Reason.BUNDLE_MISMATCH, payload);
    }

    @Test
    void validate_rejectsExpiredSubscription() {
        JWSTransactionDecodedPayload payload = validPayload().expiresDate(NOW.minusSeconds(1).toEpochMilli());

        assertReason(AppStoreVerificationException.Reason.EXPIRED, payload);
    }

    @Test
    void validate_rejectsRevokedTransaction() {
        JWSTransactionDecodedPayload payload = validPayload().revocationDate(NOW.minusSeconds(60).toEpochMilli());

        assertReason(AppStoreVerificationException.Reason.REVOKED, payload);
    }

    private void assertReason(
            AppStoreVerificationException.Reason reason,
            JWSTransactionDecodedPayload payload) {
        AppStoreVerificationException ex = assertThrows(
                AppStoreVerificationException.class,
                () -> validator.validate(payload, Environment.SANDBOX));
        assertEquals(reason, ex.getReason());
    }

    private JWSTransactionDecodedPayload validPayload() {
        return new JWSTransactionDecodedPayload()
                .transactionId("tx-1")
                .originalTransactionId("orig-1")
                .productId("com.pusula.usta")
                .bundleId("com.pusula.service")
                .environment(Environment.SANDBOX)
                .type(Type.AUTO_RENEWABLE_SUBSCRIPTION)
                .purchaseDate(NOW.minusSeconds(60).toEpochMilli())
                .expiresDate(NOW.plusSeconds(3600).toEpochMilli());
    }
}
