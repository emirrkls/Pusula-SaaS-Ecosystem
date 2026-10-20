package com.pusula.backend.service;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppTenantCredentialServiceTest {
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCredentialCrypto crypto;
    @Mock WhatsAppCoexistenceSyncStore syncStore;

    @Test
    void expiredTenantTokenNeverFallsBackToLegacySender() {
        WhatsAppBusinessIntegration integration = integration(LocalDateTime.now().minusMinutes(1));
        when(integrations.findByCompanyIdAndDeletedFalse(10L)).thenReturn(Optional.of(integration));
        WhatsAppTenantCredentialService service = new WhatsAppTenantCredentialService(
                integrations, crypto, "legacy-token", "legacy-phone", syncStore);

        assertThrows(WhatsAppTenantCredentialService.CredentialUnavailableException.class,
                () -> service.resolve(10L));
        assertEquals("EXPIRED", integration.getStatus());
        verify(crypto, never()).decrypt(anyString());
    }

    @Test
    void usesLegacySenderOnlyWhenTenantHasNoIntegration() {
        when(integrations.findByCompanyIdAndDeletedFalse(10L)).thenReturn(Optional.empty());
        WhatsAppTenantCredentialService service = new WhatsAppTenantCredentialService(
                integrations, crypto, "legacy-token", "legacy-phone", syncStore);


        var result = service.resolve(10L);

        assertTrue(result.legacy());
        assertEquals("legacy-phone", result.phoneNumberId());
    }

    private WhatsAppBusinessIntegration integration(LocalDateTime expiresAt) {
        WhatsAppBusinessIntegration integration = new WhatsAppBusinessIntegration();
        integration.setCompanyId(10L);
        integration.setPhoneNumberId("tenant-phone");
        integration.setAccessTokenCiphertext("ciphertext");
        integration.setTokenExpiresAt(expiresAt);
        integration.setStatus("CONNECTED");
        return integration;
    }

    @Test
    void syncingTenantCannotSendOrFallBackToLegacyButIsReportedAsTemporary() {
        var integration = integration(LocalDateTime.now().plusHours(2));
        integration.setCoexistence(true); integration.setCoexistenceStartedAt(LocalDateTime.now());
        when(integrations.findByCompanyIdAndDeletedFalse(10L)).thenReturn(Optional.of(integration));
        when(syncStore.snapshot(integration)).thenReturn(
            new com.pusula.backend.dto.WhatsAppOnboardingDtos.SyncStatus("SYNCING", "ACCEPTED", "RECEIVING", 30));
        var service = new WhatsAppTenantCredentialService(integrations, crypto, "legacy", "legacy-phone", syncStore);
        assertThrows(WhatsAppTenantCredentialService.CoexistencePreparingException.class, () -> service.resolve(10L));
        verifyNoInteractions(crypto);
    }
}
