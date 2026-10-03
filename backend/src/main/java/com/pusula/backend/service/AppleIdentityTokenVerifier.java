package com.pusula.backend.service;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AppleIdentityTokenVerifier {
    private final AppleSigningKeyProvider keys;
    private final String clientId;

    public AppleIdentityTokenVerifier(AppleSigningKeyProvider keys,
            @Value("${apple.sign-in.client-id:com.pusula.service}") String clientId) {
        this.keys = keys; this.clientId = clientId;
    }

    public VerifiedSocialIdentity verify(String token, String nonce, String name) {
        if (token == null || token.length() > 8192 || nonce == null || nonce.isBlank()) throw invalid();
        try {
            var parsed = Jwts.parserBuilder().requireIssuer("https://appleid.apple.com").requireAudience(clientId)
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override public Key resolveSigningKey(JwsHeader header, Claims claims) {
                            if (!"RS256".equals(header.getAlgorithm()) || header.getKeyId() == null) throw invalid();
                            var key = keys.get(header.getKeyId());
                            if (key == null) throw invalid();
                            return key;
                        }
                    }).build().parseClaimsJws(token);
            var c = parsed.getBody();
            if (c.getExpiration() == null || c.getIssuedAt() == null || c.getSubject() == null
                    || c.getSubject().isBlank() || c.getSubject().length() > 255
                    || c.getIssuedAt().after(new java.util.Date(System.currentTimeMillis() + 30_000))) throw invalid();
            String actualNonce = c.get("nonce", String.class);
            if (actualNonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                    actualNonce.getBytes(StandardCharsets.UTF_8))) throw invalid();
            String email = c.get("email", String.class);
            boolean verifiedEmail = "true".equals(String.valueOf(c.get("email_verified")));
            return new VerifiedSocialIdentity("APPLE", c.getSubject(), verifiedEmail ? email : null, name, verifiedEmail);
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private BadCredentialsException invalid() { return new BadCredentialsException("Apple kimliği doğrulanamadı. Lütfen tekrar deneyin."); }
}
