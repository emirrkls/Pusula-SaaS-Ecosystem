package com.pusula.backend.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/**
 * Tenant-scoped read-only WhatsApp mirror, not a customer import or notification consent source.
 * Only the HMAC-verified webhook controller may feed this service.
 */
@Service
public class WhatsAppCoexistenceMirrorService {
    private final WhatsAppBusinessIntegrationRepository integrations;
    private final WhatsAppCoexistenceSyncJobRepository jobs;
    private final WhatsAppCoexistenceRecordRepository records;
    private final WhatsAppCredentialCrypto crypto;
    private final ObjectMapper mapper;
    private final int retentionDays;

    public WhatsAppCoexistenceMirrorService(WhatsAppBusinessIntegrationRepository integrations,
            WhatsAppCoexistenceSyncJobRepository jobs, WhatsAppCoexistenceRecordRepository records,
            WhatsAppCredentialCrypto crypto, ObjectMapper mapper,
            @Value("${whatsapp.coexistence.retention-days:30}") int retentionDays) {
        this.integrations = integrations; this.jobs = jobs; this.records = records;
        this.crypto = crypto; this.mapper = mapper; this.retentionDays = Math.max(1, Math.min(90, retentionDays));
    }

    @Transactional
    public int processChange(String wabaId, JsonNode entryTime, String field, JsonNode value) {
        if (!Set.of("account_update", "history", "smb_app_state_sync", "smb_message_echoes", "messages").contains(field)) return 0;
        if (wabaId == null || !wabaId.matches("[0-9]{5,32}")) return 0;
        List<WhatsAppBusinessIntegration> candidates = integrations.lockActiveByWabaId(wabaId);
        String phoneId = value.path("metadata").path("phone_number_id").asText();
        int changed = 0;
        for (WhatsAppBusinessIntegration integration : candidates) {
            if (!integration.isCoexistence() || integration.getCoexistenceGeneration() == null) continue;
            if ("account_update".equals(field)) {
                changed += lifecycle(integration, entryTime, value);
                continue;
            }
            // A matching phone alone is insufficient: the enclosing WABA must also match.
            if (!"CONNECTED".equals(integration.getStatus()) || !integration.getPhoneNumberId().equals(phoneId)) continue;
            if ("smb_app_state_sync".equals(field)) {
                for (JsonNode contact : value.path("state_sync")) changed += contact(integration, contact);
                updateJob(integration, "smb_app_state_sync", "RECEIVING", 0, null);
            } else if ("history".equals(field)) {
                WhatsAppCoexistenceSyncJob historyJob = jobs.findByIntegrationIdAndGeneration(
                    integration.getId(), integration.getCoexistenceGeneration()).stream()
                    .filter(j -> "history".equals(j.getSyncType())).findFirst().orElse(null);
                if (historyJob == null || historyJob.getRequestedAt() == null || "DECLINED".equals(historyJob.getStatus())) continue;
                boolean historyDeclined = false;
                for (JsonNode chunk : value.path("history")) {
                    boolean declined = false;
                    for (JsonNode error : chunk.path("errors")) {
                        if (error.path("code").asInt() == 2593109) {
                            updateJob(integration, "history", "DECLINED", 0, "2593109");
                            records.deleteByIntegrationIdAndGenerationAndSource(integration.getId(),
                                integration.getCoexistenceGeneration(), "HISTORY");
                            declined = true;
                        } else updateJob(integration, "history", "FAILED", 0, "META_HISTORY_ERROR");
                    }
                    if (declined) { historyDeclined = true; break; }
                    if (!chunk.path("errors").isEmpty()) continue;
                    for (JsonNode thread : chunk.path("threads")) {
                        for (JsonNode message : thread.path("messages")) {
                            changed += message(integration, message, thread.path("id").asText(), "HISTORY");
                        }
                    }
                    int progress = Math.max(0, Math.min(100, chunk.path("metadata").path("progress").asInt(0)));
                    updateJob(integration, "history", progress == 100 ? "COMPLETE" : "RECEIVING", progress, null);
                }
                // Delayed media details can arrive as a second history webhook.
                if (!historyDeclined && !"DECLINED".equals(historyJob.getStatus()))
                    for (JsonNode message : value.path("messages")) changed += message(integration, message, null, "HISTORY");
            } else {
                String array = "smb_message_echoes".equals(field) ? "message_echoes" : "messages";
                for (JsonNode message : value.path(array))
                    changed += message(integration, message, null, "smb_message_echoes".equals(field) ? "ECHO" : "INBOUND");
            }
        }
        return changed;
    }

