package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.*;
import java.util.*;

/** Apple's public keys only; never follows token-supplied URLs. */
@Component
public class AppleSigningKeyProvider {
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Map<String, PublicKey> keys = Map.of();
    private Instant fetchedAt = Instant.EPOCH;

    public AppleSigningKeyProvider(ObjectMapper mapper) { this.mapper = mapper; }

    public synchronized PublicKey get(String kid) {
        Instant now = Instant.now();
        boolean expired = fetchedAt.plus(Duration.ofHours(6)).isBefore(now);
        if (expired || (!keys.containsKey(kid) && fetchedAt.plusSeconds(60).isBefore(now))) {
            try {
                var request = HttpRequest.newBuilder(URI.create("https://appleid.apple.com/auth/keys"))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 || response.body().length() > 100_000) throw new IllegalStateException();
                Map<String, PublicKey> fresh = new HashMap<>();
                for (var key : mapper.readTree(response.body()).path("keys")) {
                    if (!"RSA".equals(key.path("kty").asText()) || !"RS256".equals(key.path("alg").asText())
                            || !"sig".equals(key.path("use").asText())) continue;
                    var decoder = Base64.getUrlDecoder();
                    var spec = new RSAPublicKeySpec(new BigInteger(1, decoder.decode(key.path("n").asText())),
                            new BigInteger(1, decoder.decode(key.path("e").asText())));
                    fresh.put(key.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(spec));
                }
                if (fresh.isEmpty()) throw new IllegalStateException();
                keys = Map.copyOf(fresh);
                fetchedAt = now;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Apple doğrulama servisine ulaşılamadı.");
            } catch (Exception ex) { throw new IllegalStateException("Apple doğrulama servisine ulaşılamadı."); }
        }
        return keys.get(kid);
    }
}
