package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class WebhookSecurityService {

    @Value("${iyzico.webhook.secret:}")
    private String iyzicoWebhookSecret;

    public boolean isIyzicoSignatureValid(String rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (iyzicoWebhookSecret == null || iyzicoWebhookSecret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(iyzicoWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            String expectedHex = HexFormat.of().formatHex(digest);
            String expectedBase64 = Base64.getEncoder().encodeToString(digest);

            byte[] provided = signatureHeader.trim().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(provided, expectedHex.getBytes(StandardCharsets.UTF_8))
                    || MessageDigest.isEqual(provided, expectedBase64.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
