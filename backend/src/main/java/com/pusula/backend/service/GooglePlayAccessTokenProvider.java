package com.pusula.backend.service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Credentials stay in the protected deployment file, never in a client or log. */
@Component
public class GooglePlayAccessTokenProvider {
    private final String credentialsFile;
    private final String legacyToken;
    private ServiceAccountCredentials credentials;
    public GooglePlayAccessTokenProvider(
            @Value("${google.play.credentials-file:}") String credentialsFile,
            @Value("${google.play.api-access-token:}") String legacyToken) {
        this.credentialsFile = credentialsFile;
        this.legacyToken = legacyToken;
    }
    public synchronized String accessToken() throws IOException {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            if (legacyToken == null || legacyToken.isBlank()) throw new IOException("PLAY_CREDENTIALS_MISSING");
            return legacyToken;
        }
        if (credentials == null) {
            try (var stream = Files.newInputStream(Path.of(credentialsFile))) {
                // Typed loader: reject arbitrary external-account credential configurations.
                credentials = (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(stream)
                        .createScoped(List.of("https://www.googleapis.com/auth/androidpublisher"));
            }
        }
        credentials.refreshIfExpired();
        if (credentials.getAccessToken() == null) throw new IOException("PLAY_TOKEN_UNAVAILABLE");
        return credentials.getAccessToken().getTokenValue();
    }
    public boolean configured() {
        return credentialsFile != null && !credentialsFile.isBlank()
                && Files.isRegularFile(Path.of(credentialsFile)) && Files.isReadable(Path.of(credentialsFile));
    }
}
