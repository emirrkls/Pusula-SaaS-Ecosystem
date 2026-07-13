package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PaymentEvent;
import com.pusula.backend.entity.PaymentEventStatus;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.PaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceAppleVerifyTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private GooglePlayVerificationService googlePlayVerificationService;
    @Mock
    private AppleAppStoreVerificationService appleAppStoreVerificationService;
    @Mock
    private AuditLogService auditLogService;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                companyRepository,
                paymentEventRepository,
                googlePlayVerificationService,
                appleAppStoreVerificationService,
                auditLogService);
    }

    @Test
    void verifyAppleTransaction_validUstaTransactionUpgradesToUsta() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(20);
        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-1", "orig-1", expiresAt);
        stubNoExistingEvent();
        Company company = company(10L, PlanType.CIRAK);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionService.AppleVerifyResult result = subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed");

        assertTrue(result.verified());
        assertFalse(result.idempotentReplay());
        assertEquals("USTA", result.plan());
        assertEquals(PlanType.USTA, company.getPlanType());
        assertEquals("ACTIVE", company.getSubscriptionStatus());
        assertFalse(company.getIsReadOnly());
        assertEquals("APP_STORE", company.getSubscriptionProvider());
        assertTrue(company.getExternalSubscriptionId().startsWith("appstore:"));
        assertFalse(company.getExternalSubscriptionId().contains("orig-1"));
        assertEquals(expiresAt, company.getSubscriptionExpiresAt());
    }

    @Test
    void verifyAppleTransaction_validPatronTransactionUpgradesToPatron() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(20);
        stubAppleVerification("com.pusula.patron", PlanType.PATRON, "tx-2", "orig-2", expiresAt);
        stubNoExistingEvent();
        Company company = company(10L, PlanType.USTA);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionService.AppleVerifyResult result = subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed");

        assertEquals("PATRON", result.plan());
        assertEquals(PlanType.PATRON, company.getPlanType());
    }

    @Test
    void verifyAppleTransaction_usesVerifiedProductWhenClientPlanDisagrees() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(20);
        stubAppleVerification("com.pusula.patron", PlanType.PATRON, "tx-3", "orig-3", expiresAt);
        stubNoExistingEvent();
        Company company = company(10L, PlanType.CIRAK);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionService.AppleVerifyResult result = subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed");

        assertEquals("PATRON", result.plan());
        assertEquals(PlanType.PATRON, company.getPlanType());
    }

    @Test
    void verifyAppleTransaction_rejectsUnknownProductAndDoesNotChangeCompany() {
        when(appleAppStoreVerificationService.verifyTransaction("signed")).thenThrow(
                new AppStoreVerificationException(
                        AppStoreVerificationException.Reason.PRODUCT_NOT_ALLOWED,
                        "Apple productId tanimli degil"));

        assertThrows(AppStoreVerificationException.class,
                () -> subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed"));

        verify(companyRepository, never()).saveAndFlush(any(Company.class));
        verify(paymentEventRepository, never()).saveAndFlush(any(PaymentEvent.class));
    }

    @Test
    void verifyAppleTransaction_rejectsBundleMismatch() {
        assertVerificationFailure(AppStoreVerificationException.Reason.BUNDLE_MISMATCH);
    }

    @Test
    void verifyAppleTransaction_rejectsFakeJws() {
        assertVerificationFailure(AppStoreVerificationException.Reason.VERIFICATION_FAILED);
    }

    @Test
    void verifyAppleTransaction_rejectsExpiredTransaction() {
        assertVerificationFailure(AppStoreVerificationException.Reason.EXPIRED);
    }

    @Test
    void verifyAppleTransaction_rejectsRevokedTransaction() {
        assertVerificationFailure(AppStoreVerificationException.Reason.REVOKED);
    }

    @Test
    void verifyAppleTransaction_replayForSameCompanyIsIdempotent() {
        PaymentEvent existing = new PaymentEvent();
        existing.setId(99L);
        existing.setCompanyId(10L);
        existing.setStatus(PaymentEventStatus.PROCESSED);
        existing.setExternalSubscriptionId("orig-1");

        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-1", "orig-1", LocalDateTime.now().plusDays(20));
        when(paymentEventRepository.findByProviderAndTokenHash(eq("APP_STORE"), any()))
                .thenReturn(Optional.of(existing));

        SubscriptionService.AppleVerifyResult result = subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed");

        assertTrue(result.verified());
        assertTrue(result.idempotentReplay());
        assertEquals("processed", result.status());
        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    @Test
    void verifyAppleTransaction_replayForDifferentCompanyIsRejected() {
        PaymentEvent existing = new PaymentEvent();
        existing.setId(99L);
        existing.setCompanyId(20L);
        existing.setStatus(PaymentEventStatus.PROCESSED);

        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-1", "orig-1", LocalDateTime.now().plusDays(20));
        when(paymentEventRepository.findByProviderAndTokenHash(eq("APP_STORE"), any()))
                .thenReturn(Optional.of(existing));

        AppStoreVerificationException ex = assertThrows(AppStoreVerificationException.class,
                () -> subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed"));

        assertEquals(AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT, ex.getReason());
        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    @Test
    void verifyAppleTransaction_newTransactionForSameOriginalRenewsSubscription() {
        LocalDateTime oldExpiry = LocalDateTime.now().plusDays(2);
        LocalDateTime renewedExpiry = LocalDateTime.now().plusDays(32);
        Company company = company(10L, PlanType.USTA);
        company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId("appstore:" + sha256("orig-1"));
        company.setSubscriptionExpiresAt(oldExpiry);

        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-renewal", "orig-1", renewedExpiry);
        stubNoExistingEvent();
        when(companyRepository.findBySubscriptionProviderAndExternalSubscriptionId(
                "APP_STORE", "appstore:" + sha256("orig-1"))).thenReturn(Optional.of(company));
        stubSuccessfulPersistence(company);

        SubscriptionService.AppleVerifyResult result =
                subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed-renewal");

        assertTrue(result.verified());
        assertFalse(result.idempotentReplay());
        assertEquals(renewedExpiry, company.getSubscriptionExpiresAt());
    }

    @Test
    void verifyAppleTransaction_newTransactionForSameOriginalUpgradesToPatron() {
        Company company = company(10L, PlanType.USTA);
        company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId("appstore:" + sha256("orig-1"));
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        stubAppleVerification("com.pusula.patron", PlanType.PATRON, "tx-upgrade", "orig-1", expiresAt);
        stubNoExistingEvent();
        when(companyRepository.findBySubscriptionProviderAndExternalSubscriptionId(
                "APP_STORE", "appstore:" + sha256("orig-1"))).thenReturn(Optional.of(company));
        stubSuccessfulPersistence(company);

        SubscriptionService.AppleVerifyResult result =
                subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed-upgrade");

        assertEquals("PATRON", result.plan());
        assertEquals(PlanType.PATRON, company.getPlanType());
        assertEquals(expiresAt, company.getSubscriptionExpiresAt());
    }

    @Test
    void verifyAppleTransaction_sameOriginalOwnedByDifferentCompanyIsRejected() {
        Company owner = company(20L, PlanType.USTA);
        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-new", "orig-shared",
                LocalDateTime.now().plusDays(20));
        stubNoExistingEvent();
        when(companyRepository.findBySubscriptionProviderAndExternalSubscriptionId(
                "APP_STORE", "appstore:" + sha256("orig-shared"))).thenReturn(Optional.of(owner));

        AppStoreVerificationException ex = assertThrows(AppStoreVerificationException.class,
                () -> subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed"));

        assertEquals(AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT, ex.getReason());
        verify(paymentEventRepository, never()).saveAndFlush(any(PaymentEvent.class));
        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    @Test
    void verifyAppleTransaction_uniqueConstraintRaceReturnsOwnershipConflict() {
        Company company = company(10L, PlanType.CIRAK);
        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-race", "orig-race",
                LocalDateTime.now().plusDays(20));
        stubNoExistingEvent();
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenThrow(new DataIntegrityViolationException("ownership unique constraint"));

        AppStoreVerificationException ex = assertThrows(AppStoreVerificationException.class,
                () -> subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed"));

        assertEquals(AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT, ex.getReason());
    }

    @Test
    void verifyAppleTransaction_twoTransactionsForSameOriginalCreateTwoPaymentEvents() {
        Company company = company(10L, PlanType.USTA);
        String originalExternalId = "appstore:" + sha256("orig-1");
        company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId(originalExternalId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        when(appleAppStoreVerificationService.verifyTransaction(any()))
                .thenReturn(verification("com.pusula.usta", PlanType.USTA, "tx-1", "orig-1", expiresAt))
                .thenReturn(verification("com.pusula.usta", PlanType.USTA, "tx-2", "orig-1", expiresAt.plusDays(30)));
        stubNoExistingEvent();
        when(companyRepository.findBySubscriptionProviderAndExternalSubscriptionId("APP_STORE", originalExternalId))
                .thenReturn(Optional.of(company));
        stubSuccessfulPersistence(company);
        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);

        subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed-1");
        subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed-2");

        verify(paymentEventRepository, times(2)).saveAndFlush(eventCaptor.capture());
        List<PaymentEvent> events = eventCaptor.getAllValues();
        assertEquals(2, events.size());
        assertEquals(2, events.stream().map(PaymentEvent::getTokenHash).distinct().count());
        assertEquals(sha256("tx-1"), events.get(0).getTokenHash());
        assertEquals(sha256("tx-2"), events.get(1).getTokenHash());
    }

    @Test
    void verifyAppleTransaction_savesHashedOriginalTransactionIdNotJws() {
        stubAppleVerification("com.pusula.usta", PlanType.USTA, "tx-1", "orig-1", LocalDateTime.now().plusDays(20));
        stubNoExistingEvent();
        Company company = company(10L, PlanType.CIRAK);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);

        subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed-jws-payload");

        verify(paymentEventRepository).saveAndFlush(eventCaptor.capture());
        PaymentEvent saved = eventCaptor.getAllValues().get(0);
        assertEquals("APP_STORE", saved.getProvider());
        assertEquals(sha256("tx-1"), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertFalse(saved.getTokenHash().contains("tx-1"));
        assertFalse(saved.getTokenHash().contains("orig-1"));
        assertFalse(saved.getPurchaseTokenMasked().contains("tx-1"));
        assertFalse(saved.getPurchaseTokenMasked().contains("orig-1"));
        assertFalse(saved.getExternalSubscriptionId().contains("orig-1"));
        assertFalse(saved.getPurchaseTokenMasked().contains("signed-jws-payload"));
    }

    private void assertVerificationFailure(AppStoreVerificationException.Reason reason) {
        when(appleAppStoreVerificationService.verifyTransaction("signed")).thenThrow(
                new AppStoreVerificationException(reason, "Apple dogrulama basarisiz"));

        AppStoreVerificationException ex = assertThrows(AppStoreVerificationException.class,
                () -> subscriptionService.verifyAppleTransactionAndUpgradePlan(10L, "signed"));

        assertEquals(reason, ex.getReason());
        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    private void stubAppleVerification(
            String productId,
            PlanType planType,
            String transactionId,
            String originalTransactionId,
            LocalDateTime expiresAt) {
        when(appleAppStoreVerificationService.verifyTransaction(any())).thenReturn(
                verification(productId, planType, transactionId, originalTransactionId, expiresAt));
    }

    private AppleAppStoreVerificationService.AppleVerificationResult verification(
            String productId,
            PlanType planType,
            String transactionId,
            String originalTransactionId,
            LocalDateTime expiresAt) {
        return new AppleAppStoreVerificationService.AppleVerificationResult(
                transactionId,
                originalTransactionId,
                productId,
                planType,
                "com.pusula.service",
                "Sandbox",
                LocalDateTime.now().minusMinutes(1),
                expiresAt);
    }

    private void stubNoExistingEvent() {
        when(paymentEventRepository.findByProviderAndTokenHash(eq("APP_STORE"), any()))
                .thenReturn(Optional.empty());
    }

    private void stubSuccessfulPersistence(Company company) {
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Company company(Long id, PlanType planType) {
        Company company = new Company();
        company.setId(id);
        company.setName("Pusula");
        company.setSubscriptionStatus("ACTIVE");
        company.setPlanType(planType);
        company.setIsReadOnly(true);
        return company;
    }
}
