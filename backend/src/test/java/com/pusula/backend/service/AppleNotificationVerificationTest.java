package com.pusula.backend.service;

import com.apple.itunes.storekit.model.*;
import com.apple.itunes.storekit.verification.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppleNotificationVerificationTest {
    private final SignedDataVerifier sdk = mock(SignedDataVerifier.class);
    private final AppleAppStoreVerificationServiceImpl service =
            new AppleAppStoreVerificationServiceImpl(new AppleTransactionPayloadValidator());

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "enabledEnvironments", "SANDBOX");
        ReflectionTestUtils.setField(service, "verifierCache", new ConcurrentHashMap<>(Map.of(Environment.SANDBOX, sdk)));
    }

    @Test void verifiesOuterTransactionAndRenewalWithSameTrustedVerifier() throws Exception {
        var payload = payload();
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload);
        when(sdk.verifyAndDecodeTransaction("transaction-jws")).thenReturn(transaction());
        when(sdk.verifyAndDecodeRenewalInfo("renewal-jws")).thenReturn(
                new JWSRenewalInfoDecodedPayload().originalTransactionId("original").environment(Environment.SANDBOX)
                        .autoRenewProductId("com.pusula.usta").gracePeriodExpiresDate(2000000000000L));
        var result = service.verifyNotification("outer");
        assertEquals("DID_RENEW", result.type()); assertEquals("Sandbox", result.environment());
        assertEquals("com.pusula.patron", result.transaction().productId()); // not next renewal's product
        assertNotNull(result.graceExpiresDate());
        verify(sdk).verifyAndDecodeNotification("outer");
        verify(sdk).verifyAndDecodeTransaction("transaction-jws");
        verify(sdk).verifyAndDecodeRenewalInfo("renewal-jws");
    }

    @Test void expiredRevokedNotificationIsAcceptedButPurchaseIsRejected() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload());
        when(sdk.verifyAndDecodeTransaction("transaction-jws")).thenReturn(transaction().revocationDate(100L));
        when(sdk.verifyAndDecodeRenewalInfo("renewal-jws")).thenReturn(
                new JWSRenewalInfoDecodedPayload().originalTransactionId("original"));
        assertTrue(service.verifyNotification("outer").revoked());
        var failure = assertThrows(AppStoreVerificationException.class, () -> service.verifyTransaction("transaction-jws"));
        assertEquals(AppStoreVerificationException.Reason.REVOKED, failure.getReason());
    }

    @Test void wrongNestedBundleCannotChangeEntitlement() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload());
        when(sdk.verifyAndDecodeTransaction("transaction-jws")).thenReturn(transaction().bundleId("com.other.app"));
        var failure = assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("outer"));
        assertEquals(AppStoreVerificationException.Reason.BUNDLE_MISMATCH, failure.getReason());
        verify(sdk, never()).verifyAndDecodeRenewalInfo(any());
    }

    @Test void mismatchedRenewalSubscriptionIsRejected() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload());
        when(sdk.verifyAndDecodeTransaction("transaction-jws")).thenReturn(transaction());
        when(sdk.verifyAndDecodeRenewalInfo("renewal-jws")).thenReturn(
                new JWSRenewalInfoDecodedPayload().originalTransactionId("different"));
        assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("outer"));
    }

    @Test void testNotificationStillRequiresOuterSignature() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload().notificationType(NotificationTypeV2.TEST));
        var result = service.verifyNotification("outer");
        assertEquals("TEST", result.type()); assertNull(result.transaction());
        verify(sdk).verifyAndDecodeNotification("outer");
        verify(sdk, never()).verifyAndDecodeTransaction(any());
    }

    @Test void malformedUuidOrVersionIsRejected() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload().notificationUUID("invalid"));
        assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("outer"));
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload().version("1.0"));
        assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("outer"));
    }

    @Test void invalidOuterSignatureStopsAllNestedProcessing() throws Exception {
        when(sdk.verifyAndDecodeNotification("fake")).thenThrow(
                new VerificationException(VerificationStatus.VERIFICATION_FAILURE));
        var failure = assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("fake"));
        assertEquals(AppStoreVerificationException.Reason.VERIFICATION_FAILED, failure.getReason());
        verify(sdk, never()).verifyAndDecodeTransaction(any());
    }

    @Test void invalidNestedSignatureIsNotAcceptedDespiteValidOuterSignature() throws Exception {
        when(sdk.verifyAndDecodeNotification("outer")).thenReturn(payload());
        when(sdk.verifyAndDecodeTransaction("transaction-jws")).thenThrow(
                new VerificationException(VerificationStatus.VERIFICATION_FAILURE));
        assertThrows(AppStoreVerificationException.class, () -> service.verifyNotification("outer"));
        verify(sdk, never()).verifyAndDecodeRenewalInfo(any());
    }

    private ResponseBodyV2DecodedPayload payload() {
        return new ResponseBodyV2DecodedPayload().notificationType(NotificationTypeV2.DID_RENEW)
                .notificationUUID(UUID.randomUUID().toString()).version("2.0").signedDate(1900000000000L)
                .data(new Data().bundleId("com.pusula.service").environment(Environment.SANDBOX)
                        .signedTransactionInfo("transaction-jws").signedRenewalInfo("renewal-jws"));
    }
    private JWSTransactionDecodedPayload transaction() {
        return new JWSTransactionDecodedPayload().bundleId("com.pusula.service").environment(Environment.SANDBOX)
                .type(Type.AUTO_RENEWABLE_SUBSCRIPTION).transactionId("tx").originalTransactionId("original")
                .productId("com.pusula.patron").purchaseDate(100L).expiresDate(200L);
    }
}
