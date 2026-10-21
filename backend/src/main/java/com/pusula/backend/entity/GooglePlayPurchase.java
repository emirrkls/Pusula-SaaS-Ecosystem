package com.pusula.backend.entity;

import com.pusula.backend.service.GooglePlayVerificationService.GoogleVerificationResult;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Encrypted receipt vault for missed-notification recovery and server acknowledgement. */
@Entity @Table(name="google_play_purchases")
public class GooglePlayPurchase {
    @Id @Column(length=64) private String id;
    @Version private Long version;
    @Column(nullable=false) private Long companyId;
    @Column(columnDefinition="TEXT") private String encryptedToken;
    @Column(nullable=false, length=32) private String productId;
    @Column(length=64) private String basePlanId;
    @Column(length=32) private String deferredProductId;
    @Column(length=64) private String state;
    @Column(length=48) private String acknowledgementState;
    private LocalDateTime expiresAt;
    @Column(nullable=false) private LocalDateTime verifiedAt;
    @Column(nullable=false) private LocalDateTime nextCheckAt;
    @Column(nullable=false) private boolean retired;
    @Column(nullable=false) private int attempts;
    protected GooglePlayPurchase() {}
    public GooglePlayPurchase(String id, Long companyId) { this.id=id; this.companyId=companyId; }
    public void verified(String encrypted, GoogleVerificationResult result, LocalDateTime now) {
        if (retired) throw new IllegalStateException("PLAY_RECEIPT_RETIRED");
        encryptedToken=encrypted; productId=result.productId(); basePlanId=result.basePlanId();
        deferredProductId=result.deferredProductId(); state=result.state(); acknowledgementState=result.acknowledgementState();
        expiresAt=LocalDateTime.ofInstant(result.expiresAt(), ZoneOffset.UTC); verifiedAt=now; attempts=0;
        LocalDateTime nearExpiry = expiresAt.minusMinutes(1);
        nextCheckAt = nearExpiry.isBefore(now.plusHours(6)) ? nearExpiry : now.plusHours(6);
        if (!nextCheckAt.isAfter(now)) nextCheckAt=now.plusMinutes(1);
        if ("ACKNOWLEDGEMENT_STATE_PENDING".equals(acknowledgementState) && result.valid()) nextCheckAt=now;
    }
    public void defer(LocalDateTime now) {
        attempts++; nextCheckAt=now.plusSeconds(Math.min(21600L,60L << Math.min(attempts-1,9)));
    }
    public void acknowledge() { acknowledgementState="ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"; }
    public void retire() { retired=true; encryptedToken=null; }
    public String getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getEncryptedToken() { return encryptedToken; }
    public String getProductId() { return productId; }
    public String getBasePlanId() { return basePlanId; }
    public String getDeferredProductId() { return deferredProductId; }
    public String getState() { return state; }
    public String getAcknowledgementState() { return acknowledgementState; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getNextCheckAt() { return nextCheckAt; }
    public boolean isRetired() { return retired; }
}