    private int lifecycle(WhatsAppBusinessIntegration integration, JsonNode timestamp, JsonNode value) {
        String event = value.path("event").asText();
        if (!Set.of("PARTNER_REMOVED", "ACCOUNT_OFFBOARDED", "ACCOUNT_RECONNECTED").contains(event)) return 0;
        String phone = digits(value.path("phone_number").asText());
        if (!phone.isEmpty() && !phone.equals(digits(integration.getDisplayPhoneNumber()))) return 0;
        LocalDateTime time = timestamp(timestamp);
        if (time == null || integration.getCoexistenceStartedAt() == null
                || time.isBefore(integration.getCoexistenceStartedAt().withNano(0))
                || integration.getCoexistenceLifecycleAt() != null && time.isBefore(integration.getCoexistenceLifecycleAt())) return 0;
        // A reconnect event is not a new credential grant. Never reactivate an old token.
        if ("ACCOUNT_RECONNECTED".equals(event) && "CONNECTED".equals(integration.getStatus())) return 0;
        integration.setCoexistenceLifecycleAt(time);
        integration.setStatus("ACCOUNT_RECONNECTED".equals(event) ? "ERROR" : "DISCONNECTED");
        integration.setAccessTokenCiphertext("");
        integration.setTokenExpiresAt(LocalDateTime.now());
        integrations.save(integration);
        records.deleteByIntegrationId(integration.getId());
        for (WhatsAppCoexistenceSyncJob job : jobs.findByIntegrationIdAndGeneration(integration.getId(), integration.getCoexistenceGeneration())) {
            WhatsAppCoexistenceSyncJob locked = jobs.lockById(job.getId()).orElseThrow();
            locked.setStatus("FAILED"); locked.setErrorCode("CONNECTION_REMOVED"); locked.setUpdatedAt(LocalDateTime.now());
            jobs.save(locked);
        }
        return 1;
    }

    private int contact(WhatsAppBusinessIntegration integration, JsonNode event) {
        String action = event.path("action").asText();
        String phone = digits(event.path("contact").path("phone_number").asText());
        LocalDateTime time = timestamp(event.path("metadata").path("timestamp"));
        if (!"contact".equals(event.path("type").asText()) || !Set.of("add", "remove").contains(action)
                || phone.length() < 5 || phone.length() > 24 || time == null) return 0;
        String key = hash(phone);
        WhatsAppCoexistenceRecord record = record(integration, "CONTACT", key);
        if (record.getEventAt() != null && (record.getEventAt().isAfter(time)
                || record.getEventAt().equals(time) && (record.isDeleted() || !"remove".equals(action)))) return 0;
        boolean removed = "remove".equals(action);
        record.setPeerKey(key); record.setDeleted(removed); record.setEventAt(time);
        record.setSource("CONTACT");
        ObjectNode content = mapper.createObjectNode();
        content.put("phone", phone);
        content.put("name", bounded(event.path("contact").path("full_name").asText(), 320));
        record.setPayloadCiphertext(removed ? null : crypto.encrypt(content.toString()));
        record.setUpdatedAt(LocalDateTime.now()); records.save(record);
        return 1;
    }

    private int message(WhatsAppBusinessIntegration integration, JsonNode event, String threadPeer, String source) {
        String id = event.path("id").asText();
        String type = event.path("type").asText();
        LocalDateTime time = timestamp(event.path("timestamp"));
        if (id.isBlank() || id.length() > 200 || type.isBlank() || time == null) return 0;
        WhatsAppCoexistenceRecord record = record(integration, "MESSAGE", hash(id));
        boolean placeholder = "media_placeholder".equals(type);
        if (record.getEventAt() != null && (record.getEventAt().isAfter(time)
                || !record.isPlaceholder() && placeholder)) return 0;
        if ("ECHO".equals(record.getSource()) && "HISTORY".equals(source) && !record.isPlaceholder()) return 0;
        String ownPhone = digits(integration.getDisplayPhoneNumber());
        String peer = digits(threadPeer);
        if (peer.isEmpty()) {
            String from = digits(event.path("from").asText());
            peer = from.equals(ownPhone) ? digits(event.path("to").asText()) : from;
        }
        if (peer.isEmpty() && record.getPeerKey() == null) return 0;
        ObjectNode content = mapper.createObjectNode();
        content.put("id", id); content.put("type", bounded(type, 40)); content.put("source", source);
        content.put("from", digits(event.path("from").asText()));
        content.put("to", digits(event.path("to").asText()));
        content.put("peer", peer); content.put("outgoing", ownPhone.equals(digits(event.path("from").asText())));
        // Text-only projection for the mirror. Never follow media URLs or execute HTML.
        String text = event.path("text").path("body").asText();
        if (text.isEmpty()) text = event.path(type).path("caption").asText();
        content.put("text", bounded(text, 16000));
        if (!peer.isEmpty()) record.setPeerKey(hash(peer));
        record.setPayloadCiphertext(crypto.encrypt(content.toString()));
        record.setSource(source);
        record.setPlaceholder(placeholder); record.setDeleted(false); record.setEventAt(time);
        record.setUpdatedAt(LocalDateTime.now()); records.save(record);
        return 1;
    }

