package com.pusula.backend.service;

import com.pusula.backend.config.WhatsAppIntegrationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class WhatsAppCredentialCrypto {
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private final WhatsAppIntegrationProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public WhatsAppCredentialCrypto(WhatsAppIntegrationProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("WhatsApp credential encryption failed", ex);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            if (combined.length <= IV_LENGTH) throw new IllegalArgumentException("Invalid ciphertext");
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("WhatsApp credential decryption failed", ex);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.getCredentialEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY is not configured");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY must be Base64 encoded", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
