package com.pusula.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import com.pusula.backend.entity.WhatsAppMessageOutbox;
import com.pusula.backend.repository.WhatsAppBusinessIntegrationRepository;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import com.pusula.backend.repository.WhatsAppMessageStatusEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class WhatsAppWebhookService {
    private final ObjectMapper objectMapper;
    private final WhatsAppMessageStatusEventRepository eventRepository;
    private final WhatsAppMessageOutboxRepository outboxRepository;
    private final WhatsAppBusinessIntegrationRepository integrationRepository;

    public WhatsAppWebhookService(
            ObjectMapper objectMapper,
            WhatsAppMessageStatusEventRepository eventRepository,
            WhatsAppMessageOutboxRepository outboxRepository,
            WhatsAppBusinessIntegrationRepository integrationRepository) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.integrationRepository = integrationRepository;
    }

    @Transactional
    public int processStatusEvents(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            int inserted = 0;
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    if (!"messages".equals(change.path("field").asText())) continue;
                    JsonNode value = change.path("value");
                    String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                    Long integrationCompanyId = integrationRepository
                            .findByPhoneNumberIdAndDeletedFalse(phoneNumberId)
                            .map(WhatsAppBusinessIntegration::getCompanyId).orElse(null);
                    for (JsonNode statusNode : value.path("statuses")) {
                        if (persistStatus(statusNode, phoneNumberId, integrationCompanyId)) inserted++;
                    }
                }
            }
            return inserted;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("WhatsApp webhook içeriği okunamadı", ex);
        }
    }

    private boolean persistStatus(JsonNode node, String phoneNumberId, Long integrationCompanyId) {
        String messageId = node.path("id").asText();
        String status = node.path("status").asText();
        if (messageId.isBlank() || status.isBlank()) return false;

        String timestampText = node.path("timestamp").asText();
        LocalDateTime eventTimestamp = parseTimestamp(timestampText);
        String recipientId = node.path("recipient_id").asText(null);
        JsonNode error = node.path("errors").path(0);
        String errorCode = error.isMissingNode() ? null : error.path("code").asText(null);
        String errorMessage = error.isMissingNode() ? null
                : firstNonBlank(error.path("message").asText(), error.path("title").asText());
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }

        WhatsAppMessageOutbox outbox = outboxRepository.findByProviderMessageId(messageId).orElse(null);
        Long companyId = outbox != null ? outbox.getCompanyId() : integrationCompanyId;
        String eventKey = sha256(String.join("|", messageId, status, timestampText,
                recipientId == null ? "" : recipientId, errorCode == null ? "" : errorCode));
        int inserted = eventRepository.insertIfAbsent(eventKey, companyId,
                outbox == null ? null : outbox.getId(), messageId, phoneNumberId,
                recipientId, status, eventTimestamp, errorCode, errorMessage, LocalDateTime.now());
        if (inserted == 0) return false;
        if (outbox != null) updateOutboxStatus(outbox, status, eventTimestamp, errorMessage);
        return true;
    }

    private void updateOutboxStatus(WhatsAppMessageOutbox outbox, String status,
                                    LocalDateTime timestamp, String errorMessage) {
        String normalized = status.toLowerCase(java.util.Locale.ROOT);
        int currentRank = statusRank(outbox.getProviderStatus());
        int incomingRank = statusRank(normalized);
        if ("failed".equals(normalized) || incomingRank >= currentRank) {
            outbox.setProviderStatus(normalized);
        }
        if ("delivered".equals(normalized) && outbox.getDeliveredAt() == null) {
            outbox.setDeliveredAt(timestamp);
        } else if ("read".equals(normalized)) {
            if (outbox.getDeliveredAt() == null) outbox.setDeliveredAt(timestamp);
            if (outbox.getReadAt() == null) outbox.setReadAt(timestamp);
        } else if ("failed".equals(normalized)) {
            outbox.setFailedAt(timestamp);
            if (errorMessage != null && !errorMessage.isBlank()) outbox.setLastError(errorMessage);
        }
        outboxRepository.save(outbox);
    }

    private int statusRank(String status) {
        if (status == null) return 0;
        return switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "accepted", "sent" -> 1;
            case "delivered" -> 2;
            case "read" -> 3;
            default -> 0;
        };
    }

    private LocalDateTime parseTimestamp(String value) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), ZoneOffset.UTC);
        } catch (Exception ignored) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null || second.isBlank() ? null : second;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("WhatsApp webhook olay anahtarı üretilemedi", ex);
        }
    }
}