    private WhatsAppCoexistenceRecord record(WhatsAppBusinessIntegration integration, String kind, String key) {
        return records.findByIntegrationIdAndGenerationAndKindAndRecordKey(integration.getId(),
            integration.getCoexistenceGeneration(), kind, key).orElseGet(() -> {
                WhatsAppCoexistenceRecord record = new WhatsAppCoexistenceRecord();
                record.setIntegrationId(integration.getId()); record.setCompanyId(integration.getCompanyId());
                record.setGeneration(integration.getCoexistenceGeneration()); record.setKind(kind); record.setRecordKey(key);
                return record;
            });
    }

    private void updateJob(WhatsAppBusinessIntegration integration, String type, String status, int progress, String error) {
        for (WhatsAppCoexistenceSyncJob item : jobs.findByIntegrationIdAndGeneration(integration.getId(), integration.getCoexistenceGeneration())) {
            if (!type.equals(item.getSyncType())) continue;
            WhatsAppCoexistenceSyncJob job = jobs.lockById(item.getId()).orElseThrow();
            if (job.getRequestedAt() == null || "FAILED".equals(job.getStatus()) || "DECLINED".equals(job.getStatus())
                    || "COMPLETE".equals(job.getStatus()) && !"DECLINED".equals(status)) continue;
            job.setStatus(status); job.setProgress(Math.max(job.getProgress(), progress));
            job.setErrorCode(error); job.setUpdatedAt(LocalDateTime.now()); jobs.save(job);
        }
    }

    @Transactional(readOnly = true)
    public List<MirrorItem> read(Long companyId, String kind, String peerKey, int page) {
        if (page < 0 || page > 1000 || peerKey != null && !peerKey.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Geçersiz sayfa veya görüşme");
        WhatsAppBusinessIntegration integration = integrations.findByCompanyIdAndDeletedFalse(companyId).orElse(null);
        if (integration == null || !integration.isCoexistence() || !"CONNECTED".equals(integration.getStatus())) return List.of();
        var paging = PageRequest.of(page, 100);
        List<WhatsAppCoexistenceRecord> list = peerKey == null
            ? records.findByCompanyIdAndIntegrationIdAndGenerationAndKindAndDeletedFalseOrderByEventAtDesc(
                companyId, integration.getId(), integration.getCoexistenceGeneration(), kind, paging)
            : records.findByCompanyIdAndIntegrationIdAndGenerationAndKindAndPeerKeyAndDeletedFalseOrderByEventAtDesc(
                companyId, integration.getId(), integration.getCoexistenceGeneration(), kind, peerKey, paging);
        return list.stream().map(record -> {
            try {
                return new MirrorItem(record.getRecordKey(), record.getPeerKey(),
                    mapper.readTree(crypto.decrypt(record.getPayloadCiphertext())), record.getEventAt());
            } catch (Exception ex) { throw new IllegalStateException("WhatsApp aynası okunamadı"); }
        }).toList();
    }

    @Scheduled(cron = "${whatsapp.coexistence.cleanup-cron:0 35 3 * * *}")
    @Transactional
    public void purgeExpiredMirror() { records.deleteExpired(LocalDateTime.now().minusDays(retentionDays)); }

    private static String digits(String value) { return value == null ? "" : value.replaceAll("[^0-9]", ""); }
    private static String bounded(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("WhatsApp kayıt anahtarı oluşturulamadı"); }
    }
    private static LocalDateTime timestamp(JsonNode value) {
        try {
            // Match the integration's legacy LocalDateTime storage convention.
            // Production runs in UTC; local test/development zones must also compare like with like.
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value.asText())), ZoneId.systemDefault());
            return time.isAfter(LocalDateTime.now().plusMinutes(5)) ? null : time;
        } catch (RuntimeException ex) { return null; }
    }
    public record MirrorItem(String key, String peerKey, JsonNode payload, LocalDateTime eventAt) {}
}
