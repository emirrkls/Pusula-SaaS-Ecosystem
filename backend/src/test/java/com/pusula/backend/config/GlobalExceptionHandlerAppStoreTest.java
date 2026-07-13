package com.pusula.backend.config;

import com.pusula.backend.service.AppStoreVerificationException;
import com.pusula.backend.service.PaymentOwnershipException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerAppStoreTest {

    @Test
    void ownershipConflictReturnsHttp409WithoutInternalDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscription/apple-verify");
        AppStoreVerificationException exception = new AppStoreVerificationException(
                AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT,
                "Apple aboneligi baska bir sirket tarafindan kullanilmis");

        var response = handler.handleAppStoreVerificationException(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("APP_STORE_VERIFY_FAILED", response.getBody().get("code"));
    }

    @Test
    void paymentOwnershipConflictReturns409WithoutPlanOrSubscriptionData() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscription/google-verify");

        var response = handler.handlePaymentOwnershipException(new PaymentOwnershipException(), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PAYMENT_OWNERSHIP_CONFLICT", response.getBody().get("code"));
        assertFalse(response.getBody().containsKey("plan"));
        assertFalse(response.getBody().containsKey("subscriptionId"));
        assertFalse(response.getBody().toString().contains("private-order-id"));
    }
}
