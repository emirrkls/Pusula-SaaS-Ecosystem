package com.pusula.backend.service;

import io.jsonwebtoken.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.BadCredentialsException;
import java.security.*;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppleIdentityTokenVerifierTest {
    private KeyPair pair;
    private AppleIdentityTokenVerifier verifier;
    @BeforeEach void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair = generator.generateKeyPair();
        var keys = mock(AppleSigningKeyProvider.class); when(keys.get("test-key")).thenReturn(pair.getPublic());
        verifier = new AppleIdentityTokenVerifier(keys, "com.pusula.service");
    }
    @Test void verifiesSignatureIssuerAudienceNonceAndVerifiedEmail() {
        var result = verifier.verify(valid().compact(), "nonce", "Apple Kullanıcısı");
        assertEquals("APPLE", result.provider()); assertEquals("stable-subject", result.subject());
        assertEquals("relay@privaterelay.appleid.com", result.email()); assertTrue(result.emailAuthoritative());
    }
    @Test void returningAccountMayOmitEmailAndName() {
        var token = valid(); token.claim("email", null).claim("email_verified", null);
        var result = verifier.verify(token.compact(), "nonce", null);
        assertEquals("stable-subject", result.subject()); assertNull(result.email()); assertNull(result.fullName());
    }
    @Test void rejectsWrongIssuerAudienceNonceExpiredUnsignedAndMissingExpiry() {
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setIssuer("https://attacker.example").compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setAudience("other.app").compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().compact(), "other-nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setExpiration(Date.from(Instant.now().minusSeconds(1))).compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setExpiration(null).compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify("e30.e30.", "nonce", null));
    }
    @Test void rejectsForeignSignatureAndAlgorithmConfusion() throws Exception {
        var g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048);
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().signWith(g.generateKeyPair().getPrivate(), SignatureAlgorithm.RS256).compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().signWith(io.jsonwebtoken.security.Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact(), "nonce", null));
    }
    @Test void rejectsUnknownKeyMissingSubjectAndFutureIssuedToken() {
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setHeaderParam("kid", "unknown").compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setSubject(null).compact(), "nonce", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(valid().setIssuedAt(Date.from(Instant.now().plusSeconds(120))).compact(), "nonce", null));
    }
    private JwtBuilder valid() {
        return Jwts.builder().setHeaderParam("kid", "test-key").setIssuer("https://appleid.apple.com")
                .setAudience("com.pusula.service").setSubject("stable-subject").setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(300))).claim("nonce", "nonce")
                .claim("email", "relay@privaterelay.appleid.com").claim("email_verified", "true")
                .signWith(pair.getPrivate(), SignatureAlgorithm.RS256);
    }
}
