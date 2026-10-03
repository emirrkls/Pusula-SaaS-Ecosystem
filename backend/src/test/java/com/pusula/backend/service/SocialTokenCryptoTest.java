package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class SocialTokenCryptoTest {
    @Test void encryptsWithFreshIvAndRejectsTamperingOrWrongKey() {
        var crypto = new SocialTokenCrypto(Base64.getEncoder().encodeToString(new byte[32]));
        String a = crypto.encrypt("apple-refresh-token"), b = crypto.encrypt("apple-refresh-token");
        assertNotEquals(a, b); assertFalse(a.contains("apple-refresh-token")); assertEquals("apple-refresh-token", crypto.decrypt(a));
        byte[] bytes = Base64.getDecoder().decode(a); bytes[bytes.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(Base64.getEncoder().encodeToString(bytes)));
        assertThrows(IllegalStateException.class, () -> new SocialTokenCrypto("").requireConfigured());
    }
}
