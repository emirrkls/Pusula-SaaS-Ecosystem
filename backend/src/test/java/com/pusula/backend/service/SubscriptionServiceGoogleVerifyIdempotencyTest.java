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
    private AuditLogService auditLogService;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                companyRepository,
                paymentEventRepository,
                googlePlayVerificationService,
                auditLogService);
    }

    @Test
    void verifyGooglePurchase_replayTokenDoesNotProcessTwice() {
        PaymentEvent existing = new PaymentEvent();
        existing.setId(99L);
        existing.setStatus(PaymentEventStatus.PROCESSED);
        existing.setExternalSubscriptionId("order-123");

        when(paymentEventRepository.findByProviderAndTokenHash(eq("GOOGLE_PLAY"), any()))
                .thenReturn(Optional.of(existing));

        SubscriptionService.GoogleVerifyResult result = subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                10L,
                PlanType.USTA,
                "same-token",
                "prod-1");

        assertTrue(result.idempotentReplay());
        assertTrue(result.verified());
        assertEquals("processed", result.status());
        assertEquals("order-123", result.subscriptionId());

        verify(googlePlayVerificationService, never()).verifySubscription(any(), any());
        verify(companyRepository, never()).save(any(Company.class));
    }
}
