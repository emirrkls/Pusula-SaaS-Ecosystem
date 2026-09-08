package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import com.pusula.backend.dto.WhatsAppOnboardingDtos;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.WhatsAppOnboardingSession;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import com.pusula.backend.repository.WhatsAppOnboardingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppOnboardingServiceTest {
    @Mock WhatsAppOnboardingSessionRepository sessions;
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCredentialCrypto crypto;

    @Test
    void startCreatesSingleUseOpaqueStateWithoutPersistingRawValue() {
        WhatsAppOnboardingService service = service("app-secret", "encryption-key");
        User user = User.builder().id(7L).companyId(10L).username("admin")
                .passwordHash("x").role("COMPANY_ADMIN").build();

        WhatsAppOnboardingDtos.StartResponse response = service.start(user);

        assertTrue(response.url().startsWith("https://www.pusulaiklimlendirme.com/whatsapp-connect#state="));
        String rawState = response.url().substring(response.url().indexOf("state=") + 6);
        assertTrue(rawState.matches("[A-Za-z0-9_-]{40,80}"));
        ArgumentCaptor<WhatsAppOnboardingSession> saved = ArgumentCaptor.forClass(WhatsAppOnboardingSession.class);
        verify(sessions).save(saved.capture());
        assertEquals(10L, saved.getValue().getCompanyId());
        assertEquals(7L, saved.getValue().getUserId());
        assertNotEquals(rawState, saved.getValue().getStateHash());
        assertEquals(64, saved.getValue().getStateHash().length());
        assertTrue(saved.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));
    }

    @Test
    void completeRejectsExpiredSessionBeforeCallingMeta() {
        WhatsAppOnboardingService service = service("app-secret", "encryption-key");
        WhatsAppOnboardingSession expired = new WhatsAppOnboardingSession();
        expired.setCompanyId(10L);
        expired.setUserId(7L);
        expired.setStateHash("hash");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(sessions.findByStateHash(anyString())).thenReturn(Optional.of(expired));

        var request = new WhatsAppOnboardingDtos.CompleteRequest(
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO123", "code", "12345", "67890");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.complete(request));
        assertTrue(error.getMessage().contains("süresi dolmuş"));
        verifyNoInteractions(integrations, crypto);
    }

    @Test
    void statusDoesNotExposeCredentialMaterial() {
        WhatsAppOnboardingService service = service("app-secret", "encryption-key");
        var status = service.status(10L);
        assertFalse(status.connected());
        assertEquals("NOT_CONNECTED", status.status());
        verify(integrations).findByCompanyIdAndDeletedFalse(10L);
    }

    private WhatsAppOnboardingService service(String secret, String encryptionKey) {
        var properties = new WhatsAppIntegrationProperties(
                "4494017667582443", secret, "2136126430333068",
                "https://www.pusulaiklimlendirme.com/whatsapp-connect", encryptionKey, "v26.0");
        return new WhatsAppOnboardingService(properties, sessions, integrations, crypto, new ObjectMapper());
    }
}
