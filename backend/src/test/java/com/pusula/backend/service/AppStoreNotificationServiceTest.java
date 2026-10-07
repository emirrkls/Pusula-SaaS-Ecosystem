package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppStoreNotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final LocalDateTime TODAY = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final AppleAppStoreVerificationService verifier = mock(AppleAppStoreVerificationService.class);
    private final AppStoreNotificationRepository events = mock(AppStoreNotificationRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AppStoreNotificationService service = new AppStoreNotificationService(verifier, events, companies,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Map<String, AppStoreNotification> saved = new HashMap<>();
    private Company company;

    @BeforeEach void setup() {
        company = new Company(); company.setId(7L); company.setPlanType(PlanType.USTA);
        company.setSubscriptionStatus("ACTIVE"); company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId("appstore:" + AppStoreNotificationService.hash("original"));
        when(events.lockById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(events.saveAndFlush(any())).thenAnswer(i -> {
            AppStoreNotification n = i.getArgument(0); saved.put(n.getId(), n); return n;
        });
        when(companies.findBySubscriptionProviderAndExternalSubscriptionId("APP_STORE", company.getExternalSubscriptionId()))
                .thenReturn(Optional.of(company));
        when(companies.lockById(7L)).thenReturn(Optional.of(company));
    }

    @Test void renewalUpdatesBoundCompanyAndStoresOnlyHashes() {
        var event = deliver("DID_RENEW", false, TODAY.plusDays(30), null);
        assertEquals("PROCESSED", event.getProcessingStatus());
        assertEquals(PlanType.PATRON, company.getPlanType());
        assertEquals(TODAY.plusDays(30), company.getSubscriptionExpiresAt());
        assertFalse(company.getIsReadOnly());
        assertEquals("Sandbox", company.getAppStoreEnvironment());
        assertEquals(64, event.getOriginalTransactionHash().length());
        assertNotEquals("original", event.getOriginalTransactionHash());
    }

    @Test void disablingAutoRenewDoesNotEndPaidPeriod() {
        deliver("DID_CHANGE_RENEWAL_STATUS", false, TODAY.plusDays(20), null);
        assertFalse(company.getIsReadOnly()); assertEquals("ACTIVE", company.getSubscriptionStatus());
    }

    @Test void scheduledPreferenceChangeUsesCurrentTransactionPlan() {
        deliver("DID_CHANGE_RENEWAL_PREF", false, TODAY.plusDays(20), null);
        assertEquals(PlanType.PATRON, company.getPlanType());
    }

    @Test void refundRevokesAccessWithoutErasingBusinessData() {
        deliver("REFUND", true, TODAY.plusDays(20), null);
        assertTrue(company.getIsReadOnly()); assertEquals("REVOKED", company.getSubscriptionStatus());
        assertEquals(PlanType.PATRON, company.getPlanType());
        verify(companies, never()).delete(any());
    }

    @Test void refundReversedRestoresAnUnexpiredEntitlement() {
        company.setSubscriptionStatus("REVOKED"); company.setIsReadOnly(true);
        deliver("REFUND_REVERSED", false, TODAY.plusDays(20), null);
        assertFalse(company.getIsReadOnly()); assertEquals("ACTIVE", company.getSubscriptionStatus());
    }

    @Test void expiredOrBillingRetryWithoutGraceIsReadOnly() {
        deliver("DID_FAIL_TO_RENEW", false, TODAY.minusSeconds(1), null);
        assertTrue(company.getIsReadOnly()); assertEquals("EXPIRED", company.getSubscriptionStatus());
    }

    @Test void signedBillingGraceExtendsAccessToExactGraceExpiry() {
        var payload = notification("DID_FAIL_TO_RENEW", false, TODAY.minusDays(1), TODAY.plusDays(3));
        when(verifier.verifyNotification("signed")).thenReturn(payload);
        service.receive("signed");
        assertFalse(company.getIsReadOnly());
        assertEquals(TODAY.plusDays(3), company.getSubscriptionExpiresAt());
    }

    @Test void duplicateDeliveryIsIdempotent() {
        deliver("DID_RENEW", false, TODAY.plusDays(30), null);
        service.receive("signed");
        verify(events, times(1)).saveAndFlush(any());
        verify(companies, times(1)).saveAndFlush(any());
    }

    @Test void signatureFailureHasNoPersistenceOrTenantLookup() {
        clearInvocations(events, companies);
        when(verifier.verifyNotification("fake")).thenThrow(new AppStoreVerificationException(
                AppStoreVerificationException.Reason.VERIFICATION_FAILED, "invalid"));
        assertThrows(AppStoreVerificationException.class, () -> service.receive("fake"));
        verifyNoInteractions(events, companies);
    }

    @Test void earlyNotificationWaitsForAuthenticatedBindingAndRetries() {
        when(companies.findBySubscriptionProviderAndExternalSubscriptionId(any(), any())).thenReturn(Optional.empty());
        var event = deliver("DID_RENEW", false, TODAY.plusDays(30), null);
        assertEquals("WAITING_OWNER", event.getProcessingStatus());
        verify(companies, never()).saveAndFlush(any());
        when(companies.findBySubscriptionProviderAndExternalSubscriptionId(any(), any())).thenReturn(Optional.of(company));
        service.retry(event.getId());
        assertEquals("PROCESSED", event.getProcessingStatus());
    }

    @Test void sandboxCannotOverwriteProductionBinding() {
        company.setAppStoreEnvironment("Production");
        assertEquals("STALE", deliver("DID_RENEW", false, TODAY.plusDays(30), null).getProcessingStatus());
        verify(companies, never()).saveAndFlush(any());
    }

    @Test void newerPaidTransactionIsNotRevokedByOldRefund() {
        company.setAppStorePurchaseDate(TODAY.plusDays(1)); company.setPlanType(PlanType.USTA);
        assertEquals("STALE", deliver("REFUND", true, TODAY.plusDays(30), null).getProcessingStatus());
        assertFalse(company.getIsReadOnly()); assertEquals(PlanType.USTA, company.getPlanType());
    }

    @Test void olderSignedSnapshotCannotReverseNewerState() {
        company.setAppStorePurchaseDate(TODAY); company.setAppStoreSignedDate(NOW.toEpochMilli() + 1);
        assertEquals("STALE", deliver("DID_RENEW", false, TODAY.plusDays(30), null).getProcessingStatus());
    }

    @Test void administrativeSuspensionIsPreserved() {
        company.setSubscriptionStatus("SUSPENDED"); company.setIsReadOnly(true);
        deliver("DID_RENEW", false, TODAY.plusDays(30), null);
        assertEquals("SUSPENDED", company.getSubscriptionStatus()); assertTrue(company.getIsReadOnly());
    }

    @Test void providerChangeDuringOwnerLockIsNotOverwritten() {
        company.setSubscriptionProvider("IYZICO");
        assertEquals("STALE", deliver("DID_RENEW", false, TODAY.plusDays(30), null).getProcessingStatus());
        assertEquals("IYZICO", company.getSubscriptionProvider());
    }

    @Test void testAndUnknownEventsAreAcknowledgedWithoutAnAccount() {
        when(verifier.verifyNotification("signed")).thenReturn(new AppleAppStoreVerificationService.AppleNotificationResult(
                UUID.randomUUID().toString(), "TEST", null, "Sandbox", NOW.toEpochMilli(), null, false, null, null));
        service.receive("signed");
        assertEquals("IGNORED", saved.values().iterator().next().getProcessingStatus());
        verify(companies, never()).saveAndFlush(any());
    }

    private AppStoreNotification deliver(String type, boolean revoked, LocalDateTime expiresAt, LocalDateTime grace) {
        when(verifier.verifyNotification("signed")).thenReturn(notification(type, revoked, expiresAt, grace));
        service.receive("signed"); return saved.values().iterator().next();
    }
    private AppleAppStoreVerificationService.AppleNotificationResult notification(
            String type, boolean revoked, LocalDateTime expiresAt, LocalDateTime grace) {
        var tx = new AppleAppStoreVerificationService.AppleVerificationResult("transaction", "original",
                "com.pusula.patron", PlanType.PATRON, "com.pusula.service", "Sandbox", TODAY, expiresAt);
        return new AppleAppStoreVerificationService.AppleNotificationResult(UUID.randomUUID().toString(), type,
                grace == null ? null : "GRACE_PERIOD", "Sandbox", NOW.toEpochMilli(), tx, revoked,
                grace == null ? null : "BILLING_GRACE_PERIOD", grace);
    }
}
