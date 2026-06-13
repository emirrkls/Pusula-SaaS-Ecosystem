package com.pusula.backend.controller;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRateLimitTest {

    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private HttpServletRequest httpServletRequest;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authenticationService);
    }

    @Test
    void authenticate_afterTooManyFailures_returns429() {
        AuthRequest request = new AuthRequest();
        request.setUsername("user@test.com");
        request.setPassword("wrong");

        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authenticationService.authenticate(any())).thenThrow(new RuntimeException("bad creds"));

        for (int i = 0; i < 8; i++) {
            assertThrows(RuntimeException.class, () -> authController.authenticate(request, httpServletRequest));
        }

        ResponseEntity<?> response = authController.authenticate(request, httpServletRequest);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(429, response.getStatusCode().value());
        assertNotNull(body);
        assertEquals("AUTH_RATE_LIMITED", body.get("code"));
        verify(authenticationService, times(8)).authenticate(any());
    }
}
