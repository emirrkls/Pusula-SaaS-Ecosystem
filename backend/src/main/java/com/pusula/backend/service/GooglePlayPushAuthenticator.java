package com.pusula.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Pub/Sub Google OIDC credentials are not Pusula user session tokens. */
@Component
public class GooglePlayPushAuthenticator {
    private final GooglePlayNotificationSettings settings;
    private final GoogleIdTokenVerifier verifier;
    @Autowired
    public GooglePlayPushAuthenticator(GooglePlayNotificationSettings settings) {
        this(settings, new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(List.of(settings.audience())).build());
    }
    GooglePlayPushAuthenticator(GooglePlayNotificationSettings settings, GoogleIdTokenVerifier verifier) {
        this.settings = settings; this.verifier = verifier;
    }
    public void authenticate(String header) {
        if (!settings.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (header == null || !header.startsWith("Bearer ") || header.length() > 8200)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            // Library verifies Google signature, issuer, expiration and exact configured audience.
            var token = verifier.verify(header.substring(7));
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())
                    || !settings.email().equals(token.getPayload().getEmail()))
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            // No token, exception URL or payload is returned/logged.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
