package com.pusula.backend.service;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookSubscriptionServiceTest {
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCredentialCrypto crypto;
    @Mock WhatsAppCloudApiClient client;

    @Test
    void successfulSubscriptionIsPersisted() {
        WhatsAppBusinessIntegration integration = integration();
        when(crypto.decrypt("ciphertext")).thenReturn("token");
        WhatsAppWebhookSubscriptionService service =
                new WhatsAppWebhookSubscriptionService(integrations, crypto, client);

        service.attemptSubscription(integration, LocalDateTime.now());

        verify(client).subscribeApp("waba", "token");
        assertEquals("SUBSCRIBED", integration.getWebhookSubscriptionStatus());
        assertNotNull(integration.getWebhookSubscribedAt());
        verify(integrations).save(integration);
    }

    @Test
    void transientFailureSchedulesPersistentRetry() {
        WhatsAppBusinessIntegration integration = integration();
        when(crypto.decrypt("ciphertext")).thenReturn("token");
        doThrow(new WhatsAppCloudApiClient.WhatsAppCloudApiException("busy", true))
                .when(client).subscribeApp("waba", "token");
        WhatsAppWebhookSubscriptionService service =
                new WhatsAppWebhookSubscriptionService(integrations, crypto, client);

        service.attemptSubscription(integration, LocalDateTime.now());

        assertEquals("RETRY", integration.getWebhookSubscriptionStatus());
        assertNotNull(integration.getWebhookSubscriptionNextAttemptAt());
        assertEquals(1, integration.getWebhookSubscriptionAttempts());
    }

    private WhatsAppBusinessIntegration integration() {
        WhatsAppBusinessIntegration integration = new WhatsAppBusinessIntegration();
        integration.setCompanyId(10L);
        integration.setWabaId("waba");
        integration.setPhoneNumberId("phone");
        integration.setAccessTokenCiphertext("ciphertext");
        integration.setTokenExpiresAt(LocalDateTime.now().plusDays(1));
        integration.setStatus("CONNECTED");
        integration.setWebhookSubscriptionStatus("PENDING");
        return integration;
    }
}
