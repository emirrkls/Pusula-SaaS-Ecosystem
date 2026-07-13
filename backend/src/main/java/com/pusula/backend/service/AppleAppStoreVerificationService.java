package com.pusula.backend.service;

import com.pusula.backend.entity.PlanType;

import java.time.LocalDateTime;

public interface AppleAppStoreVerificationService {

    AppleVerificationResult verifyTransaction(String signedTransactionInfo);

    record AppleVerificationResult(
            String transactionId,
            String originalTransactionId,
            String productId,
            PlanType planType,
            String bundleId,
            String environment,
            LocalDateTime purchaseDate,
            LocalDateTime expiresDate
    ) {
    }
}
