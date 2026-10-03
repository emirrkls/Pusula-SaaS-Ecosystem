package com.pusula.backend.controller;

import com.pusula.backend.dto.GoogleAuthRequest;
import com.pusula.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialAuthControllerTest {
    private final SocialAuthenticationService auth = mock(SocialAuthenticationService.class);
    private final AppleAuthChallengeService challenges = mock(AppleAuthChallengeService.class);
    private final AppleSignInOAuthClient apple = mock(AppleSignInOAuthClient.class);
    private final SocialAuthController controller = new SocialAuthController(auth, challenges, apple);

    @Test void rateLimitSharedAcrossChallengeAndLoginAndTrustedProxySeparatesUsers() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Real-IP", "1.2.3.4");
        for (int i = 0; i < 10; i++) { assertEquals(200, controller.challenge(request).getStatusCode().value());
            assertEquals(200, controller.google(new GoogleAuthRequest(), request).getStatusCode().value()); }
        assertEquals(429, controller.challenge(request).getStatusCode().value());
        request.removeHeader("X-Real-IP"); request.addHeader("X-Real-IP", "1.2.3.5");
        assertEquals(200, controller.challenge(request).getStatusCode().value());
        verify(challenges, times(11)).create();
    }

    @Test void untrustedDirectClientCannotSpoofHeadersToEvadeLimit() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("8.8.8.8");
        for (int i = 0; i < 20; i++) { request.removeHeader("X-Real-IP"); request.addHeader("X-Real-IP", "1.2.3." + i);
            assertEquals(200, controller.challenge(request).getStatusCode().value()); }
        assertEquals(429, controller.challenge(request).getStatusCode().value());
    }

    @Test void databaseConflictDoesNotExposeSqlOrIdentityToken() {
        var response = controller.databaseFailure(new DataIntegrityViolationException("secret token, subject, raw SQL"));
        assertEquals(409, response.getStatusCode().value()); assertFalse(response.getBody().toString().contains("secret"));
    }
}
