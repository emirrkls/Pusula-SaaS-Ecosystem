package com.pusula.backend.service;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WhatsAppTenantCredentialService {
    private final WhatsAppBusinessIntegrationRepository integrationRepository;
    private final WhatsAppCredentialCrypto crypto;
    private final String legacyToken;
    private final String legacyPhoneNumberId;
    private final WhatsAppCoexistenceSyncStore syncStore;

    public WhatsAppTenantCredentialService(
            WhatsAppBusinessIntegrationRepository integrationRepository,
            WhatsAppCredentialCrypto crypto,
            @Value("${whatsapp.api.token:}") String legacyToken,
            @Value("${whatsapp.api.phone-id:}") String legacyPhoneNumberId, WhatsAppCoexistenceSyncStore syncStore) {
        this.integrationRepository = integrationRepository;
        this.crypto = crypto;
        this.legacyToken = legacyToken;
        this.legacyPhoneNumberId = legacyPhoneNumberId;
        this.syncStore = syncStore;
    }

    public Credentials resolve(Long companyId) {
        WhatsAppBusinessIntegration integration = integrationRepository
                .findByCompanyIdAndDeletedFalse(companyId).orElse(null);
        if (integration != null) {
            LocalDateTime now = LocalDateTime.now();
            if (integration.getTokenExpiresAt() != null && !integration.getTokenExpiresAt().isAfter(now)) {
                integration.setStatus("EXPIRED");
                integrationRepository.save(integration);
                throw new CredentialUnavailableException("WhatsApp bağlantı anahtarının süresi dolmuş");
            }
            if (!"CONNECTED".equals(integration.getStatus())) {
                throw new CredentialUnavailableException("WhatsApp bağlantısı aktif değil");
            }
            if (integration.isCoexistence()) {
                var sync = syncStore.snapshot(integration);
                if (sync != null && SetHolder.PENDING.contains(sync.status()))
                    throw new CoexistencePreparingException(integration.getCoexistenceStartedAt() == null
                        ? now : integration.getCoexistenceStartedAt().plusHours(24));
                if (sync == null || !"READY".equals(sync.status()))
                    throw new CredentialUnavailableException("WhatsApp eşitlemesi henüz hazır değil; bağlantı durumunu kontrol edin");
            }
            try {
                return new Credentials(integration.getPhoneNumberId(),
                        crypto.decrypt(integration.getAccessTokenCiphertext()), false);
            } catch (RuntimeException ex) {
                integration.setStatus("ERROR");
                integrationRepository.save(integration);
                throw new CredentialUnavailableException("WhatsApp bağlantı anahtarı çözülemedi", ex);
            }
        }

        // Backward-compatible pilot sender. It is used only when the tenant has no
        // integration at all; an expired/broken tenant token never falls through here.
        if (legacyToken == null || legacyToken.isBlank()
                || legacyPhoneNumberId == null || legacyPhoneNumberId.isBlank()) {
            throw new CredentialUnavailableException("İşletmeye ait WhatsApp bağlantısı bulunamadı");
        }
        return new Credentials(legacyPhoneNumberId, legacyToken, true);
    }

    public record Credentials(String phoneNumberId, String accessToken, boolean legacy) {}

    private static class SetHolder {
        static final java.util.Set<String> PENDING = java.util.Set.of("WAITING_WEBHOOKS", "SYNCING");
    }
    public static class CoexistencePreparingException extends CredentialUnavailableException {
        private final LocalDateTime deadline;
        public CoexistencePreparingException(LocalDateTime deadline) {
            super("WhatsApp eşitlemesi hazırlanıyor"); this.deadline = deadline;
        }
        public LocalDateTime deadline() { return deadline; }
    }

    public static class CredentialUnavailableException extends RuntimeException {
        public CredentialUnavailableException(String message) { super(message); }
        public CredentialUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
