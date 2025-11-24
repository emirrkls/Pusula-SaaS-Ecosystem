package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@SQLDelete(sql = "UPDATE companies SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus;

    public Company() {
    }

    public Company(Long id, String name, String subscriptionStatus, LocalDateTime createdAt) {
        this.setId(id);
        this.name = name;
        this.subscriptionStatus = subscriptionStatus;
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

    public static class CompanyBuilder {
        private Long id;
        private String name;
        private String subscriptionStatus;
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

        public Company build() {
            return new Company(id, name, subscriptionStatus, createdAt);
        }
    }
}
