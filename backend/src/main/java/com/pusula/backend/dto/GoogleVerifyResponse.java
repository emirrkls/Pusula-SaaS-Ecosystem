package com.pusula.backend.dto;

public class GoogleVerifyResponse {
    private boolean verified;
    private boolean idempotentReplay;
    private String plan;
    private String subscriptionId;
    private String status;
    private String scheduledPlan;
    private String basePlanId;
    private String receiptHash;

    public GoogleVerifyResponse(boolean verified, boolean idempotentReplay, String plan, String subscriptionId, String status) {
        this(verified, idempotentReplay, plan, subscriptionId, status, null, null, null);
    }
    public GoogleVerifyResponse(boolean verified, boolean idempotentReplay, String plan, String subscriptionId, String status,
            String scheduledPlan, String basePlanId, String receiptHash) {
        this.verified = verified;
        this.idempotentReplay = idempotentReplay;
        this.plan = plan;
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.scheduledPlan=scheduledPlan; this.basePlanId=basePlanId; this.receiptHash=receiptHash;
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
    public String getScheduledPlan() { return scheduledPlan; }
    public String getBasePlanId() { return basePlanId; }
    public String getReceiptHash() { return receiptHash; }
}
