package com.pusula.backend.service;

import java.time.Instant;

public interface GooglePlayVerificationService {

    /** productId may be null only for internal RTDN reconciliation, never for a client ownership claim. */
    GoogleVerificationResult verifySubscription(String purchaseToken, String productId);

    record GoogleVerificationResult(
            boolean valid,
            String subscriptionId,
            String productId,
            String reason,
            Instant expiresAt,
            String state,
            boolean retryable,
            String obfuscatedAccountId,
            String linkedTokenHash,
            String basePlanId,
            String deferredProductId,
            String acknowledgementState
    ) {
        public GoogleVerificationResult(boolean valid, String subscriptionId, String productId, String reason,
                Instant expiresAt, String state, boolean retryable, String account) {
            this(valid, subscriptionId, productId, reason, expiresAt, state, retryable, account, null, null, null, null);
        }
        public GoogleVerificationResult(boolean valid, String subscriptionId, String productId, String reason) {
            this(valid, subscriptionId, productId, reason, null, null, false, null);
        }
    }
}
