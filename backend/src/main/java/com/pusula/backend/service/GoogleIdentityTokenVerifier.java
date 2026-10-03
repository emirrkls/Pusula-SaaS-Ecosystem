package com.pusula.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoogleIdentityTokenVerifier {
    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleIdentityTokenVerifier(@Value("${google.oauth.web-client-id:}") String clientId) {
        this.clientId = clientId;
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(List.of(clientId)).build();
    }

    public VerifiedSocialIdentity verify(String token) {
        if (clientId.isBlank()) throw new IllegalStateException("Google ile giriş henüz yapılandırılmadı.");
        if (token == null || token.isBlank() || token.length() > 8192) throw invalid();
        try {
            var verified = verifier.verify(token);
            if (verified == null) throw invalid();
            var p = verified.getPayload();
            if (!Boolean.TRUE.equals(p.getEmailVerified()) || p.getSubject() == null
                    || p.getSubject().isBlank() || p.getEmail() == null || p.getEmail().isBlank()) throw invalid();
            // Google is not authoritative for arbitrary third-party email domains.
            boolean authoritative = p.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com")
                    || (p.getHostedDomain() != null && !p.getHostedDomain().isBlank());
            return new VerifiedSocialIdentity("GOOGLE", p.getSubject(), p.getEmail(),
                    (String) p.get("name"), authoritative);
        } catch (BadCredentialsException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private BadCredentialsException invalid() { return new BadCredentialsException("Google kimliği doğrulanamadı."); }
}
