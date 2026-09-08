package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.PaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceExpiryGracePeriodTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private GooglePlayVerificationService googlePlayVerificationService;
    @Mock private AppleAppStoreVerificationService appleAppStoreVerificationService;
    @Mock private AuditLogService auditLogService;

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
    void subscriptionExpiredSixDaysAgoRemainsWritableDuringGracePeriod() {
        Company company = company(10L, LocalDateTime.now().minusDays(6));
        when(companyRepository.findAll()).thenReturn(List.of(company));

        subscriptionService.enforceExpiredSubscriptions();

        assertFalse(company.getIsReadOnly());
        assertEquals("ACTIVE", company.getSubscriptionStatus());
        verify(companyRepository, never()).save(company);
    }

    @Test
    void subscriptionExpiredEightDaysAgoBecomesReadOnly() {
        Company company = company(11L, LocalDateTime.now().minusDays(8));
        when(companyRepository.findAll()).thenReturn(List.of(company));

        subscriptionService.enforceExpiredSubscriptions();

        assertTrue(company.getIsReadOnly());
        assertEquals("EXPIRED", company.getSubscriptionStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void subscriptionWithoutExpiryRemainsWritableIndefinitely() {
        Company company = company(12L, null);
        when(companyRepository.findAll()).thenReturn(List.of(company));

        subscriptionService.enforceExpiredSubscriptions();

        assertFalse(company.getIsReadOnly());
        assertEquals("ACTIVE", company.getSubscriptionStatus());
        verify(companyRepository, never()).save(company);
    }

    private Company company(Long id, LocalDateTime expiresAt) {
        Company company = new Company();
        company.setId(id);
        company.setName("Company " + id);
        company.setPlanType(PlanType.PATRON);
        company.setSubscriptionStatus("ACTIVE");
        company.setIsReadOnly(false);
        company.setSubscriptionExpiresAt(expiresAt);
        return company;
    }
}
