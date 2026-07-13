package com.pusula.backend.config;

import com.pusula.backend.service.AppStoreVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
