package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.entity.WhatsAppMessageOutbox;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import com.pusula.backend.repository.WhatsAppMessageStatusEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {
    @Mock WhatsAppMessageStatusEventRepository events;
    @Mock WhatsAppMessageOutboxRepository outboxRepository;
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    private WhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppWebhookService(new ObjectMapper(), events, outboxRepository, integrations);
    }

    @Test
    void persistsStatusIdempotentlyAndUpdatesMatchingOutbox() {
        WhatsAppBusinessIntegration integration = new WhatsAppBusinessIntegration();
        integration.setCompanyId(10L);
        WhatsAppMessageOutbox outbox = new WhatsAppMessageOutbox();
        outbox.setCompanyId(10L);
        outbox.setProviderMessageId("wamid.123");
        outbox.setProviderStatus("sent");

        when(integrations.findByPhoneNumberIdAndDeletedFalse("phone-1"))
                .thenReturn(Optional.of(integration));
        when(outboxRepository.findByProviderMessageId("wamid.123")).thenReturn(Optional.of(outbox));
        when(events.insertIfAbsent(anyString(), eq(10L), isNull(), eq("wamid.123"), eq("phone-1"),
                eq("905551112233"), eq("delivered"), any(LocalDateTime.class), isNull(), isNull(),
                any(LocalDateTime.class))).thenReturn(1);

        int inserted = service.processStatusEvents(payload("delivered"));

        assertEquals(1, inserted);
        assertEquals("delivered", outbox.getProviderStatus());
        verify(outboxRepository).save(outbox);
    }

    @Test
    void duplicateStatusDoesNotMutateOutboxTwice() {
        WhatsAppMessageOutbox outbox = new WhatsAppMessageOutbox();
        outbox.setCompanyId(10L);
        outbox.setProviderMessageId("wamid.123");
        when(integrations.findByPhoneNumberIdAndDeletedFalse("phone-1")).thenReturn(Optional.empty());
        when(outboxRepository.findByProviderMessageId("wamid.123")).thenReturn(Optional.of(outbox));
        when(events.insertIfAbsent(anyString(), anyLong(), isNull(), anyString(), anyString(), anyString(),
                anyString(), any(LocalDateTime.class), isNull(), isNull(), any(LocalDateTime.class)))
                .thenReturn(0);

        assertEquals(0, service.processStatusEvents(payload("delivered")));
        verify(outboxRepository, never()).save(any());
    }

    private String payload(String status) {
        return """
                {"entry":[{"changes":[{"field":"messages","value":{
                  "metadata":{"phone_number_id":"phone-1"},
                  "statuses":[{"id":"wamid.123","status":"%s","timestamp":"1720000000",
                    "recipient_id":"905551112233"}]
                }}]}]}
                """.formatted(status);
    }
}
