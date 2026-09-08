package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_business_integrations")
public class WhatsAppBusinessIntegration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;
    @Column(name = "waba_id", nullable = false, length = 32)
    private String wabaId;
    @Column(name = "phone_number_id", nullable = false, length = 32)
    private String phoneNumberId;
    @Column(name = "display_phone_number", length = 32)
    private String displayPhoneNumber;
    @Column(name = "verified_name", length = 160)
    private String verifiedName;
    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCiphertext;
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;
    @Column(nullable = false, length = 24)
    private String status = "CONNECTED";
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getDisplayPhoneNumber() { return displayPhoneNumber; }
    public void setDisplayPhoneNumber(String displayPhoneNumber) { this.displayPhoneNumber = displayPhoneNumber; }
    public String getVerifiedName() { return verifiedName; }
    public void setVerifiedName(String verifiedName) { this.verifiedName = verifiedName; }
    public String getAccessTokenCiphertext() { return accessTokenCiphertext; }
    public void setAccessTokenCiphertext(String value) { this.accessTokenCiphertext = value; }
    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeleted() { return deleted; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
