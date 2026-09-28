package com.pusula.backend.service;

import com.pusula.backend.config.WhatsAppIntegrationProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppWebhookSecurityTest {
    private final WhatsAppIntegrationProperties properties = new WhatsAppIntegrationProperties(
            "app", "top-secret", "config", "https://example.test/connect",
            "unused", "v26.0", "verify-me");
    private final WhatsAppWebhookSecurity security = new WhatsAppWebhookSecurity(properties);

    @Test
    void validatesMetaChallengeWithConfiguredToken() {
        assertTrue(security.isVerificationRequestValid("subscribe", "verify-me"));
        assertFalse(security.isVerificationRequestValid("subscribe", "wrong"));
        assertFalse(security.isVerificationRequestValid("unsubscribe", "verify-me"));
    }

    @Test
    void validatesSha256PayloadSignatureAgainstRawBody() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("top-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertTrue(security.isPayloadSignatureValid(body, signature));
        assertFalse(security.isPayloadSignatureValid(body + " ", signature));
        assertFalse(security.isPayloadSignatureValid(body, "sha256=00"));
    }
}
