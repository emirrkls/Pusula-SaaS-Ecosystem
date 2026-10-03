package com.pusula.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SocialTokenCrypto {
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    public SocialTokenCrypto(@Value("${social.auth.token-encryption-key:}") String key) { configuredKey = key; }
    public void requireConfigured() { key(); }
    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey.trim());
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception ex) { throw new IllegalStateException("Sosyal giriş güvenlik yapılandırması eksik."); }
    }
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("Sosyal giriş bilgisi güvenli kaydedilemedi."); }
    }
    public String decrypt(String value) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            if (data.length <= 28) throw new IllegalArgumentException();
            var buffer = ByteBuffer.wrap(data); byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Sosyal giriş bilgisi okunamadı."); }
    }
}
