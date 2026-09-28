package com.pusula.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@SQLDelete(sql = "UPDATE customers SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Customer extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String phone;

    private String address;

    private String coordinates; // Format: "lat,long"

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn = false;

    @Column(name = "whatsapp_opt_in_at")
    private LocalDateTime whatsappOptInAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "whatsapp_opt_in_source", length = 32)
    private WhatsAppConsentSource whatsappOptInSource;

    @Column(name = "whatsapp_opt_out_at")
    private LocalDateTime whatsappOptOutAt;

    public Customer() {
    }

    public Customer(Long id, Long companyId, String name, String phone, String address, String coordinates,
            LocalDateTime createdAt) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.coordinates = coordinates;
        this.setCreatedAt(createdAt);
    }

    public static CustomerBuilder builder() {
        return new CustomerBuilder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @JsonIgnore
    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }

    @JsonIgnore
    public LocalDateTime getWhatsappOptInAt() {
        return whatsappOptInAt;
    }

    public void setWhatsappOptInAt(LocalDateTime whatsappOptInAt) {
        this.whatsappOptInAt = whatsappOptInAt;
    }

    @JsonIgnore
    public WhatsAppConsentSource getWhatsappOptInSource() {
        return whatsappOptInSource;
    }

    public void setWhatsappOptInSource(WhatsAppConsentSource whatsappOptInSource) {
        this.whatsappOptInSource = whatsappOptInSource;
    }

    @JsonIgnore
    public LocalDateTime getWhatsappOptOutAt() {
        return whatsappOptOutAt;
    }

    public void setWhatsappOptOutAt(LocalDateTime whatsappOptOutAt) {
        this.whatsappOptOutAt = whatsappOptOutAt;
    }

    public static class CustomerBuilder {
        private Long id;
        private Long companyId;
        private String name;
        private String phone;
        private String address;
        private String coordinates;
        private LocalDateTime createdAt;

        CustomerBuilder() {
        }

        public CustomerBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CustomerBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public CustomerBuilder name(String name) {
            this.name = name;
            return this;
        }

        public CustomerBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public CustomerBuilder address(String address) {
            this.address = address;
            return this;
        }

        public CustomerBuilder coordinates(String coordinates) {
            this.coordinates = coordinates;
            return this;
        }

        public CustomerBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Customer build() {
            return new Customer(id, companyId, name, phone, address, coordinates, createdAt);
        }
    }
}
