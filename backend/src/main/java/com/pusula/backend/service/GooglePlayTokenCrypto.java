package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Dedicated deployment key. Bind ciphertext to the inbox message ID to prevent row swapping. */
@Component
public class GooglePlayTokenCrypto {
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    public GooglePlayTokenCrypto(@Value("${google.play.notifications.encryption-key:}") String key) {
        configuredKey = key;
    }
    public void requireConfigured() { key(); }
    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey.trim());
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception ex) { throw new IllegalStateException("PLAY_NOTIFICATION_KEY_UNAVAILABLE"); }
    }
    public String encrypt(String value, String messageId) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(messageId.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("PLAY_NOTIFICATION_ENCRYPTION_FAILED"); }
    }
    public String decrypt(String value, String messageId) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            if (data.length <= 28) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(data); byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(messageId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("PLAY_NOTIFICATION_DECRYPTION_FAILED"); }
    }
}
