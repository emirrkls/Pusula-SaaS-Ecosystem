package com.pusula.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pusula.backend.entity.GooglePlayNotification;
import com.pusula.backend.repository.GooglePlayNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class GooglePlayNotificationService {
    private static final Set<String> RETRYABLE = Set.of("PENDING", "RETRY", "WAITING_OWNER");
    private final GooglePlayNotificationSettings settings;
    private final GooglePlayTokenCrypto crypto;
    private final GooglePlayNotificationRepository inbox;
    private final SubscriptionService subscriptions;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired
    public GooglePlayNotificationService(GooglePlayNotificationSettings settings, GooglePlayTokenCrypto crypto,
            GooglePlayNotificationRepository inbox, SubscriptionService subscriptions, ObjectMapper json) {
        this(settings, crypto, inbox, subscriptions, json, Clock.systemUTC());
    }
    GooglePlayNotificationService(GooglePlayNotificationSettings settings, GooglePlayTokenCrypto crypto,
            GooglePlayNotificationRepository inbox, SubscriptionService subscriptions, ObjectMapper json, Clock clock) {
        this.settings = settings; this.crypto = crypto; this.inbox = inbox; this.subscriptions = subscriptions;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION); this.clock = clock;
    }
    @Transactional
    public void receive(byte[] body) {
        if (!settings.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        try { crypto.requireConfigured(); }
        catch (IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
        var event = parse(body);
        String hash = GooglePlayAccountBinding.sha256(new String(event.payload(), StandardCharsets.UTF_8));
        var previous = inbox.findById(event.id());
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getPayloadHash())) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return;
        }
        String encrypted = event.token() == null ? null : crypto.encrypt(event.token(), event.id());
        inbox.saveAndFlush(new GooglePlayNotification(event.id(), hash,
                event.token() == null ? null : GooglePlayAccountBinding.sha256(event.token()), encrypted,
                event.type(), event.time(), now()));
        // 204 is returned only after transaction commit. Unique-key races remain non-2xx and are retried by Pub/Sub.
    }
    private Event parse(byte[] body) {
        if (body == null || body.length == 0 || body.length > 32768) throw badRequest();
        try {
            JsonNode root = json.readTree(body);
            if (!settings.subscription().equals(root.path("subscription").asText())) throw badRequest();
            JsonNode message = root.path("message");
            String id = message.path("messageId").asText();
            String data = message.path("data").asText();
            if (!id.matches("[A-Za-z0-9_-]{1,128}") || data.isBlank() || data.length() > 22000) throw badRequest();
            byte[] payload = Base64.getDecoder().decode(data);
            if (payload.length > 16384) throw badRequest();
            JsonNode developer = json.readTree(payload);
            if (!settings.packageName().equals(developer.path("packageName").asText())) throw badRequest();
            long time = Long.parseLong(developer.path("eventTimeMillis").asText());
            if (time < 0 || time > clock.millis() + 300000) throw badRequest();
            String kind = null;
            for (String name : List.of("subscriptionNotification", "voidedPurchaseNotification", "testNotification",
                    "oneTimeProductNotification", "oneTimeProductPurchaseNotification", "pendingPurchaseRefundNotification")) {
                if (developer.has(name)) {
                    if (kind != null || !developer.get(name).isObject()) throw badRequest();
                    kind = name;
                }
            }
            if (kind == null) throw badRequest();
            JsonNode notification = developer.get(kind);
            boolean subscription = "subscriptionNotification".equals(kind);
            boolean voided = "voidedPurchaseNotification".equals(kind);
            if (subscription && (!notification.path("notificationType").canConvertToInt()
                    || notification.path("notificationType").asInt() < 1)) throw badRequest();
            String token = subscription || voided ? notification.path("purchaseToken").asText() : null;
            if (token != null && (token.isBlank() || token.length() > 4096)) throw badRequest();
            return new Event(id, payload, token, subscription ? "SUBSCRIPTION_" + notification.path("notificationType").asInt()
                    : kind, time);
        } catch (Exception ex) { throw badRequest(); }
    }
    @Transactional
    public void process(String id) {
        if (!settings.configured()) return;
        var row = inbox.lockById(id).orElse(null);
        LocalDateTime now = now();
        if (row == null || !RETRYABLE.contains(row.getProcessingStatus()) || row.getNextRetryAt().isAfter(now)) return;
        if (row.getReceivedAt().isBefore(now.minusDays(30))) {
            row.finish("EXPIRED_RETRY", now);
        } else {
            // An exception rolls back this attempt; the durable row remains due for the next tick.
            String status = subscriptions.reconcileGoogleNotification(crypto.decrypt(row.getEncryptedToken(), row.getId()));
            if ("RETRY".equals(status) || "WAITING_OWNER".equals(status)) row.retry(status, now);
            else row.finish(status, now);
        }
        inbox.save(row);
    }
    @Transactional
    public void cleanup() { inbox.deleteFinishedBefore(now().minusDays(30)); }
    @Transactional
    public void defer(String id) {
        inbox.lockById(id).filter(row -> RETRYABLE.contains(row.getProcessingStatus()))
                .ifPresent(row -> { row.retry("RETRY", now()); inbox.save(row); });
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private ResponseStatusException badRequest() { return new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    private record Event(String id, byte[] payload, String token, String type, long time) {}
}
