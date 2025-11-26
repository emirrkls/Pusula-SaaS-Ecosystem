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

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "email")
    private String email;

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

    public static class CompanyBuilder {
        private Long id;
        private String name;
        private String subscriptionStatus;
        private String phone;
        private String address;
        private String email;
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

        public Company build() {
            return new Company(id, name, subscriptionStatus, phone, address, email, createdAt);
        }
    }
}
