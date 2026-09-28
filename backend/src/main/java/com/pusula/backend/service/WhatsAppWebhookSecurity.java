package com.pusula.backend.service;

import com.pusula.backend.config.WhatsAppIntegrationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WhatsAppWebhookSecurity {
    private static final String SIGNATURE_PREFIX = "sha256=";
    private final WhatsAppIntegrationProperties properties;

    public WhatsAppWebhookSecurity(WhatsAppIntegrationProperties properties) {
        this.properties = properties;
    }

    public boolean isVerificationRequestValid(String mode, String verifyToken) {
        String configured = properties.getWebhookVerifyToken();
        return "subscribe".equals(mode) && configured != null && !configured.isBlank()
                && verifyToken != null
                && MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                        verifyToken.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isPayloadSignatureValid(String rawBody, String signatureHeader) {
        return rawBody != null && isPayloadSignatureValid(
                rawBody.getBytes(StandardCharsets.UTF_8), signatureHeader);
    }

    public boolean isPayloadSignatureValid(byte[] rawBody, String signatureHeader) {
        String secret = properties.getAppSecret();
        if (rawBody == null || signatureHeader == null
                || !signatureHeader.startsWith(SIGNATURE_PREFIX)
                || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = SIGNATURE_PREFIX + HexFormat.of().formatHex(
                    mac.doFinal(rawBody));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
