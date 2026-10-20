package com.pusula.backend.service;

import com.fasterxml.jackson.databind.*;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppCoexistenceMirrorTest {
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCoexistenceSyncJobRepository jobs;
    @Mock WhatsAppCoexistenceRecordRepository records;
    final ObjectMapper mapper = new ObjectMapper();
    final Map<String, WhatsAppCoexistenceRecord> persisted = new HashMap<>();
    WhatsAppCoexistenceMirrorService service;
    WhatsAppCredentialCrypto crypto;
    WhatsAppBusinessIntegration integration;
    long now = Instant.now().getEpochSecond();

    @BeforeEach void setup() {
        crypto = new WhatsAppCredentialCrypto(new WhatsAppIntegrationProperties("12345", "s", "c", "url",
            Base64.getEncoder().encodeToString(new byte[32]), "v26.0", "v"));
        service = new WhatsAppCoexistenceMirrorService(integrations, jobs, records, crypto, mapper, 30);
        integration = new WhatsAppBusinessIntegration(); ReflectionTestUtils.setField(integration, "id", 22L);
        integration.setCompanyId(10L); integration.setWabaId("12345"); integration.setPhoneNumberId("67890");
        integration.setDisplayPhoneNumber("+905550000000"); integration.setCoexistence(true);
        integration.setCoexistenceGeneration("g"); integration.setCoexistenceStartedAt(LocalDateTime.now().minusHours(1));
        integration.setStatus("CONNECTED"); integration.setAccessTokenCiphertext("ciphertext");
        lenient().when(integrations.lockActiveByWabaId("12345")).thenReturn(List.of(integration));
        lenient().when(records.findByIntegrationIdAndGenerationAndKindAndRecordKey(anyLong(), anyString(), anyString(), anyString()))
            .thenAnswer(i -> Optional.ofNullable(persisted.get(i.getArgument(2) + ":" + i.getArgument(3))));
        lenient().when(records.save(any())).thenAnswer(i -> {
            WhatsAppCoexistenceRecord record = i.getArgument(0);
            persisted.put(record.getKind() + ":" + record.getRecordKey(), record); return record;
        });
    }
    JsonNode value(String extra) throws Exception {
        return mapper.readTree("{\"metadata\":{\"phone_number_id\":\"67890\"}," + extra + "}");
    }
    JsonNode contact(String action, long timestamp) throws Exception {
        return value("\"state_sync\":[{\"type\":\"contact\",\"action\":\"" + action +
            "\",\"metadata\":{\"timestamp\":\"" + timestamp +
            "\"},\"contact\":{\"phone_number\":\"905551111111\",\"full_name\":\"Private Demo\"}}]");
    }
    void process(String field, JsonNode value) { service.processChange("12345", mapper.valueToTree(now), field, value); }
    WhatsAppCoexistenceSyncJob history() {
        var history = new WhatsAppCoexistenceSyncJob(); history.setId(1L); history.setSyncType("history");
        history.setStatus("ACCEPTED"); history.setRequestedAt(LocalDateTime.now().minusMinutes(1));
        when(jobs.findByIntegrationIdAndGeneration(22L, "g")).thenReturn(List.of(history));
        when(jobs.lockById(1L)).thenReturn(Optional.of(history)); return history;
    }
    @Test void contactUpsertsAreEncryptedIdempotentAndOlderUpdatesCannotUndoRemoval() throws Exception {
        process("smb_app_state_sync", contact("add", now-10));
        process("smb_app_state_sync", contact("add", now-10));
        assertEquals(1, persisted.size());
        var record = persisted.values().iterator().next();
        assertFalse(record.getPayloadCiphertext().contains("Private Demo"));
        assertTrue(crypto.decrypt(record.getPayloadCiphertext()).contains("Private Demo"));
        process("smb_app_state_sync", contact("remove", now));
        process("smb_app_state_sync", contact("add", now-10));
        assertTrue(record.isDeleted()); assertNull(record.getPayloadCiphertext());
        // A contact mirror is never a Customer or a WhatsApp opt-in.
        assertEquals("CONTACT", record.getKind()); assertEquals(10L, record.getCompanyId());
    }
    @Test void signedButForeignWabaOrPhoneCannotReachAnotherTenantMirror() throws Exception {
        service.processChange("99999", mapper.valueToTree(now), "smb_app_state_sync", contact("add", now));
        process("smb_app_state_sync", mapper.readTree("{\"metadata\":{\"phone_number_id\":\"99999\"},\"state_sync\":[]}"));
        assertTrue(persisted.isEmpty()); verify(records, never()).save(any());
    }
    @Test void historyDeclineIsExplicitAndDelayedHistoryIsNotStored() throws Exception {
        var history = history();
        process("history", value("\"history\":[{\"errors\":[{\"code\":2593109}]}]"));
        assertEquals("DECLINED", history.getStatus());
        verify(records).deleteByIntegrationIdAndGenerationAndSource(22L, "g", "HISTORY");
        process("history", historyChunk("text", 100));
        assertTrue(persisted.isEmpty());
    }
    JsonNode historyChunk(String type, int progress) throws Exception {
        return value("\"history\":[{\"metadata\":{\"phase\":0,\"chunk_order\":1,\"progress\":" + progress +
            "},\"threads\":[{\"id\":\"905551111111\",\"messages\":[{\"id\":\"wamid.demo\",\"type\":\"" +
            type + "\",\"from\":\"905551111111\",\"timestamp\":\"" + (now-100) +
            "\",\"text\":{\"body\":\"Private message\"}}]}]}]");
    }
    @Test void delayedMediaReplacesPlaceholderWithoutDuplicatingMessageAndProgressNeverRegresses() throws Exception {
        var history = history();
        process("history", historyChunk("media_placeholder", 60));
        process("history", historyChunk("text", 100));
        process("history", historyChunk("media_placeholder", 20));
        assertEquals(1, persisted.size());
        var record = persisted.values().iterator().next();
        assertFalse(record.isPlaceholder());
        assertEquals("COMPLETE", history.getStatus()); assertEquals(100, history.getProgress());
        assertEquals("text", mapper.readTree(crypto.decrypt(record.getPayloadCiphertext())).path("type").asText());
    }
    @Test void echoesAreMirroredWithoutSendingAnotherNotification() throws Exception {
        var value = value("\"message_echoes\":[{\"id\":\"wamid.echo\",\"from\":\"905550000000\",\"to\":\"905551111111\",\"type\":\"text\",\"timestamp\":\"" +
            now + "\",\"text\":{\"body\":\"Demo outgoing\"}}]");
        process("smb_message_echoes", value); process("smb_message_echoes", value);
        assertEquals(1, persisted.size());
        var record = persisted.values().iterator().next();
        assertEquals("ECHO", record.getSource());
        assertTrue(mapper.readTree(crypto.decrypt(record.getPayloadCiphertext())).path("outgoing").asBoolean());
    }
    @Test void removalDisablesCredentialsPurgesMirrorAndDoesNotAutomaticallyReconnect() throws Exception {
        process("account_update", mapper.readTree("{\"event\":\"PARTNER_REMOVED\"}"));
        assertEquals("DISCONNECTED", integration.getStatus()); assertEquals("", integration.getAccessTokenCiphertext());
        verify(records).deleteByIntegrationId(22L);
        service.processChange("12345", mapper.valueToTree(now+1), "account_update",
            mapper.readTree("{\"event\":\"ACCOUNT_RECONNECTED\"}"));
        assertEquals("ERROR", integration.getStatus());
        process("smb_app_state_sync", contact("add", now));
        assertTrue(persisted.isEmpty());
    }
    @Test void oldOrUndatedLifecycleCallbackCannotDisableFreshConnection() throws Exception {
        service.processChange("12345", mapper.valueToTree(now-7200), "account_update",
            mapper.readTree("{\"event\":\"PARTNER_REMOVED\"}"));
        service.processChange("12345", mapper.nullNode(), "account_update", mapper.readTree("{\"event\":\"PARTNER_REMOVED\"}"));
        assertEquals("CONNECTED", integration.getStatus()); verify(records, never()).deleteByIntegrationId(any());
    }
    @Test void adminReadIsScopedToAuthenticatedCompanyAndRejectsInvalidPeerKey() {
        assertEquals(List.of(), service.read(99L, "MESSAGE", null, 0));
        verify(integrations).findByCompanyIdAndDeletedFalse(99L);
        verifyNoInteractions(records);
        assertThrows(IllegalArgumentException.class, () -> service.read(10L, "MESSAGE", "../foreign", 0));
    }
}
