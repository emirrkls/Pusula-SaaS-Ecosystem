package com.pusula.backend.controller;

import com.pusula.backend.dto.GoogleVerifyRequest;
import com.pusula.backend.dto.GoogleVerifyResponse;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.PlanRepository;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerGooglePlanSecurityTest {

    @Mock
    private PlanRepository planRepository;
    @Mock
    private FeatureService featureService;
    @Mock
    private SubscriptionService subscriptionService;

    private SubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new SubscriptionController(planRepository, featureService, subscriptionService);
        User user = new User();
        user.setCompanyId(10L);
        user.setRole("COMPANY_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ustaProductIgnoresClientPatronPlan() {
        GoogleVerifyRequest request = request("usta", "PATRON");
        when(subscriptionService.verifyGooglePurchaseAndUpgradePlan(10L, "token", "usta"))
                .thenReturn(new SubscriptionService.GoogleVerifyResult(
                        true, false, "USTA", "order-1", "processed"));

        ResponseEntity<GoogleVerifyResponse> response = controller.verifyGooglePurchase(request);

        assertEquals("USTA", response.getBody().getPlan());
        verify(subscriptionService).verifyGooglePurchaseAndUpgradePlan(10L, "token", "usta");
    }

    @Test
    void patronProductIgnoresClientUstaPlan() {
        GoogleVerifyRequest request = request("patron", "USTA");
        when(subscriptionService.verifyGooglePurchaseAndUpgradePlan(10L, "token", "patron"))
                .thenReturn(new SubscriptionService.GoogleVerifyResult(
                        true, false, "PATRON", "order-2", "processed"));

        ResponseEntity<GoogleVerifyResponse> response = controller.verifyGooglePurchase(request);

        assertEquals("PATRON", response.getBody().getPlan());
        verify(subscriptionService).verifyGooglePurchaseAndUpgradePlan(10L, "token", "patron");
    }

    private GoogleVerifyRequest request(String productId, String clientPlan) {
        GoogleVerifyRequest request = new GoogleVerifyRequest();
        request.setPurchaseToken("token");
        request.setProductId(productId);
        request.setPlan(clientPlan);
        return request;
    }
}
