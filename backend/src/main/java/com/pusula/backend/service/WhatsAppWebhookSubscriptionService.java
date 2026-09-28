package com.pusula.backend.service;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WhatsAppWebhookSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookSubscriptionService.class);
    private static final int MAX_ATTEMPTS = 8;

    private final WhatsAppBusinessIntegrationRepository integrationRepository;
    private final WhatsAppCredentialCrypto crypto;
    private final WhatsAppCloudApiClient cloudApiClient;

    @Value("${whatsapp.api.enabled:false}")
    private boolean apiEnabled;

    public WhatsAppWebhookSubscriptionService(
            WhatsAppBusinessIntegrationRepository integrationRepository,
            WhatsAppCredentialCrypto crypto,
            WhatsAppCloudApiClient cloudApiClient) {
        this.integrationRepository = integrationRepository;
        this.crypto = crypto;
        this.cloudApiClient = cloudApiClient;
    }

    @Scheduled(fixedDelayString = "${whatsapp.onboarding.subscription-retry-delay-ms:60000}")
    @Transactional
    public void subscribeDueIntegrations() {
        if (!apiEnabled) return;
        LocalDateTime now = LocalDateTime.now();
        for (WhatsAppBusinessIntegration integration : integrationRepository
                .lockDueWebhookSubscriptions(now, PageRequest.of(0, 10))) {
            attemptSubscription(integration, now);
        }
    }

    void attemptSubscription(WhatsAppBusinessIntegration integration, LocalDateTime now) {
        int attempt = integration.getWebhookSubscriptionAttempts() + 1;
        integration.setWebhookSubscriptionAttempts(attempt);
        try {
            if (integration.getTokenExpiresAt() != null
                    && !integration.getTokenExpiresAt().isAfter(now)) {
                integration.setStatus("EXPIRED");
                throw new WhatsAppTenantCredentialService.CredentialUnavailableException(
                        "WhatsApp bağlantı anahtarının süresi dolmuş");
            }
            if (!"CONNECTED".equals(integration.getStatus())) {
                throw new WhatsAppTenantCredentialService.CredentialUnavailableException(
                        "WhatsApp bağlantısı aktif değil");
            }
            cloudApiClient.subscribeApp(integration.getWabaId(),
                    crypto.decrypt(integration.getAccessTokenCiphertext()));
            integration.setWebhookSubscriptionStatus("SUBSCRIBED");
            integration.setWebhookSubscribedAt(now);
            integration.setWebhookSubscriptionNextAttemptAt(null);
            integration.setWebhookSubscriptionLastError(null);
        } catch (WhatsAppTenantCredentialService.CredentialUnavailableException ex) {
            integration.setWebhookSubscriptionStatus("FAILED");
            integration.setWebhookSubscriptionNextAttemptAt(null);
            integration.setWebhookSubscriptionLastError(truncate(ex.getMessage()));
        } catch (WhatsAppCloudApiClient.WhatsAppCloudApiException ex) {
            boolean retry = ex.isRetryable() && attempt < MAX_ATTEMPTS;
            integration.setWebhookSubscriptionStatus(retry ? "RETRY" : "FAILED");
            integration.setWebhookSubscriptionNextAttemptAt(retry
                    ? now.plusSeconds(backoffSeconds(attempt)) : null);
            integration.setWebhookSubscriptionLastError(truncate(ex.getMessage()));
            log.warn("WhatsApp webhook subscription failed: companyId={}, attempt={}, retryable={}, reason={}",
                    integration.getCompanyId(), attempt, retry, truncate(ex.getMessage()));
        } catch (RuntimeException ex) {
            boolean retry = attempt < MAX_ATTEMPTS;
            integration.setWebhookSubscriptionStatus(retry ? "RETRY" : "FAILED");
            integration.setWebhookSubscriptionNextAttemptAt(retry
                    ? now.plusSeconds(backoffSeconds(attempt)) : null);
            integration.setWebhookSubscriptionLastError(truncate(ex.getMessage()));
            log.warn("WhatsApp webhook subscription failed: companyId={}, attempt={}",
                    integration.getCompanyId(), attempt, ex);
        }
        integrationRepository.save(integration);
    }

    private long backoffSeconds(int attempt) {
        return Math.min(3600L, 30L * (1L << Math.min(Math.max(0, attempt - 1), 7)));
    }

    private String truncate(String value) {
        String safe = value == null ? "Bilinmeyen abonelik hatası" : value;
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }
}
