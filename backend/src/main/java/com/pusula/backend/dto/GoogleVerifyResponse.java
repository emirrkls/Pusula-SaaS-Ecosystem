package com.pusula.backend.dto;

public class GoogleVerifyResponse {
    private boolean verified;
    private boolean idempotentReplay;
    private String plan;
    private String subscriptionId;
    private String status;

    public GoogleVerifyResponse(boolean verified, boolean idempotentReplay, String plan, String subscriptionId, String status) {
        this.verified = verified;
        this.idempotentReplay = idempotentReplay;
        this.plan = plan;
        this.subscriptionId = subscriptionId;
        this.status = status;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public String getPlan() {
        return plan;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getStatus() {
        return status;
    }
}
