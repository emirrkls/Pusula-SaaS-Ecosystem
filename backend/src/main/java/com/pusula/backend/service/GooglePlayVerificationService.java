package com.pusula.backend.service;

public interface GooglePlayVerificationService {

    GoogleVerificationResult verifySubscription(String purchaseToken, String productId);

    record GoogleVerificationResult(
            boolean valid,
            String subscriptionId,
            String reason
    ) {
    }
}
