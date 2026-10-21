package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.CompanyRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePlayBillingStatusServiceTest {
    @Mock CompanyRepository companies;
    @Mock GooglePlayReceiptStore receipts;
    @Mock GooglePlayAccessTokenProvider credentials;
    Company company;
    GooglePlayBillingStatusService service;
    @BeforeEach void setup() {
        company=new Company(); company.setId(10L); company.setPlanType(PlanType.CIRAK); company.setIsReadOnly(false);
        when(companies.findById(10L)).thenReturn(Optional.of(company));
        service=new GooglePlayBillingStatusService(companies,receipts,credentials,"com.pusula.service");
    }
    void ready() { when(receipts.ready()).thenReturn(true); when(credentials.configured()).thenReturn(true); }
    @Test void unavailableEncryptedVaultBlocksCheckoutBeforePayment() {
        var status=service.status(10L); assertFalse(status.ready()); assertFalse(status.purchaseAllowed());
        assertEquals("server_not_ready",status.reason()); verifyNoInteractions(credentials);
    }
    @Test void missingServiceAccountBlocksCheckout() {
        when(receipts.ready()).thenReturn(true); assertFalse(service.status(10L).purchaseAllowed());
    }
    @Test void healthyFreeTenantCanPurchase() { ready(); assertTrue(service.status(10L).purchaseAllowed()); }
    @Test void suspensionIsNotBypassedWithAnotherPurchase() {
        ready(); company.setSubscriptionStatus("SUSPENDED");
        assertEquals("account_restricted",service.status(10L).reason());
    }
    @Test void activeOtherProviderPreventsDoubleBilling() {
        ready(); company.setPlanType(PlanType.USTA); company.setSubscriptionProvider("APP_STORE");
        company.setSubscriptionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        assertEquals("another_provider_active",service.status(10L).reason());
    }
    @Test void expiredOtherProviderAllowsExplicitNewPurchase() {
        ready(); company.setPlanType(PlanType.USTA); company.setSubscriptionProvider("APP_STORE");
        company.setSubscriptionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        assertTrue(service.status(10L).purchaseAllowed());
    }
    @Test void foreignReceiptMetadataIsNeverExposed() {
        ready(); company.setSubscriptionProvider("GOOGLE_PLAY"); company.setExternalSubscriptionId("google:hash");
        when(receipts.find("hash")).thenReturn(Optional.of(new GooglePlayPurchase("hash",11L)));
        var result=service.status(10L); assertNull(result.receiptHash()); assertNull(result.basePlanId());
    }
    @Test void wrongPackageFailsClosed() {
        ready(); service=new GooglePlayBillingStatusService(companies,receipts,credentials,"another.app");
        assertFalse(service.status(10L).ready());
    }
}
