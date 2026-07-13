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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceGoogleVerifyIdempotencyTest {

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
    void verifyGooglePurchase_replayTokenDoesNotProcessTwice() {
        PaymentEvent existing = new PaymentEvent();
        existing.setId(99L);
        existing.setCompanyId(10L);
        existing.setStatus(PaymentEventStatus.PROCESSED);
        existing.setExternalSubscriptionId("order-123");

        Company company = company(10L, PlanType.USTA);

        when(paymentEventRepository.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), any()))
                .thenReturn(Optional.of(existing));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));

        SubscriptionService.GoogleVerifyResult result = subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                10L,
                "same-token",
                "usta");

        assertTrue(result.idempotentReplay());
        assertTrue(result.verified());
        assertEquals("processed", result.status());
        assertEquals("order-123", result.subscriptionId());

        verify(googlePlayVerificationService, never()).verifySubscription(any(), any());
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void verifyGooglePurchase_verifiedUstaProductUpdatesGoogleProviderOnly() {
        Company company = company(10L, PlanType.PATRON);
        company.setIyzicoSubscriptionId("legacy-iyzico-id");
        stubSuccessfulGoogleVerification("usta", "order-usta", company);

        SubscriptionService.GoogleVerifyResult result = subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                10L, "purchase-token", "usta");

        assertTrue(result.verified());
        assertEquals("USTA", result.plan());
        assertEquals(PlanType.USTA, company.getPlanType());
        assertEquals("GOOGLE_PLAY", company.getSubscriptionProvider());
        assertEquals("google:order-usta", company.getExternalSubscriptionId());
        assertNull(company.getIyzicoSubscriptionId());
        assertEquals("ACTIVE", company.getSubscriptionStatus());
        assertFalse(company.getIsReadOnly());
    }

    @Test
    void verifyGooglePurchase_verifiedPatronProductUpdatesPlanToPatron() {
        Company company = company(10L, PlanType.USTA);
        stubSuccessfulGoogleVerification("patron", "order-patron", company);

        SubscriptionService.GoogleVerifyResult result = subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                10L, "purchase-token", "patron");

        assertEquals("PATRON", result.plan());
        assertEquals(PlanType.PATRON, company.getPlanType());
        assertEquals("GOOGLE_PLAY", company.getSubscriptionProvider());
    }

    @Test
    void verifyGooglePurchase_rejectsUnknownVerifiedProduct() {
        when(paymentEventRepository.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), any()))
                .thenReturn(Optional.empty());
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(googlePlayVerificationService.verifySubscription("purchase-token", "usta"))
                .thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                        true, "order-unknown", "unknown", null));

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.verifyGooglePurchaseAndUpgradePlan(10L, "purchase-token", "usta"));

        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void verifyGooglePurchase_rejectsUnknownRequestedProductWithoutCallingGoogle() {
        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                        10L, "purchase-token", "cirak"));

        verify(googlePlayVerificationService, never()).verifySubscription(any(), any());
        verify(paymentEventRepository, never()).save(any(PaymentEvent.class));
    }

    @Test
    void iyzicoUpgradeStillUsesIyzicoProviderAndIdentifier() {
        Company company = company(10L, PlanType.CIRAK);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.upgradePlan(10L, PlanType.USTA, "iyzico-subscription-1");

        assertEquals("IYZICO", company.getSubscriptionProvider());
        assertEquals("iyzico-subscription-1", company.getIyzicoSubscriptionId());
        assertEquals("iyzico-subscription-1", company.getExternalSubscriptionId());
    }

    private void stubSuccessfulGoogleVerification(String productId, String orderId, Company company) {
        when(paymentEventRepository.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), any()))
                .thenReturn(Optional.empty());
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(googlePlayVerificationService.verifySubscription("purchase-token", productId))
                .thenReturn(new GooglePlayVerificationService.GoogleVerificationResult(
                        true, orderId, productId, null));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Company company(Long id, PlanType planType) {
        Company company = new Company();
        company.setId(id);
        company.setName("Pusula");
        company.setPlanType(planType);
        company.setSubscriptionStatus("ACTIVE");
        company.setIsReadOnly(true);
        return company;
    }
}
