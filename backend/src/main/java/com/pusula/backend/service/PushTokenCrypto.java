package com.pusula.backend.service;

import com.pusula.backend.config.ApplePushProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class PushTokenCrypto {
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private final ApplePushProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PushTokenCrypto(ApplePushProperties properties) {
        this.properties = properties;
    }

    public String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }

    public String hash(String normalizedToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(normalizedToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Push token hashing failed", ex);
        }
    }

    public String encrypt(String normalizedToken) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalizedToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Push token encryption failed", ex);
        }
    }

    public String decrypt(String encodedCiphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(encodedCiphertext);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.US_ASCII);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Push token decryption failed", ex);
        }
    }

    private SecretKeySpec encryptionKey() {
        String configured = properties.getTokenEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("PUSH_TOKEN_ENCRYPTION_KEY is not configured");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PUSH_TOKEN_ENCRYPTION_KEY must be Base64 encoded", ex);
        }
        if (key.length != 32) {
            throw new IllegalStateException("PUSH_TOKEN_ENCRYPTION_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(key, "AES");
    }
}
