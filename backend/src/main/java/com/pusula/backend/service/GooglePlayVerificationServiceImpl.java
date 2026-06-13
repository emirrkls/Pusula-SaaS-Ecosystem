package com.pusula.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GooglePlayVerificationServiceImpl implements GooglePlayVerificationService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlayVerificationServiceImpl.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.play.package-name:}")
    private String packageName;

    @Value("${google.play.api-access-token:}")
    private String apiAccessToken;

    @Override
    public GoogleVerificationResult verifySubscription(String purchaseToken, String productId) {
        if (packageName == null || packageName.isBlank() || apiAccessToken == null || apiAccessToken.isBlank()) {
            return new GoogleVerificationResult(false, null, "Google Play doğrulama konfigürasyonu eksik");
        }

        String url = String.format(
                "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s",
                packageName,
                productId,
                purchaseToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiAccessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) {
                return new GoogleVerificationResult(false, null, "Google API boş yanıt döndü");
            }

            Object expiry = body.get("expiryTimeMillis");
            Object paymentState = body.get("paymentState");
            Object orderId = body.get("orderId");
            Object acknowledgementState = body.get("acknowledgementState");

            if (expiry == null || orderId == null) {
                return new GoogleVerificationResult(false, null, "Satın alma kaydı geçersiz");
            }
            if (paymentState != null && !"1".equals(String.valueOf(paymentState))) {
                return new GoogleVerificationResult(false, null, "Ödeme state uygun değil");
            }
            if (acknowledgementState != null && "0".equals(String.valueOf(acknowledgementState))) {
                log.warn("Google purchase henüz acknowledge edilmemiş: productId={}", productId);
            }

            return new GoogleVerificationResult(true, String.valueOf(orderId), null);
        } catch (RestClientException ex) {
            log.warn("Google Play verify failed: {}", ex.getMessage());
            return new GoogleVerificationResult(false, null, "Google doğrulama başarısız");
        }
    }
}
