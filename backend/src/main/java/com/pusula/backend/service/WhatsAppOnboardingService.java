package com.pusula.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import com.pusula.backend.dto.WhatsAppOnboardingDtos;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.entity.WhatsAppOnboardingSession;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import com.pusula.backend.repository.WhatsAppOnboardingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;

@Service
public class WhatsAppOnboardingService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final long DEFAULT_TOKEN_TTL_SECONDS = Duration.ofDays(60).toSeconds();

    private final WhatsAppIntegrationProperties properties;
    private final WhatsAppOnboardingSessionRepository sessionRepository;
    private final WhatsAppBusinessIntegrationRepository integrationRepository;
    private final WhatsAppCredentialCrypto crypto;
    private final ObjectMapper objectMapper;
    private final WhatsAppCoexistenceSyncStore syncStore;
    private final SecureRandom secureRandom = new SecureRandom();
    private final HttpClient httpClient;

    @Autowired
    public WhatsAppOnboardingService(WhatsAppIntegrationProperties properties,
            WhatsAppOnboardingSessionRepository sessionRepository,
            WhatsAppBusinessIntegrationRepository integrationRepository,
            WhatsAppCredentialCrypto crypto, ObjectMapper objectMapper, WhatsAppCoexistenceSyncStore syncStore) {
        this(properties, sessionRepository, integrationRepository, crypto, objectMapper, syncStore,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    WhatsAppOnboardingService(WhatsAppIntegrationProperties properties,
            WhatsAppOnboardingSessionRepository sessionRepository,
            WhatsAppBusinessIntegrationRepository integrationRepository,
            WhatsAppCredentialCrypto crypto, ObjectMapper objectMapper, WhatsAppCoexistenceSyncStore syncStore,
            HttpClient httpClient) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.integrationRepository = integrationRepository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.syncStore = syncStore;
        this.httpClient = httpClient;
    }

    @Transactional
    public WhatsAppOnboardingDtos.StartResponse start(User user) {
        requireConfigured(false);
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(SESSION_TTL.toSeconds());

        WhatsAppOnboardingSession session = new WhatsAppOnboardingSession();
        session.setCompanyId(user.getCompanyId());
        session.setUserId(user.getId());
        session.setStateHash(sha256(state));
        session.setExpiresAt(expiresAt);
        sessionRepository.save(session);

        // Keep the one-time state in the URL fragment so it is not sent in HTTP
        // request logs or Referer headers while the Meta SDK is loading.
        String url = properties.getConnectUrl() + "#state=" + state;
        return new WhatsAppOnboardingDtos.StartResponse(
                url, properties.getAppId(), properties.getConfigurationId(), expiresAt);
    }

    @Transactional
    public WhatsAppOnboardingDtos.StatusResponse status(Long companyId) {
        return integrationRepository.findByCompanyIdAndDeletedFalse(companyId)
                .map(value -> {
                    boolean expired = value.getTokenExpiresAt() != null
                            && !value.getTokenExpiresAt().isAfter(LocalDateTime.now());
                    if (expired && "CONNECTED".equals(value.getStatus())) {
                        value.setStatus("EXPIRED");
                        integrationRepository.save(value);
                    }
                    return new WhatsAppOnboardingDtos.StatusResponse(
                            "CONNECTED".equals(value.getStatus()) && !expired,
                            value.getDisplayPhoneNumber(), value.getVerifiedName(), value.getStatus(),
                            value.getTokenExpiresAt(), value.getUpdatedAt(), syncStore.snapshot(value));
                })
                .orElseGet(() -> new WhatsAppOnboardingDtos.StatusResponse(
                        false, null, null, "NOT_CONNECTED", null, null));
    }

    @Transactional
    public WhatsAppOnboardingDtos.CompleteResponse complete(WhatsAppOnboardingDtos.CompleteRequest request) {
        requireConfigured(true);
        LocalDateTime now = LocalDateTime.now();
        WhatsAppOnboardingSession session = sessionRepository.findByStateHash(sha256(request.state()))
                .orElseThrow(() -> new IllegalArgumentException("Bağlantı oturumu geçersiz"));
        if (session.getUsedAt() != null) {
            WhatsAppBusinessIntegration existing = completedIntegration(session, now);
            if (!existing.getWabaId().equals(request.wabaId())
                    || (request.phoneNumberId() != null && !existing.getPhoneNumberId().equals(request.phoneNumberId()))) {
                throw new IllegalArgumentException("Bağlantı oturumu farklı bir hesap için kullanılmış");
            }
            return new WhatsAppOnboardingDtos.CompleteResponse("CONNECTED".equals(existing.getStatus())
                && (existing.getTokenExpiresAt() == null || existing.getTokenExpiresAt().isAfter(now)),
                existing.getDisplayPhoneNumber(), existing.getVerifiedName(), syncStore.snapshot(existing));
        }
        if (!session.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Bağlantı oturumunun süresi dolmuş veya daha önce kullanılmış");
        }
        if (request.phoneNumberId() == null && !Boolean.TRUE.equals(request.coexistence()))
            throw new IllegalArgumentException("Telefon numarası seçimi eksik");

        TokenResult token = exchangeCode(request.code());
        PhoneResult phone = verifyPhoneOwnership(request.wabaId(), request.phoneNumberId(),
            Boolean.TRUE.equals(request.coexistence()), token.accessToken());

        integrationRepository.findByPhoneNumberIdAndDeletedFalse(phone.id())
                .filter(existing -> !existing.getCompanyId().equals(session.getCompanyId()))
                .ifPresent(existing -> { throw new IllegalStateException("Bu WhatsApp numarası başka bir işletmeye bağlı"); });

        WhatsAppBusinessIntegration integration = integrationRepository
                .findByCompanyIdAndDeletedFalse(session.getCompanyId())
                .orElseGet(WhatsAppBusinessIntegration::new);
        integration.setCompanyId(session.getCompanyId());
        integration.setWabaId(request.wabaId());
        integration.setPhoneNumberId(phone.id());
        integration.setDisplayPhoneNumber(phone.displayPhoneNumber());
        integration.setVerifiedName(phone.verifiedName());
        integration.setAccessTokenCiphertext(crypto.encrypt(token.accessToken()));
        integration.setTokenExpiresAt(token.expiresInSeconds() > 0 ? now.plusSeconds(token.expiresInSeconds()) : null);
        integration.setCoexistence(phone.coexistence());
        integration.setCoexistenceGeneration(UUID.randomUUID().toString());
        integration.setCoexistenceStartedAt(now);
        integration.setCoexistenceLifecycleAt(now.withNano(0));
        integration.setStatus("CONNECTED");
        integration.setWebhookSubscriptionStatus("PENDING");
        integration.setWebhookSubscriptionAttempts(0);
        integration.setWebhookSubscriptionNextAttemptAt(now);
        integration.setWebhookSubscriptionLastError(null);
        integration.setWebhookSubscribedAt(null);
        integrationRepository.saveAndFlush(integration);
        syncStore.enqueue(integration, now);

        session.setUsedAt(now);
        session.setCompletedIntegrationId(integration.getId());
        session.setCompletedGeneration(integration.getCoexistenceGeneration());
        sessionRepository.save(session);
        return new WhatsAppOnboardingDtos.CompleteResponse(true, phone.displayPhoneNumber(), phone.verifiedName(),
            syncStore.snapshot(integration));
    }

    @Transactional
    public WhatsAppOnboardingDtos.StatusResponse progress(String state) {
        WhatsAppOnboardingSession session = sessionRepository.findByStateHash(sha256(state))
            .orElseThrow(() -> new IllegalArgumentException("Bağlantı oturumu geçersiz"));
        WhatsAppBusinessIntegration integration = completedIntegration(session, LocalDateTime.now());
        boolean connected = "CONNECTED".equals(integration.getStatus()) &&
            (integration.getTokenExpiresAt() == null || integration.getTokenExpiresAt().isAfter(LocalDateTime.now()));
        return new WhatsAppOnboardingDtos.StatusResponse(connected, integration.getDisplayPhoneNumber(),
            integration.getVerifiedName(), integration.getStatus(), integration.getTokenExpiresAt(),
            integration.getUpdatedAt(), syncStore.snapshot(integration));
    }

    private WhatsAppBusinessIntegration completedIntegration(WhatsAppOnboardingSession session, LocalDateTime now) {
        if (session.getUsedAt() == null || session.getCompletedIntegrationId() == null
                || !session.getUsedAt().plusHours(24).isAfter(now)) {
            throw new IllegalArgumentException("Bağlantı sonucu bu oturumdan alınamıyor");
        }
        WhatsAppBusinessIntegration integration = integrationRepository.findByCompanyIdAndDeletedFalse(session.getCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Bağlantı bulunamadı"));
        if (!Objects.equals(integration.getId(), session.getCompletedIntegrationId())
                || !Objects.equals(integration.getCoexistenceGeneration(), session.getCompletedGeneration())) {
            throw new IllegalArgumentException("Bağlantı oturumu yenilenmiş; Pusula Ayarlar ekranından kontrol edin");
        }
        return integration;
    }

    private TokenResult exchangeCode(String code) {
        try {
            String form = "client_id=" + encode(properties.getAppId())
                    + "&client_secret=" + encode(properties.getAppSecret())
                    + "&code=" + encode(code);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + properties.getGraphVersion() + "/oauth/access_token"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || body.path("access_token").asText().isBlank()) {
                throw new IllegalStateException("Meta yetkilendirme kodu değiştirilemedi");
            }
            long expiresIn = body.path("expires_in").asLong(DEFAULT_TOKEN_TTL_SECONDS);
            return new TokenResult(body.path("access_token").asText(), expiresIn);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta bağlantısı kesintiye uğradı", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Meta yetkilendirme servisine ulaşılamadı", ex);
        }
    }

    private PhoneResult verifyPhoneOwnership(String wabaId, String phoneNumberId, boolean requireCoexistence, String token) {
        try {
            if (phoneNumberId == null && !requireCoexistence) {
                throw new IllegalStateException("Telefon numarası seçimi eksik");
            }
            String url = "https://graph.facebook.com/" + properties.getGraphVersion() + "/" + wabaId
                    + "/phone_numbers?fields=id,display_phone_number,verified_name,is_on_biz_app,platform_type&limit=100";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Meta WhatsApp hesabı doğrulanamadı");
            }
            var candidates = new ArrayList<PhoneResult>();
            for (JsonNode item : body.path("data")) {
                String id = item.path("id").asText();
                boolean coexistence = item.path("is_on_biz_app").asBoolean(false)
                    && "CLOUD_API".equals(item.path("platform_type").asText());
                if (id.matches("[0-9]{5,32}") && (phoneNumberId == null || phoneNumberId.equals(id))
                        && (!requireCoexistence || coexistence)) {
                    candidates.add(new PhoneResult(id, item.path("display_phone_number").asText(),
                        item.path("verified_name").asText(), coexistence));
                }
            }
            // Never guess among multiple phones or an incomplete paginated result.
            if (candidates.size() == 1 && (phoneNumberId != null || body.path("paging").path("next").asText().isBlank())) {
                return candidates.get(0);
            }
            if (candidates.size() > 1 || !body.path("paging").path("next").asText().isBlank())
                throw new IllegalStateException("Birden fazla numara bulundu; seçilen telefon güvenle belirlenemedi");
            throw new IllegalStateException("Seçilen telefon numarası WhatsApp Business hesabına ait değil");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta bağlantısı kesintiye uğradı", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("WhatsApp telefon numarası doğrulanamadı", ex);
        }
    }

    private void requireConfigured(boolean includeSecret) {
        if (properties.getAppId().isBlank() || properties.getConfigurationId().isBlank()
                || properties.getConnectUrl().isBlank()
                || (includeSecret && (properties.getAppSecret().isBlank()
                    || properties.getCredentialEncryptionKey().isBlank()))) {
            throw new IllegalStateException("WhatsApp Embedded Signup sunucu ayarları tamamlanmamış");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bağlantı oturumu doğrulanamadı", ex);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    private record TokenResult(String accessToken, long expiresInSeconds) {}
    private record PhoneResult(String id, String displayPhoneNumber, String verifiedName, boolean coexistence) {}
}
