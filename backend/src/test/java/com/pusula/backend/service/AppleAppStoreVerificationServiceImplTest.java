package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleAppStoreVerificationServiceImplTest {

    @Test
    void missingCertificatesDoNotPreventServiceConstruction() {
        AppleAppStoreVerificationServiceImpl service = new AppleAppStoreVerificationServiceImpl(
                new AppleTransactionPayloadValidator());

        assertNotNull(service);
    }

    @Test
    void invalidEnvironmentConfigurationProducesControlledConfigurationFailure() {
        AppleAppStoreVerificationServiceImpl service = new AppleAppStoreVerificationServiceImpl(
                new AppleTransactionPayloadValidator());
        ReflectionTestUtils.setField(service, "enabledEnvironments", "STAGING");

        AppStoreVerificationException ex = assertThrows(
                AppStoreVerificationException.class,
                () -> service.verifyTransaction("not-a-jws"));

        assertEquals(AppStoreVerificationException.Reason.CONFIGURATION, ex.getReason());
    }

    @Test
    void wrongConfiguredBundleProducesControlledConfigurationFailure() {
        AppleAppStoreVerificationServiceImpl service = new AppleAppStoreVerificationServiceImpl(
                new AppleTransactionPayloadValidator());
        ReflectionTestUtils.setField(service, "enabledEnvironments", "SANDBOX");
        ReflectionTestUtils.setField(service, "bundleId", "com.example.other");

        AppStoreVerificationException ex = assertThrows(
                AppStoreVerificationException.class,
                () -> service.verifyTransaction("not-a-jws"));

        assertEquals(AppStoreVerificationException.Reason.CONFIGURATION, ex.getReason());
    }
}
