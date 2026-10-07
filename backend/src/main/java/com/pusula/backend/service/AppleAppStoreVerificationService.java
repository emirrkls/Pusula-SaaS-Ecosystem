package com.pusula.backend.service;

import com.pusula.backend.entity.PlanType;

import java.time.LocalDateTime;

public interface AppleAppStoreVerificationService {

    AppleVerificationResult verifyTransaction(String signedTransactionInfo);

    AppleNotificationResult verifyNotification(String signedPayload);

    record AppleNotificationResult(
            String notificationId, String type, String subtype, String environment,
            Long signedDate, AppleVerificationResult transaction, boolean revoked,
            String status, LocalDateTime graceExpiresDate) {}

    record AppleVerificationResult(
            String transactionId,
            String originalTransactionId,
            String productId,
            PlanType planType,
            String bundleId,
            String environment,
            LocalDateTime purchaseDate,
            LocalDateTime expiresDate,
            Long signedDate
    ) {
        public AppleVerificationResult(String transactionId, String originalTransactionId,
                String productId, PlanType planType, String bundleId, String environment,
                LocalDateTime purchaseDate, LocalDateTime expiresDate) {
            this(transactionId, originalTransactionId, productId, planType, bundleId,
                    environment, purchaseDate, expiresDate, null);
        }
    }
}
