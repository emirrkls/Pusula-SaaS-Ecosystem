package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
@SQLDelete(sql = "UPDATE companies SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "email")
    private String email;

    // ── SaaS Fields ──────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type")
    private PlanType planType = PlanType.CIRAK;

    @Column(name = "org_code", unique = true, length = 20)
    private String orgCode;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "billing_email")
    private String billingEmail;

    @Column(name = "is_read_only")
    private Boolean isReadOnly = false;

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;

    @Column(name = "iyzico_subscription_id", length = 100)
    private String iyzicoSubscriptionId;

    // ─────────────────────────────────────────────────────────────

    @Column(name = "logo_path")
    private String logoPath;

    public Company() {
    }

    public Company(Long id, String name, String subscriptionStatus, String phone, String address, String email,
            LocalDateTime createdAt) {
        this.setId(id);
        this.name = name;
        this.subscriptionStatus = subscriptionStatus;
        this.phone = phone;
        this.address = address;
        this.email = email;
        this.setCreatedAt(createdAt);
    }

    public static CompanyBuilder builder() {
        return new CompanyBuilder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PlanType getPlanType() {
        return planType != null ? planType : PlanType.CIRAK;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public LocalDateTime getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(LocalDateTime trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public String getBillingEmail() {
        return billingEmail;
    }

    public void setBillingEmail(String billingEmail) {
        this.billingEmail = billingEmail;
    }

    public Boolean getIsReadOnly() {
        return isReadOnly != null && isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public LocalDateTime getSubscriptionExpiresAt() {
        return subscriptionExpiresAt;
    }

    public void setSubscriptionExpiresAt(LocalDateTime subscriptionExpiresAt) {
        this.subscriptionExpiresAt = subscriptionExpiresAt;
    }

    public String getIyzicoSubscriptionId() {
        return iyzicoSubscriptionId;
    }

    public void setIyzicoSubscriptionId(String iyzicoSubscriptionId) {
        this.iyzicoSubscriptionId = iyzicoSubscriptionId;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("logoUrl")
    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public static class CompanyBuilder {
        private Long id;
        private String name;
        private String subscriptionStatus;
        private String phone;
        private String address;
        private String email;
        private String logoPath;
        private LocalDateTime createdAt;

        CompanyBuilder() {
        }

        public CompanyBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CompanyBuilder name(String name) {
            this.name = name;
            return this;
        }

        public CompanyBuilder subscriptionStatus(String subscriptionStatus) {
            this.subscriptionStatus = subscriptionStatus;
            return this;
        }

        public CompanyBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CompanyBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public CompanyBuilder address(String address) {
            this.address = address;
            return this;
        }

        public CompanyBuilder email(String email) {
            this.email = email;
            return this;
        }

        public CompanyBuilder logoPath(String logoPath) {
            this.logoPath = logoPath;
            return this;
        }

        public Company build() {
            Company company = new Company(id, name, subscriptionStatus, phone, address, email, createdAt);
            company.setLogoPath(logoPath);
            return company;
        }
    }
}
