package com.pusula.backend.controller;

import com.pusula.backend.dto.AppleVerifyRequest;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.PlanRepository;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SubscriptionControllerAppleResultTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void failedReplayMustNotReturnSuccessfulNoContent() {
        assertStatus(false, 409);
    }

    @Test
    void verifiedPurchaseReturnsNoContent() {
        assertStatus(true, 204);
    }

    private void assertStatus(boolean verified, int expected) {
        SubscriptionService service = mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(
                mock(PlanRepository.class), mock(FeatureService.class), service);
        User user = new User();
        user.setCompanyId(10L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        AppleVerifyRequest request = new AppleVerifyRequest();
        request.setSignedTransactionInfo("test-signed-transaction");
        when(service.verifyAppleTransactionAndUpgradePlan(10L, "test-signed-transaction"))
                .thenReturn(new SubscriptionService.AppleVerifyResult(verified, true, "USTA", "test-subscription", verified ? "processed" : "failed"));
        assertEquals(expected, controller.verifyApplePurchase(request).getStatusCode().value());
    }
}
