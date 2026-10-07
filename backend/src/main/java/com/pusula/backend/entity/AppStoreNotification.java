package com.pusula.backend.entity;

import com.pusula.backend.service.AppleAppStoreVerificationService.AppleNotificationResult;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Only verified, minimal snapshots are stored: no JWS, raw transaction IDs or credentials. */
@Entity
@Table(name = "app_store_notifications")
public class AppStoreNotification {
    @Id @Column(length = 36) private String id;
    @Version private Long version;
    @Column(nullable = false, length = 64) private String type;
    @Column(length = 64) private String subtype;
    @Column(nullable = false, length = 16) private String environment;
    @Column(nullable = false) private Long signedDate;
    @Column(length = 64) private String originalTransactionHash;
    @Column(length = 64) private String transactionHash;
    @Enumerated(EnumType.STRING) @Column(length = 16) private PlanType planType;
    private LocalDateTime purchaseDate;
    private LocalDateTime expiresDate;
    private LocalDateTime graceExpiresDate;
    @Column(nullable = false) private boolean revoked;
    @Column(length = 32) private String subscriptionStatus;
    @Column(nullable = false, length = 32) private String processingStatus;
    @Column(nullable = false) private LocalDateTime receivedAt;
    @Column(nullable = false) private LocalDateTime nextRetryAt;
    private LocalDateTime processedAt;
    private Long companyId;

    protected AppStoreNotification() {}

    public AppStoreNotification(AppleNotificationResult result, String originalHash, String transactionHash,
            LocalDateTime receivedAt) {
        id = result.notificationId(); type = result.type(); subtype = result.subtype();
        environment = result.environment(); signedDate = result.signedDate();
        originalTransactionHash = originalHash; this.transactionHash = transactionHash;
        if (result.transaction() != null) {
            planType = result.transaction().planType(); purchaseDate = result.transaction().purchaseDate();
            expiresDate = result.transaction().expiresDate();
        }
        graceExpiresDate = result.graceExpiresDate(); revoked = result.revoked();
        subscriptionStatus = result.status(); processingStatus = "WAITING_OWNER"; this.receivedAt = receivedAt;
        nextRetryAt = receivedAt;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getSubtype() { return subtype; }
    public String getEnvironment() { return environment; }
    public Long getSignedDate() { return signedDate; }
    public String getOriginalTransactionHash() { return originalTransactionHash; }
    public String getTransactionHash() { return transactionHash; }
    public PlanType getPlanType() { return planType; }
    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public LocalDateTime getExpiresDate() { return expiresDate; }
    public LocalDateTime getGraceExpiresDate() { return graceExpiresDate; }
    public boolean isRevoked() { return revoked; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public String getProcessingStatus() { return processingStatus; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public Long getCompanyId() { return companyId; }
    public void retryAfter(LocalDateTime value) { nextRetryAt = value; }
    public void finish(String status, Long owner, LocalDateTime now) {
        processingStatus = status; companyId = owner; processedAt = now;
    }
}
