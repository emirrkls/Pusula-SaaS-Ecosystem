package com.pusula.backend.service;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AppleAppStoreVerificationServiceImpl implements AppleAppStoreVerificationService {

    private final AppleTransactionPayloadValidator payloadValidator;
    private final ConcurrentMap<Environment, SignedDataVerifier> verifierCache = new ConcurrentHashMap<>();

    public AppleAppStoreVerificationServiceImpl(AppleTransactionPayloadValidator payloadValidator) {
        this.payloadValidator = payloadValidator;
    }

    @Value("${apple.app-store.bundle-id:" + AppleTransactionPayloadValidator.EXPECTED_BUNDLE_ID + "}")
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
                return payloadValidator.validate(payload, environment);
            } catch (VerificationException ex) {
                lastFailure = new AppStoreVerificationException(
                        AppStoreVerificationException.Reason.VERIFICATION_FAILED,
                        "Apple imza dogrulamasi basarisiz");
            }
        }
        throw lastFailure != null
                ? lastFailure
                : configurationFailure("Apple environment konfigurasyonu eksik");
    }

    private SignedDataVerifier verifier(Environment environment) {
        return verifierCache.computeIfAbsent(environment, this::createVerifier);
    }

    private SignedDataVerifier createVerifier(Environment environment) {
        if (!AppleTransactionPayloadValidator.EXPECTED_BUNDLE_ID.equals(bundleId)) {
            throw configurationFailure("Apple bundle ID konfigurasyonu gecersiz");
        }
        Long appId = parseAppAppleId(environment);
        if (rootCertificatePaths == null || rootCertificatePaths.isBlank()) {
            throw configurationFailure("Apple root certificate konfigurasyonu eksik");
        }

        List<InputStream> rootCertificates = new ArrayList<>();
        try {
            Arrays.stream(rootCertificatePaths.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(this::openCertificate)
                    .forEach(rootCertificates::add);
            if (rootCertificates.isEmpty()) {
                throw configurationFailure("Apple root certificate konfigurasyonu eksik");
            }
            return new SignedDataVerifier(
                    new HashSet<>(rootCertificates),
                    bundleId,
                    appId,
                    environment,
                    enableOnlineChecks);
        } catch (RuntimeException ex) {
            if (ex instanceof AppStoreVerificationException appEx) {
                throw appEx;
            }
            throw configurationFailure("Apple verifier olusturulamadi");
        } finally {
            rootCertificates.forEach(this::closeCertificate);
        }
    }

    private Set<Environment> parseEnabledEnvironments() {
        if (enabledEnvironments == null || enabledEnvironments.isBlank()) {
            throw configurationFailure("Apple environment konfigurasyonu eksik");
        }
        Set<Environment> environments = new LinkedHashSet<>();
        for (String configuredValue : enabledEnvironments.split(",")) {
            String value = configuredValue.trim();
            if (value.isBlank()) {
                continue;
            }
            try {
                Environment environment = Environment.valueOf(value.toUpperCase(Locale.ROOT));
                if (environment != Environment.SANDBOX && environment != Environment.PRODUCTION) {
                    throw configurationFailure("Apple environment konfigurasyonu gecersiz");
                }
                environments.add(environment);
            } catch (IllegalArgumentException ex) {
                throw configurationFailure("Apple environment konfigurasyonu gecersiz");
            }
        }
        if (environments.isEmpty()) {
            throw configurationFailure("Apple environment konfigurasyonu eksik");
        }
        return environments;
    }

    private Long parseAppAppleId(Environment environment) {
        if (environment != Environment.PRODUCTION) {
            return null;
        }
        if (appAppleId == null || appAppleId.isBlank()) {
            throw configurationFailure("Production icin Apple App Apple ID gerekli");
        }
        try {
            return Long.valueOf(appAppleId.trim());
        } catch (NumberFormatException ex) {
            throw configurationFailure("Apple App Apple ID gecersiz");
        }
    }

    private InputStream openCertificate(String path) {
        try {
            return new FileInputStream(path);
        } catch (IOException ex) {
            throw configurationFailure("Apple root certificate okunamadi");
        }
    }

    private void closeCertificate(InputStream certificate) {
        try {
            certificate.close();
        } catch (IOException ignored) {
            // The certificate has already been parsed into an in-memory trust anchor.
        }
    }

    private AppStoreVerificationException configurationFailure(String message) {
        return new AppStoreVerificationException(
                AppStoreVerificationException.Reason.CONFIGURATION,
                message);
    }
}
