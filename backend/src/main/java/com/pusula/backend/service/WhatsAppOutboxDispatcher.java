package com.pusula.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.WhatsAppMessageOutbox;
import com.pusula.backend.entity.WhatsAppOutboxStatus;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class WhatsAppOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 8;
    private static final int BATCH_SIZE = 20;

    private final WhatsAppOutboxClaimService claimService;
    private final WhatsAppMessageOutboxRepository repository;
    private final WhatsAppTenantCredentialService credentialService;
    private final WhatsAppCloudApiClient cloudApiClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String provider;

    public WhatsAppOutboxDispatcher(
            WhatsAppOutboxClaimService claimService,
            WhatsAppMessageOutboxRepository repository,
            WhatsAppTenantCredentialService credentialService,
            WhatsAppCloudApiClient cloudApiClient,
            ObjectMapper objectMapper,
            @Value("${whatsapp.api.enabled:false}") boolean enabled,
            @Value("${whatsapp.api.provider:META}") String provider) {
        this.claimService = claimService;
        this.repository = repository;
        this.credentialService = credentialService;
        this.cloudApiClient = cloudApiClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.provider = provider;
    }

    @Scheduled(fixedDelayString = "${whatsapp.outbox.dispatch-delay-ms:10000}")
    public void dispatchDueMessages() {
        if (!enabled || !"META".equalsIgnoreCase(provider)) return;
        for (Long id : claimService.claimDue(BATCH_SIZE)) {
            dispatchOne(id);
        }
    }

    void dispatchOne(Long id) {
        WhatsAppMessageOutbox item = repository.findById(id).orElse(null);
        if (item == null || item.getStatus() != WhatsAppOutboxStatus.PROCESSING) return;
        try {
            WhatsAppTenantCredentialService.Credentials credentials =
                    credentialService.resolve(item.getCompanyId());
            List<String> parameters = objectMapper.readValue(
                    item.getParametersJson(), new TypeReference<>() {});
            WhatsAppCloudApiClient.SendResult result = cloudApiClient.sendTemplate(
                    credentials.phoneNumberId(), credentials.accessToken(), item.getRecipientPhone(),
                    item.getTemplateName(), item.getTemplateLanguage(), parameters);
            markSent(id, result.messageId());
        } catch (WhatsAppTenantCredentialService.CredentialUnavailableException ex) {
            markFailure(id, ex.getMessage(), false);
        } catch (WhatsAppCloudApiClient.WhatsAppCloudApiException ex) {
            markFailure(id, ex.getMessage(), ex.isRetryable());
        } catch (Exception ex) {
            markFailure(id, ex.getMessage(), true);
        }
    }

    @Transactional
    void markSent(Long id, String providerMessageId) {
        repository.findById(id).ifPresent(item -> {
            item.setStatus(WhatsAppOutboxStatus.SENT);
            item.setProviderMessageId(providerMessageId);
            item.setProviderStatus("accepted");
            item.setSentAt(LocalDateTime.now());
            item.setProcessingStartedAt(null);
            item.setLastError(null);
            repository.save(item);
        });
    }

    @Transactional
    void markFailure(Long id, String reason, boolean retryable) {
        repository.findById(id).ifPresent(item -> {
            String safeReason = truncate(reason == null ? "Bilinmeyen WhatsApp gönderim hatası" : reason, 1000);
            item.setLastError(safeReason);
            item.setProcessingStartedAt(null);
            if (retryable && item.getAttemptCount() < MAX_ATTEMPTS) {
                item.setStatus(WhatsAppOutboxStatus.RETRY);
                item.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(item.getAttemptCount())));
            } else {
                item.setStatus(WhatsAppOutboxStatus.FAILED);
                item.setFailedAt(LocalDateTime.now());
            }
            repository.save(item);
            log.warn("WhatsApp outbox delivery failed: id={}, attempt={}, retryable={}, reason={}",
                    id, item.getAttemptCount(), retryable, safeReason);
        });
    }

    private long backoffSeconds(int attempt) {
        return Math.min(3600L, 30L * (1L << Math.min(Math.max(0, attempt - 1), 7)));
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
