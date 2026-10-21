package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePlayEntitlementRegressionTest {
    @Mock CompanyRepository companies;
    @Mock PaymentEventRepository events;
    @Mock GooglePlayVerificationService google;
    @Mock AppleAppStoreVerificationService apple;
    @Mock AuditLogService audit;
    SubscriptionService service;
    Company company;
    PaymentEvent receipt;
    final Instant expiry = Instant.now().plusSeconds(365L * 86400);
    @BeforeEach void setup() {
        service = new SubscriptionService(companies, events, google, apple, audit);
        company = new Company(); company.setId(10L); company.setPlanType(PlanType.USTA);
        company.setIsReadOnly(false); company.setSubscriptionProvider("GOOGLE_PLAY");
        company.setSubscriptionStatus("ACTIVE"); company.setExternalSubscriptionId("google:stable-receipt");
        receipt = new PaymentEvent(); receipt.setId(1L); receipt.setCompanyId(10L);
        receipt.setStatus(PaymentEventStatus.PROCESSED); receipt.setExternalSubscriptionId("stable-receipt");
    }
    void storedReceipt() {
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), anyString())).thenReturn(Optional.of(receipt));
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
    }
    void validGoogle() {
        when(google.verifySubscription("receipt", "usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true, "stable-receipt", "usta", null, expiry, "SUBSCRIPTION_STATE_ACTIVE", false,
                GooglePlayAccountBinding.companyId(10L)));
    }
    void successfulSave() {
        when(companies.findById(10L)).thenReturn(Optional.of(company));
        when(companies.save(company)).thenReturn(company);
    }
    @Test void failedReceiptCanBeRetriedAndGrantedWithActualAnnualExpiry() {
        receipt.setStatus(PaymentEventStatus.FAILED);
        storedReceipt(); validGoogle(); successfulSave();
        assertTrue(service.verifyGooglePurchaseAndUpgradePlan(10L, "receipt", "usta").verified());
        assertEquals(PaymentEventStatus.PROCESSED, receipt.getStatus());
        assertEquals(LocalDateTime.ofInstant(expiry, ZoneOffset.UTC), company.getSubscriptionExpiresAt());
        verify(events, never()).saveAndFlush(any());
    }
    @Test void replayRechecksRenewalWithoutAddingAnotherThirtyDays() {
        storedReceipt(); validGoogle(); successfulSave();
        var result = service.verifyGooglePurchaseAndUpgradePlan(10L, "receipt", "usta");
        assertTrue(result.verified()); assertTrue(result.idempotentReplay());
        assertEquals(LocalDateTime.ofInstant(expiry, ZoneOffset.UTC), company.getSubscriptionExpiresAt());
        verify(google).verifySubscription("receipt", "usta");
    }
    @Test void networkOutageDoesNotRevokeAnExistingPaidEntitlement() {
        storedReceipt();
        when(google.verifySubscription("receipt", "usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                false, null, null, "PLAY_HTTP_503", null, null, true, null));
        var result = service.verifyGooglePurchaseAndUpgradePlan(10L, "receipt", "usta");
        assertFalse(result.verified()); assertEquals("retryable", result.status());
        assertFalse(company.getIsReadOnly()); assertEquals(PaymentEventStatus.PROCESSED, receipt.getStatus());
        verify(companies, never()).save(any());
    }
    @Test void authoritativeExpiryRevokesOnlyTheCurrentGoogleReceipt() {
        storedReceipt();
        Instant past = Instant.now().minusSeconds(1);
        when(google.verifySubscription("receipt", "usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                false, "stable-receipt", "usta", "SUBSCRIPTION_NOT_ENTITLED", past,
                "SUBSCRIPTION_STATE_EXPIRED", false, GooglePlayAccountBinding.companyId(10L)));
        assertFalse(service.verifyGooglePurchaseAndUpgradePlan(10L, "receipt", "usta").verified());
        assertTrue(company.getIsReadOnly()); assertEquals("EXPIRED", company.getSubscriptionStatus());
    }
    @Test void oldGoogleRestoreNeverOverwritesCurrentApplePlan() {
        company.setSubscriptionProvider("APP_STORE"); company.setExternalSubscriptionId("appstore:new-apple");
        storedReceipt(); validGoogle();
        assertEquals("another_provider_active", service.verifyGooglePurchaseAndUpgradePlan(10L, "receipt", "usta").status());
        assertEquals("APP_STORE", company.getSubscriptionProvider());
        verify(companies, never()).save(any());
    }
    @Test void aNewReceiptWithTheWrongTenantBindingIsRejected() {
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), anyString())).thenReturn(Optional.empty());
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", "usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true, "stable-receipt", "usta", null, expiry, "SUBSCRIPTION_STATE_ACTIVE", false,
                GooglePlayAccountBinding.companyId(11L)));
        assertThrows(PaymentOwnershipException.class, () -> service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta"));
        verify(companies, never()).save(any());
    }
    @Test void aNewUnboundReceiptCannotBeClaimedByAnArbitraryTenant() {
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), anyString())).thenReturn(Optional.empty());
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", "usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true, "stable-receipt", "usta", null, expiry, "SUBSCRIPTION_STATE_ACTIVE", false, null));
        assertThrows(PaymentOwnershipException.class, () -> service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta"));
    }
    @Test void googleExpiryUsesUtcWithoutAnExtraLocalGracePeriod() {
        company.setSubscriptionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(companies.findAll()).thenReturn(List.of(company));
        service.enforceExpiredSubscriptions();
        assertTrue(company.getIsReadOnly());
        assertTrue(CompanyAccessPolicy.isReadOnly(company));
    }
    void notificationReceipt() {
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), anyString())).thenReturn(Optional.of(receipt));
    }
    GooglePlayVerificationService.GoogleVerificationResult state(boolean valid, String state, boolean retryable) {
        return new GooglePlayVerificationService.GoogleVerificationResult(valid, "stable-receipt", "usta",
                valid ? null : "SUBSCRIPTION_NOT_ENTITLED", expiry, state, retryable, GooglePlayAccountBinding.companyId(10L));
    }
    @Test void notificationCannotClaimAnUnownedPurchase() {
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), anyString())).thenReturn(Optional.empty());
        when(google.verifySubscription("receipt",null)).thenReturn(state(true,"SUBSCRIPTION_STATE_ACTIVE",false));
        assertEquals("WAITING_OWNER", service.reconcileGoogleNotification("receipt"));
        verifyNoInteractions(companies);
    }
    @Test void notificationCannotOverwriteAnotherProvider() {
        notificationReceipt(); company.setSubscriptionProvider("APP_STORE");
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
        assertEquals("IGNORED_STALE", service.reconcileGoogleNotification("receipt")); verifyNoInteractions(google);
    }
    @Test void notificationCannotOverwriteANewerGoogleReceipt() {
        notificationReceipt(); company.setExternalSubscriptionId("google:newer-receipt");
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
        assertEquals("IGNORED_STALE", service.reconcileGoogleNotification("receipt")); verifyNoInteractions(google);
    }
    @Test void renewalAndCanceledPaidPeriodKeepActualEntitlement() {
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company)); successfulSave();
        when(google.verifySubscription("receipt", null)).thenReturn(state(true, "SUBSCRIPTION_STATE_CANCELED", false));
        assertEquals("APPLIED", service.reconcileGoogleNotification("receipt"));
        assertFalse(company.getIsReadOnly()); assertEquals("ACTIVE", company.getSubscriptionStatus());
        assertEquals(LocalDateTime.ofInstant(expiry, ZoneOffset.UTC), company.getSubscriptionExpiresAt());
    }
    @Test void holdMakesCurrentReceiptReadOnlyEvenWithFutureExpiry() {
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", null)).thenReturn(state(false, "SUBSCRIPTION_STATE_ON_HOLD", false));
        assertEquals("APPLIED", service.reconcileGoogleNotification("receipt"));
        assertTrue(company.getIsReadOnly()); assertEquals("EXPIRED", company.getSubscriptionStatus());
    }
    @Test void transientNotificationVerificationNeverRevokesAccess() {
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", null)).thenReturn(state(false, null, true));
        assertEquals("RETRY", service.reconcileGoogleNotification("receipt"));
        assertFalse(company.getIsReadOnly()); verify(companies, never()).save(any());
    }
    @Test void notificationWithMismatchedTenantIsRejected() {
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", null)).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true,"stable-receipt","usta",null,expiry,"SUBSCRIPTION_STATE_ACTIVE",false,GooglePlayAccountBinding.companyId(11L)));
        assertEquals("REJECTED_OWNER", service.reconcileGoogleNotification("receipt")); verify(companies, never()).save(any());
    }
    @Test void renewalPreservesManualSuspension() {
        company.setSubscriptionStatus("SUSPENDED"); company.setIsReadOnly(true);
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company)); successfulSave();
        when(google.verifySubscription("receipt", null)).thenReturn(state(true,"SUBSCRIPTION_STATE_ACTIVE",false));
        assertEquals("APPLIED", service.reconcileGoogleNotification("receipt"));
        assertTrue(company.getIsReadOnly()); assertEquals("SUSPENDED", company.getSubscriptionStatus());
    }
    @Test void expiryNotificationDoesNotEraseManualSuspension() {
        company.setSubscriptionStatus("SUSPENDED"); company.setIsReadOnly(true);
        notificationReceipt(); when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt", null)).thenReturn(state(false,"SUBSCRIPTION_STATE_EXPIRED",false));
        service.reconcileGoogleNotification("receipt"); assertEquals("SUSPENDED", company.getSubscriptionStatus());
    }
    @Test void deviceReplayAlsoPreservesManualSuspension() {
        company.setSubscriptionStatus("SUSPENDED"); company.setIsReadOnly(true);
        storedReceipt(); validGoogle(); successfulSave();
        assertTrue(service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta").verified());
        assertTrue(company.getIsReadOnly()); assertEquals("SUSPENDED", company.getSubscriptionStatus());
    }
    @Test void oldProcessedReceiptCannotDisplaceNewerGooglePurchaseOnRestore() {
        company.setExternalSubscriptionId("google:newer-receipt"); storedReceipt(); validGoogle();
        assertEquals("another_google_receipt_active", service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta").status());
        verify(companies, never()).save(any());
    }
    @Test void failedUnboundReceiptCannotBecomeAnOwnershipClaim() {
        receipt.setStatus(PaymentEventStatus.FAILED); storedReceipt();
        when(google.verifySubscription("receipt","usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true,"stable-receipt","usta",null,expiry,"SUBSCRIPTION_STATE_ACTIVE",false,null));
        assertThrows(PaymentOwnershipException.class,()->service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta"));
        verify(companies,never()).save(any());
    }
    @Test void aRetiredReceiptCannotBeRestoredEvenIfGoogleStillReportsItPaid() {
        receipt.setFailureReason("GOOGLE_REPLACED");
        when(events.findByProviderAndTokenHash(eq("GOOGLE_PLAY"),anyString())).thenReturn(Optional.of(receipt));
        assertEquals("receipt_replaced",service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta").status());
        verifyNoInteractions(google,companies);
    }
    @Test void linkedReplacementCannotStealAnotherTenantsPurchase() {
        String linked="a".repeat(64); PaymentEvent foreign=new PaymentEvent();
        foreign.setCompanyId(11L); foreign.setStatus(PaymentEventStatus.PROCESSED);
        when(events.findByProviderAndTokenHash("GOOGLE_PLAY",GooglePlayAccountBinding.sha256("receipt"))).thenReturn(Optional.empty());
        when(events.findByProviderAndTokenHash("GOOGLE_PLAY",linked)).thenReturn(Optional.of(foreign));
        when(companies.lockById(10L)).thenReturn(Optional.of(company));
        when(google.verifySubscription("receipt","usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true,"new-receipt","usta",null,expiry,"SUBSCRIPTION_STATE_ACTIVE",false,
                GooglePlayAccountBinding.companyId(10L),linked,"usta-monthly",null,"ACKNOWLEDGEMENT_STATE_PENDING"));
        assertThrows(PaymentOwnershipException.class,()->service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta"));
        verify(companies,never()).save(any());
    }
    @Test void verifiedDeferredReplacementKeepsPaidPlanAndRetiresOldReceipt() {
        String linked="a".repeat(64); receipt.setTokenHash(linked);
        company.setPlanType(PlanType.PATRON); company.setExternalSubscriptionId("google:"+linked);
        when(events.findByProviderAndTokenHash("GOOGLE_PLAY",GooglePlayAccountBinding.sha256("receipt"))).thenReturn(Optional.empty());
        when(events.findByProviderAndTokenHash("GOOGLE_PLAY",linked)).thenReturn(Optional.of(receipt));
        when(companies.lockById(10L)).thenReturn(Optional.of(company)); successfulSave();
        when(google.verifySubscription("receipt","usta")).thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                true,"new-receipt","patron",null,expiry,"SUBSCRIPTION_STATE_ACTIVE",false,null,
                linked,"patron-monthly","usta","ACKNOWLEDGEMENT_STATE_PENDING"));
        var result=service.verifyGooglePurchaseAndUpgradePlan(10L,"receipt","usta");
        assertTrue(result.verified()); assertEquals("PATRON",result.plan()); assertEquals("USTA",result.scheduledPlan());
        assertEquals(PlanType.PATRON,company.getPlanType()); assertEquals("GOOGLE_REPLACED",receipt.getFailureReason());
        assertEquals(PaymentEventStatus.PROCESSED,receipt.getStatus()); assertEquals("google:new-receipt",company.getExternalSubscriptionId());
    }
}
