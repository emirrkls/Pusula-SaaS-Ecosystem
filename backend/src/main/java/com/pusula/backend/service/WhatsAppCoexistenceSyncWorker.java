package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class WhatsAppCoexistenceSyncWorker {
    private final WhatsAppCoexistenceSyncStore store;
    private final WhatsAppCloudApiClient client;
    private final WhatsAppCredentialCrypto crypto;
    private final boolean enabled;

    public WhatsAppCoexistenceSyncWorker(WhatsAppCoexistenceSyncStore store, WhatsAppCloudApiClient client,
            WhatsAppCredentialCrypto crypto, @Value("${whatsapp.api.enabled:false}") boolean enabled) {
        this.store = store; this.client = client; this.crypto = crypto; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${whatsapp.coexistence.sync-delay-ms:15000}")
    public void runDue() {
        if (!enabled) return;
        for (Long id : store.due(LocalDateTime.now())) dispatch(id);
    }

    void dispatch(Long id) {
        store.claim(id, LocalDateTime.now()).ifPresent(job -> {
            String token;
            try {
                token = crypto.decrypt(job.ciphertext());
            } catch (RuntimeException ex) {
                store.outcome(id, null, "CREDENTIAL_UNAVAILABLE", false, LocalDateTime.now());
                return;
            }
            try {
                String requestId = client.requestAppData(job.phoneNumberId(), token, job.syncType());
                store.outcome(id, requestId, null, false, LocalDateTime.now());
            } catch (WhatsAppCloudApiClient.SyncRequestException ex) {
                store.outcome(id, null, ex.code(), ex.definitelyRejectedRetryable(), LocalDateTime.now());
            } catch (RuntimeException ex) {
                // Includes interruption, IO loss and unexpected outcomes. No blind retry.
                store.outcome(id, null, "REQUEST_OUTCOME_UNKNOWN", false, LocalDateTime.now());
            }
        });
    }
}
