package com.pusula.backend.service;

import com.google.api.client.googleapis.auth.oauth2.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GooglePlayPushAuthenticatorTest {
    final GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    final GooglePlayNotificationSettings settings = new GooglePlayNotificationSettings(true,
            "https://example.test/callback", "push@example.test", "projects/test/subscriptions/play", "com.pusula.service");
    final GooglePlayPushAuthenticator authentication = new GooglePlayPushAuthenticator(settings, verifier);
    void identity(String email, boolean verified) throws Exception {
        var token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(new GoogleIdToken.Payload().setEmail(email).setEmailVerified(verified));
        when(verifier.verify("oidc-token")).thenReturn(token);
    }
    @Test void acceptsOnlyVerifiedConfiguredPushIdentity() throws Exception {
        identity("push@example.test", true);
        assertDoesNotThrow(() -> authentication.authenticate("Bearer oidc-token"));
    }
    @Test void rejectsMissingOrUnboundedTokenBeforeVerifier() {
        for (String header : new String[] {"", "Basic token", "Bearer " + "a".repeat(8194)})
            assertEquals(401, assertThrows(ResponseStatusException.class, () -> authentication.authenticate(header)).getStatusCode().value());
        verifyNoInteractions(verifier);
    }
    @Test void googleVerifierRejectionNeverTrustsUnsignedClaims() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> authentication.authenticate("Bearer oidc-token")).getStatusCode().value());
    }
    @Test void rejectsDifferentServiceAccount() throws Exception {
        identity("other@example.test", true);
        assertThrows(ResponseStatusException.class, () -> authentication.authenticate("Bearer oidc-token"));
    }
    @Test void rejectsUnverifiedEmail() throws Exception {
        identity("push@example.test", false);
        assertThrows(ResponseStatusException.class, () -> authentication.authenticate("Bearer oidc-token"));
    }
    @Test void disabledConfigurationFailsClosedWithoutCallingGoogle() {
        var disabled = new GooglePlayPushAuthenticator(new GooglePlayNotificationSettings(false, "", "", "", ""), verifier);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> disabled.authenticate("Bearer oidc-token")).getStatusCode().value());
        verifyNoInteractions(verifier);
    }
}
