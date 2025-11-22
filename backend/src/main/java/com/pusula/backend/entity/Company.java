package com.pusula.backend.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Company() {
    }

    public Company(UUID id, String name, String subscriptionStatus, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.subscriptionStatus = subscriptionStatus;
        this.createdAt = createdAt;
    }

    public static CompanyBuilder builder() {
        return new CompanyBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class CompanyBuilder {
        private UUID id;
        private String name;
        private String subscriptionStatus;
        private LocalDateTime createdAt;

        CompanyBuilder() {
        }

        public CompanyBuilder id(UUID id) {
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
