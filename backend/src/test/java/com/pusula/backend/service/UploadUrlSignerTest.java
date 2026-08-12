package com.pusula.backend.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class UploadUrlSignerTest {
    @Test
    void signedUrlValidatesAndTamperingFails() {
        UploadUrlSigner signer = new UploadUrlSigner("a-test-secret-long-enough-for-hmac");
        String signed = signer.sign("/uploads/service-photos/10/20/photo.png");
        URI uri = URI.create(signed);
        Map<String, String> query = Arrays.stream(uri.getQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(value -> value[0], value -> value[1]));

        assertTrue(signer.isValid(uri.getPath(), query.get("expires"), query.get("sig")));
        assertFalse(signer.isValid("/uploads/service-photos/99/20/photo.png",
                query.get("expires"), query.get("sig")));
    }
}
