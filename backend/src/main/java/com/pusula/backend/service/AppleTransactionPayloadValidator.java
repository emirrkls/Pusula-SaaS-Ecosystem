package com.pusula.backend.service;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.pusula.backend.entity.PlanType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class AppleTransactionPayloadValidator {

    static final String EXPECTED_BUNDLE_ID = "com.pusula.service";
    private static final Map<String, PlanType> PRODUCT_PLANS = Map.of(
            "com.pusula.usta", PlanType.USTA,
            "com.pusula.usta.yearly", PlanType.USTA,
            "com.pusula.patron", PlanType.PATRON,
            "com.pusula.patron.yearly", PlanType.PATRON
    );

    private final Clock clock;

    public AppleTransactionPayloadValidator() {
        this(Clock.systemUTC());
    }

    AppleTransactionPayloadValidator(Clock clock) {
        this.clock = clock;
    }

    public AppleAppStoreVerificationService.AppleVerificationResult validate(
            JWSTransactionDecodedPayload payload,
            Environment verifierEnvironment) {
        var result = validateIdentity(payload, verifierEnvironment);
        if (payload.getRevocationDate() != null) {
            throw failure(AppStoreVerificationException.Reason.REVOKED, "Apple transaction revoke edilmis");
        }
        if (!result.expiresDate().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw failure(AppStoreVerificationException.Reason.EXPIRED, "Apple aboneligin suresi dolmus");
        }
        return result;
    }

    // Notifications must also accept cryptographically verified historical/revoked
    // transactions. This does NOT relax the active-purchase verification endpoint.
    AppleAppStoreVerificationService.AppleVerificationResult validateIdentity(
            JWSTransactionDecodedPayload payload, Environment verifierEnvironment) {
        if (payload == null) {
            throw failure(AppStoreVerificationException.Reason.VERIFICATION_FAILED, "Apple transaction okunamadi");
        }
        if (!EXPECTED_BUNDLE_ID.equals(payload.getBundleId())) {
            throw failure(AppStoreVerificationException.Reason.BUNDLE_MISMATCH, "Apple bundleId uyumsuz");
        }
        if (payload.getEnvironment() != null && payload.getEnvironment() != verifierEnvironment) {
            throw failure(AppStoreVerificationException.Reason.ENVIRONMENT_NOT_ALLOWED, "Apple environment uyumsuz");
        }
        if (payload.getType() != Type.AUTO_RENEWABLE_SUBSCRIPTION) {
            throw failure(AppStoreVerificationException.Reason.NOT_SUBSCRIPTION, "Apple transaction abonelik degil");
        }
        LocalDateTime expiresAt = millisToUtc(payload.getExpiresDate());
        if (expiresAt == null || payload.getPurchaseDate() == null) {
            throw failure(AppStoreVerificationException.Reason.MALFORMED, "Apple transaction tarihleri eksik");
        }

        PlanType planType = PRODUCT_PLANS.get(payload.getProductId());
        if (planType == null) {
            throw failure(AppStoreVerificationException.Reason.PRODUCT_NOT_ALLOWED, "Apple productId tanimli degil");
        }
        if (isBlank(payload.getTransactionId()) || isBlank(payload.getOriginalTransactionId())) {
            throw failure(AppStoreVerificationException.Reason.MALFORMED, "Apple transaction kimligi eksik");
        }

        return new AppleAppStoreVerificationService.AppleVerificationResult(
                payload.getTransactionId(),
                payload.getOriginalTransactionId(),
                payload.getProductId(),
                planType,
                payload.getBundleId(),
                payload.getEnvironment() != null
                        ? payload.getEnvironment().getValue()
                        : verifierEnvironment.getValue(),
                millisToUtc(payload.getPurchaseDate()),
                expiresAt,
                payload.getSignedDate());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AppStoreVerificationException failure(
            AppStoreVerificationException.Reason reason,
            String message) {
        return new AppStoreVerificationException(reason, message);
    }

    private LocalDateTime millisToUtc(Long millis) {
        if (millis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }
}
