package com.pusula.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_events", indexes = {
        @Index(name = "idx_payment_event_provider_token_hash", columnList = "provider,token_hash", unique = true)
})
public class PaymentEvent extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "purchase_token_masked", nullable = false, length = 32)
    private String purchaseTokenMasked;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentEventStatus status;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getPurchaseTokenMasked() {
        return purchaseTokenMasked;
    }

    public void setPurchaseTokenMasked(String purchaseTokenMasked) {
        this.purchaseTokenMasked = purchaseTokenMasked;
    }

    public String getExternalSubscriptionId() {
        return externalSubscriptionId;
    }

    public void setExternalSubscriptionId(String externalSubscriptionId) {
        this.externalSubscriptionId = externalSubscriptionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public PaymentEventStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentEventStatus status) {
        this.status = status;
    }
}
