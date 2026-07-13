package com.pusula.backend.controller;

import com.pusula.backend.dto.AppleVerifyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionControllerSecurityTest {

    @Test
    void appleVerifyEndpointRequiresAdminRole() throws NoSuchMethodException {
        Method method = SubscriptionController.class.getMethod("verifyApplePurchase", AppleVerifyRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertTrue(preAuthorize.value().contains("COMPANY_ADMIN"));
        assertTrue(preAuthorize.value().contains("SUPER_ADMIN"));
    }
}
