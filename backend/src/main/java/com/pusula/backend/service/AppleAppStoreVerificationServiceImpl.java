package com.pusula.backend.service;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.pusula.backend.entity.PlanType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppleAppStoreVerificationServiceImpl implements AppleAppStoreVerificationService {

    private static final String EXPECTED_BUNDLE_ID = "com.pusula.service";
    private static final Map<String, PlanType> PRODUCT_PLANS = Map.of(
            "com.pusula.usta", PlanType.USTA,
            "com.pusula.patron", PlanType.PATRON
    );

    @Value("${apple.app-store.bundle-id:" + EXPECTED_BUNDLE_ID + "}")
    private String bundleId;

    @Value("${apple.app-store.app-apple-id:}")
    private String appAppleId;

    @Value("${apple.app-store.environments:SANDBOX,PRODUCTION}")
    private String enabledEnvironments;

    @Value("${apple.app-store.root-certificate-paths:}")
    private String rootCertificatePaths;

    @Value("${apple.app-store.enable-online-checks:true}")
    private boolean enableOnlineChecks;

    @Override
    public AppleVerificationResult verifyTransaction(String signedTransactionInfo) {
        if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.MALFORMED,
                    "Apple signedTransactionInfo eksik");
        }

        Set<Environment> environments = parseEnabledEnvironments();
        AppStoreVerificationException lastFailure = null;
        for (Environment environment : environments) {
            try {
                JWSTransactionDecodedPayload payload = verifier(environment)
                        .verifyAndDecodeTransaction(signedTransactionInfo);
                return validatePayload(payload, environment);
            } catch (VerificationException ex) {
                lastFailure = new AppStoreVerificationException(
                        AppStoreVerificationException.Reason.VERIFICATION_FAILED,
                        "Apple imza dogrulamasi basarisiz");
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AppStoreVerificationException(
                AppStoreVerificationException.Reason.CONFIGURATION,
                "Apple environment konfigürasyonu eksik");
    }

    private SignedDataVerifier verifier(Environment environment) {
        Long appId = parseAppAppleId(environment);
        Set<InputStream> rootCertificates = openRootCertificates();
        return new SignedDataVerifier(rootCertificates, bundleId, appId, environment, enableOnlineChecks);
    }

    private AppleVerificationResult validatePayload(JWSTransactionDecodedPayload payload, Environment verifierEnvironment) {
        if (payload == null) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.VERIFICATION_FAILED,
                    "Apple transaction okunamadi");
        }
        if (!EXPECTED_BUNDLE_ID.equals(payload.getBundleId())) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.BUNDLE_MISMATCH,
                    "Apple bundleId uyumsuz");
        }
        if (payload.getEnvironment() != null && payload.getEnvironment() != verifierEnvironment) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.ENVIRONMENT_NOT_ALLOWED,
                    "Apple environment uyumsuz");
        }
        if (payload.getType() != Type.AUTO_RENEWABLE_SUBSCRIPTION) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.NOT_SUBSCRIPTION,
                    "Apple transaction abonelik degil");
        }
        if (payload.getRevocationDate() != null) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.REVOKED,
                    "Apple transaction revoke edilmis");
        }

        LocalDateTime expiresAt = millisToUtc(payload.getExpiresDate());
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.EXPIRED,
                    "Apple aboneligin suresi dolmus");
        }

        PlanType planType = PRODUCT_PLANS.get(payload.getProductId());
        if (planType == null) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.PRODUCT_NOT_ALLOWED,
                    "Apple productId tanimli degil");
        }
        if (payload.getTransactionId() == null || payload.getTransactionId().isBlank()
                || payload.getOriginalTransactionId() == null || payload.getOriginalTransactionId().isBlank()) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.MALFORMED,
                    "Apple transaction kimligi eksik");
        }

        return new AppleVerificationResult(
                payload.getTransactionId(),
                payload.getOriginalTransactionId(),
                payload.getProductId(),
                planType,
                payload.getBundleId(),
                payload.getEnvironment() != null ? payload.getEnvironment().getValue() : verifierEnvironment.getValue(),
                millisToUtc(payload.getPurchaseDate()),
                expiresAt);
    }

    private Set<Environment> parseEnabledEnvironments() {
        return Arrays.stream(enabledEnvironments.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> Environment.valueOf(value.toUpperCase(Locale.ROOT)))
                .filter(environment -> environment == Environment.SANDBOX || environment == Environment.PRODUCTION)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Long parseAppAppleId(Environment environment) {
        if (environment != Environment.PRODUCTION) {
            return null;
        }
        if (appAppleId == null || appAppleId.isBlank()) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.CONFIGURATION,
                    "Production icin Apple App Apple ID gerekli");
        }
        try {
            return Long.valueOf(appAppleId.trim());
        } catch (NumberFormatException ex) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.CONFIGURATION,
                    "Apple App Apple ID gecersiz");
        }
    }

    private Set<InputStream> openRootCertificates() {
        if (rootCertificatePaths == null || rootCertificatePaths.isBlank()) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.CONFIGURATION,
                    "Apple root certificate konfigürasyonu eksik");
        }
        try {
            return Arrays.stream(rootCertificatePaths.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(this::openCertificate)
                    .collect(Collectors.toSet());
        } catch (RuntimeException ex) {
            if (ex instanceof AppStoreVerificationException appEx) {
                throw appEx;
            }
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.CONFIGURATION,
                    "Apple root certificate okunamadi");
        }
    }

    private InputStream openCertificate(String path) {
        try {
            return new FileInputStream(path);
        } catch (IOException ex) {
            throw new AppStoreVerificationException(
                    AppStoreVerificationException.Reason.CONFIGURATION,
                    "Apple root certificate okunamadi");
        }
    }

    private LocalDateTime millisToUtc(Long millis) {
        if (millis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }
}
