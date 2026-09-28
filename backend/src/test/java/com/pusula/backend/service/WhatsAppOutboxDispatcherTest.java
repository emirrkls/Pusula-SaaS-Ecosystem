package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.WhatsAppMessageOutbox;
import com.pusula.backend.entity.WhatsAppOutboxStatus;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppOutboxDispatcherTest {
    @Mock WhatsAppOutboxClaimService claims;
    @Mock WhatsAppMessageOutboxRepository repository;
    @Mock WhatsAppTenantCredentialService credentials;
    @Mock WhatsAppCloudApiClient client;
    private WhatsAppOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WhatsAppOutboxDispatcher(claims, repository, credentials, client,
                new ObjectMapper(), true, "META");
    }

    @Test
    void dispatchesClaimedMessageAndPersistsProviderId() {
        WhatsAppMessageOutbox item = item();
        when(claims.claimDue(20)).thenReturn(List.of(1L));
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(credentials.resolve(10L)).thenReturn(
                new WhatsAppTenantCredentialService.Credentials("phone-id", "token", false));
        when(client.sendTemplate("phone-id", "token", "+905551112233", "template", "tr",
                List.of("a", "b"))).thenReturn(new WhatsAppCloudApiClient.SendResult("wamid.1"));

        dispatcher.dispatchDueMessages();

        assertEquals(WhatsAppOutboxStatus.SENT, item.getStatus());
        assertEquals("wamid.1", item.getProviderMessageId());
        verify(repository).save(item);
    }

    @Test
    void schedulesRetryWithBackoffForTransientMetaFailure() {
        WhatsAppMessageOutbox item = item();
        item.setAttemptCount(1);
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(credentials.resolve(10L)).thenReturn(
                new WhatsAppTenantCredentialService.Credentials("phone-id", "token", false));
        when(client.sendTemplate(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new WhatsAppCloudApiClient.WhatsAppCloudApiException("rate limited", true));

        dispatcher.dispatchOne(1L);

        assertEquals(WhatsAppOutboxStatus.RETRY, item.getStatus());
        verify(repository).save(item);
    }

    private WhatsAppMessageOutbox item() {
        WhatsAppMessageOutbox item = new WhatsAppMessageOutbox();
        item.setCompanyId(10L);
        item.setStatus(WhatsAppOutboxStatus.PROCESSING);
        item.setRecipientPhone("+905551112233");
        item.setTemplateName("template");
        item.setTemplateLanguage("tr");
        item.setParametersJson("[\"a\",\"b\"]");
        return item;
    }
}
