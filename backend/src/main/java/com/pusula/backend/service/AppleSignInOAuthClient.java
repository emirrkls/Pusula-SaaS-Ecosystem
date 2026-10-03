package com.pusula.backend.service;

import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;

/** Native authorization-code exchange enables server-side revocation on account deletion. */
@Service
public class AppleSignInOAuthClient {
    private final ObjectMapper mapper;
    private final SocialTokenCrypto crypto;
    private final String clientId, teamId, keyId, keyPath;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public AppleSignInOAuthClient(ObjectMapper mapper, SocialTokenCrypto crypto,
            @Value("${apple.sign-in.client-id:com.pusula.service}") String clientId,
            @Value("${apple.sign-in.team-id:}") String teamId,
            @Value("${apple.sign-in.key-id:}") String keyId,
            @Value("${apple.sign-in.key-path:}") String keyPath) {
        this.mapper = mapper; this.crypto = crypto; this.clientId = clientId;
        this.teamId = teamId; this.keyId = keyId; this.keyPath = keyPath;
    }
    public void requireConfigured() {
        if (teamId.isBlank() || keyId.isBlank() || keyPath.isBlank() || !Files.isRegularFile(Path.of(keyPath)))
            throw new IllegalStateException("Apple ile giriş henüz yapılandırılmadı.");
        crypto.requireConfigured();
    }
    public Tokens exchange(String code) {
        requireConfigured();
        var result = post("token", Map.of("grant_type", "authorization_code", "code", code));
        String token = result.path("id_token").asText(), refresh = result.path("refresh_token").asText();
        if (token.isBlank() || refresh.isBlank()) throw new BadCredentialsException("Apple giriş kodu doğrulanamadı.");
        return new Tokens(token, crypto.encrypt(refresh));
    }
    public void revoke(String ciphertext) {
        requireConfigured();
        post("revoke", Map.of("token", crypto.decrypt(ciphertext), "token_type_hint", "refresh_token"));
    }
    private JsonNode post(String path, Map<String, String> params) {
        try {
            Map<String, String> form = new HashMap<>(params);
            form.put("client_id", clientId); form.put("client_secret", clientSecret());
            String body = form.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            var request = HttpRequest.newBuilder(URI.create("https://appleid.apple.com/auth/" + path))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) throw new IllegalStateException("Apple giriş servisine ulaşılamadı. Lütfen tekrar deneyin.");
            if (response.statusCode() != 200) throw new BadCredentialsException("Apple giriş kodu geçersiz veya bağlantı iptal edilemedi.");
            return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Apple giriş servisine ulaşılamadı.");
        } catch (BadCredentialsException | IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Apple giriş servisine ulaşılamadı."); }
    }
    private String clientSecret() throws Exception {
        String pem = Files.readString(Path.of(keyPath)).replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        var key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Instant now = Instant.now();
        return Jwts.builder().setHeaderParam("kid", keyId).setIssuer(teamId).setSubject(clientId)
                .setAudience("https://appleid.apple.com").setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(300))).signWith(key, SignatureAlgorithm.ES256).compact();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public record Tokens(String idToken, String refreshTokenCiphertext) {}
}
