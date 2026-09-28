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

    public WhatsAppTenantCredentialService(
            WhatsAppBusinessIntegrationRepository integrationRepository,
            WhatsAppCredentialCrypto crypto,
            @Value("${whatsapp.api.token:}") String legacyToken,
            @Value("${whatsapp.api.phone-id:}") String legacyPhoneNumberId) {
        this.integrationRepository = integrationRepository;
        this.crypto = crypto;
        this.legacyToken = legacyToken;
        this.legacyPhoneNumberId = legacyPhoneNumberId;
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

    public static class CredentialUnavailableException extends RuntimeException {
        public CredentialUnavailableException(String message) { super(message); }
        public CredentialUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
