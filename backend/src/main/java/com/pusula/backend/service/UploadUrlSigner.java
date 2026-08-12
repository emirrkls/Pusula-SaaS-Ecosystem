package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class UploadUrlSigner {
    private final byte[] secret;

    public UploadUrlSigner(@Value("${jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String path) {
        long expires = Instant.now().plusSeconds(3600).getEpochSecond();
        return path + (path.contains("?") ? "&" : "?")
                + "expires=" + expires + "&sig=" + signature(path, expires);
    }

    public boolean isValid(String path, String expiresValue, String providedSignature) {
        if (path == null || expiresValue == null || providedSignature == null) return false;
        try {
            long expires = Long.parseLong(expiresValue);
            if (expires < Instant.now().getEpochSecond() || expires > Instant.now().plusSeconds(7200).getEpochSecond()) {
                return false;
            }
            byte[] expected = signature(path, expires).getBytes(StandardCharsets.US_ASCII);
            byte[] provided = providedSignature.toLowerCase().getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception ex) {
            return false;
        }
    }

    private String signature(String path, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (path + "|" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Upload URL imzası üretilemedi.", ex);
        }
    }
}
