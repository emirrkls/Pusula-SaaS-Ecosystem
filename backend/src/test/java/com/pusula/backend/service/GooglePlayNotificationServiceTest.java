package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.GooglePlayNotification;
import com.pusula.backend.repository.GooglePlayNotificationRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GooglePlayNotificationServiceTest {
    static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    static final LocalDateTime TODAY = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    static final String SUB = "projects/pusula-test/subscriptions/play-test";
    final GooglePlayNotificationSettings settings = new GooglePlayNotificationSettings(true,
            "https://example.test/api/public/google/play-notifications", "push@example.test", SUB, "com.pusula.service");
    final GooglePlayTokenCrypto crypto = new GooglePlayTokenCrypto(Base64.getEncoder().encodeToString(new byte[32]));
    final GooglePlayNotificationRepository inbox = mock(GooglePlayNotificationRepository.class);
    final SubscriptionService subscriptions = mock(SubscriptionService.class);
    final Map<String, GooglePlayNotification> rows = new HashMap<>();
    GooglePlayNotificationService service;
    @BeforeEach void setup() {
        service = service(NOW);
        when(inbox.findById(anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        when(inbox.lockById(anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        when(inbox.saveAndFlush(any())).thenAnswer(i -> {
            GooglePlayNotification row = i.getArgument(0); rows.put(row.getId(), row); return row;
        });
    }
    GooglePlayNotificationService service(Instant now) {
        return new GooglePlayNotificationService(settings, crypto, inbox, subscriptions, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }
    String payload(String kind) {
        return "{\"version\":\"1.0\",\"packageName\":\"com.pusula.service\",\"eventTimeMillis\":\"" + NOW.toEpochMilli()
                + "\",\"" + kind + "\":{\"notificationType\":2,\"purchaseToken\":\"secret-receipt\"}}";
    }
    byte[] envelope(String id, String payload) {
        return ("{\"subscription\":\"" + SUB + "\",\"message\":{\"messageId\":\"" + id + "\",\"data\":\""
                + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "\"}}").getBytes(StandardCharsets.UTF_8);
    }
    @Test void persistsEncryptedMinimalInboxBeforeDoingAnyApiWork() {
        service.receive(envelope("msg-1", payload("subscriptionNotification")));
        var row = rows.get("msg-1");
        assertEquals("PENDING", row.getProcessingStatus());
        assertFalse(row.getEncryptedToken().contains("secret-receipt"));
        assertEquals("secret-receipt", crypto.decrypt(row.getEncryptedToken(), "msg-1"));
        verifyNoInteractions(subscriptions);
    }
    @Test void duplicateMessageDoesNotProcessOrStoreTwice() {
        byte[] body = envelope("msg-1", payload("subscriptionNotification"));
        service.receive(body); service.receive(body);
        verify(inbox, times(1)).saveAndFlush(any()); verifyNoInteractions(subscriptions);
    }
    @Test void conflictingDuplicateMessageIsRejected() {
        service.receive(envelope("msg-1", payload("subscriptionNotification")));
        var ex = assertThrows(ResponseStatusException.class, () -> service.receive(envelope("msg-1",
                payload("subscriptionNotification").replace("notificationType\":2", "notificationType\":3"))));
        assertEquals(409, ex.getStatusCode().value());
    }
    @Test void wrongPackageOrSubscriptionCannotEnterInbox() {
        assertThrows(ResponseStatusException.class, () -> service.receive(envelope("x", payload("subscriptionNotification")
                .replace("com.pusula.service", "com.other.app"))));
        byte[] wrong = new String(envelope("x", payload("subscriptionNotification")), StandardCharsets.UTF_8)
                .replace(SUB, "projects/other/subscriptions/other").getBytes(StandardCharsets.UTF_8);
        assertThrows(ResponseStatusException.class, () -> service.receive(wrong)); assertTrue(rows.isEmpty());
    }
    @Test void invalidBase64OversizeDuplicateFieldsOrMultipleEventsFailClosed() {
        assertThrows(ResponseStatusException.class, () -> service.receive(new byte[32769]));
        assertThrows(ResponseStatusException.class, () -> service.receive("{\"message\":{},\"message\":{}}".getBytes()));
        String doubleEvent = payload("subscriptionNotification").replace("\"version\":\"1.0\"", "\"testNotification\":{},\"version\":\"1.0\"");
        assertThrows(ResponseStatusException.class, () -> service.receive(envelope("x", doubleEvent)));
        assertThrows(ResponseStatusException.class, () -> service.receive(("{\"subscription\":\"" + SUB
                + "\",\"message\":{\"messageId\":\"x\",\"data\":\"!!\"}}").getBytes()));
        assertTrue(rows.isEmpty());
    }
    @Test void testNotificationNeverChangesEntitlements() {
        service.receive(envelope("test", payload("testNotification"))); service.process("test");
        assertEquals("IGNORED", rows.get("test").getProcessingStatus());
        assertNull(rows.get("test").getEncryptedToken()); verifyNoInteractions(subscriptions);
    }
    @Test void voidedPurchaseIsReconciledWithGoogleNotBlindlyRevoked() {
        service.receive(envelope("v", payload("voidedPurchaseNotification")));
        when(subscriptions.reconcileGoogleNotification("secret-receipt")).thenReturn("APPLIED"); service.process("v");
        assertEquals("APPLIED", rows.get("v").getProcessingStatus()); assertNull(rows.get("v").getEncryptedToken());
    }
    @Test void missingOwnerAndTransientFailuresRemainDurableWithBackoff() {
        service.receive(envelope("x", payload("subscriptionNotification")));
        when(subscriptions.reconcileGoogleNotification("secret-receipt")).thenReturn("WAITING_OWNER", "RETRY", "APPLIED");
        service.process("x"); var row = rows.get("x");
        assertEquals("WAITING_OWNER", row.getProcessingStatus()); assertEquals(TODAY.plusMinutes(1), row.getNextRetryAt());
        service.process("x"); verify(subscriptions, times(1)).reconcileGoogleNotification(anyString());
        service(NOW.plusSeconds(60)).process("x"); assertEquals(TODAY.plusMinutes(3), row.getNextRetryAt());
        service(NOW.plusSeconds(180)).process("x"); assertEquals("APPLIED", row.getProcessingStatus());
        assertNull(row.getEncryptedToken());
    }
    @Test void retriesStopAfterThirtyDaysAndRemoveSecret() {
        service.receive(envelope("x", payload("subscriptionNotification")));
        service(NOW.plusSeconds(31 * 86400L)).process("x");
        assertEquals("EXPIRED_RETRY", rows.get("x").getProcessingStatus());
        assertNull(rows.get("x").getEncryptedToken()); verifyNoInteractions(subscriptions);
    }
    @Test void finishedRowsAreNotAppliedAgainAndCleanupRetainsThirtyDays() {
        service.receive(envelope("x", payload("subscriptionNotification")));
        when(subscriptions.reconcileGoogleNotification("secret-receipt")).thenReturn("IGNORED_STALE");
        service.process("x"); service.process("x");
        verify(subscriptions, times(1)).reconcileGoogleNotification(anyString());
        service.cleanup(); verify(inbox).deleteFinishedBefore(TODAY.minusDays(30));
    }
    @Test void missingKeyOrDisabledConfigurationDoesNotAcknowledgeOrPersist() {
        var disabled = new GooglePlayNotificationService(new GooglePlayNotificationSettings(false, "", "", "", ""),
                crypto, inbox, subscriptions, new ObjectMapper());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> disabled.receive(envelope("x", payload("subscriptionNotification")))).getStatusCode().value());
        var noKey = new GooglePlayNotificationService(settings, new GooglePlayTokenCrypto(""), inbox, subscriptions, new ObjectMapper());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> noKey.receive(envelope("x", payload("subscriptionNotification")))).getStatusCode().value());
        assertTrue(rows.isEmpty());
    }
    @Test void ciphertextUsesRandomIvAndRejectsTamperingAndMessageSwap() {
        String one = crypto.encrypt("receipt", "a"), two = crypto.encrypt("receipt", "a");
        assertNotEquals(one, two); assertEquals("receipt", crypto.decrypt(one, "a"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(one, "b"));
        byte[] bytes = Base64.getDecoder().decode(one); bytes[15] ^= 1;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(Base64.getEncoder().encodeToString(bytes), "a"));
    }
}
