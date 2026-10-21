package com.pusula.backend.context;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.service.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleReactivationTenantGuardTest {
    private Company company;
    private User user;
    private TenantInterceptor interceptor;

    @BeforeEach void setup() {
        company = new Company(); company.setId(29L); company.setPlanType(PlanType.USTA);
        company.setIsReadOnly(true); company.setSubscriptionStatus("EXPIRED");
        company.setSubscriptionProvider("GOOGLE_PLAY");
        var companies = mock(CompanyRepository.class);
        when(companies.findById(29L)).thenReturn(Optional.of(company));
        interceptor = new TenantInterceptor(mock(JwtService.class), companies);
        user = new User(); user.setId(290L); user.setCompanyId(29L); user.setRole("COMPANY_ADMIN");
        authenticate();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); TenantContext.clear(); }

    private boolean allowed(String method, String path) {
        return interceptor.preHandle(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), new Object());
    }

    @Test void expiredRevokedAndPaymentFailedGoogleAdminsMayVerifyANewPurchase() {
        for (String status : List.of("EXPIRED", "REVOKED", "PAYMENT_FAILED")) {
            company.setSubscriptionStatus(status);
            assertTrue(allowed("POST", "/api/subscription/google-verify"), status);
        }
        assertTrue(company.getIsReadOnly(), "The interceptor must not grant an entitlement itself");
    }

    @Test void businessWritesRemainBlockedWhileReactivationIsPending() throws Exception {
        for (String path : List.of("/api/expenses", "/api/customers", "/api/users", "/api/inventory", "/api/tickets")) {
            var response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", path), response, new Object()), path);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("COMPANY_READ_ONLY"));
        }
    }

    @Test void otherMethodsAndLookalikePathsDoNotGetAReactivationException() {
        for (String method : List.of("PUT", "PATCH", "DELETE")) assertFalse(allowed(method, "/api/subscription/google-verify"));
        for (String path : List.of("/api/subscription/google-verify/", "/api/subscription/google-verify-extra",
            "/api/subscription/google-verify/anything", "/api/superadmin/subscription/google-verify")) {
            assertFalse(allowed("POST", path), path);
        }
    }

    @Test void suspendedAndManuallyLockedActiveAccountsRemainBlocked() {
        for (String state : List.of("SUSPENDED", "ACTIVE", "TRIAL", "UNKNOWN")) {
            company.setSubscriptionStatus(state);
            assertFalse(allowed("POST", "/api/subscription/google-verify"), state);
        }
    }

    @Test void aTechnicianCannotUseThePurchaseVerificationException() {
        user.setRole("TECHNICIAN"); authenticate();
        assertFalse(allowed("POST", "/api/subscription/google-verify"));
    }

    @Test void appleAndOtherProviderBehaviorIsUnchanged() {
        assertFalse(allowed("POST", "/api/subscription/apple-verify"));
        for (String provider : List.of("APP_STORE", "IYZICO", "UNKNOWN")) {
            company.setSubscriptionProvider(provider);
            assertFalse(allowed("POST", "/api/subscription/google-verify"), provider);
        }
    }
}
