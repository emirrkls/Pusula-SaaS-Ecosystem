package com.pusula.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Minimal durable inbox; never stores the raw Pub/Sub payload or a plaintext purchase token. */
@Entity
@Table(name = "google_play_notifications")
public class GooglePlayNotification {
    @Id @Column(length = 128) private String id;
    @Version private Long version;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(length = 64) private String tokenHash;
    @Column(columnDefinition = "TEXT") private String encryptedToken;
    @Column(nullable = false, length = 48) private String type;
    @Column(nullable = false) private long eventTimeMillis;
    @Column(nullable = false, length = 32) private String processingStatus;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private LocalDateTime receivedAt;
    @Column(nullable = false) private LocalDateTime nextRetryAt;
    private LocalDateTime processedAt;
    protected GooglePlayNotification() {}
    public GooglePlayNotification(String id, String payloadHash, String tokenHash, String encryptedToken,
            String type, long eventTimeMillis, LocalDateTime now) {
        this.id = id; this.payloadHash = payloadHash; this.tokenHash = tokenHash; this.encryptedToken = encryptedToken;
        this.type = type; this.eventTimeMillis = eventTimeMillis; receivedAt = now; nextRetryAt = now;
        processingStatus = encryptedToken == null ? "IGNORED" : "PENDING";
        if (encryptedToken == null) processedAt = now;
    }
    public String getId() { return id; }
    public String getPayloadHash() { return payloadHash; }
    public String getEncryptedToken() { return encryptedToken; }
    public String getProcessingStatus() { return processingStatus; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public int getAttempts() { return attempts; }
    public void retry(String status, LocalDateTime now) {
        processingStatus = status; attempts++;
        nextRetryAt = now.plusSeconds(Math.min(21600L, 60L << Math.min(attempts - 1, 9)));
    }
    public void finish(String status, LocalDateTime now) {
        processingStatus = status; processedAt = now; encryptedToken = null;
    }
}
