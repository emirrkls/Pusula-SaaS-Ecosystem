package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_parties",
        uniqueConstraints = @UniqueConstraint(name = "uq_account_party_company_type_name",
                columnNames = {"company_id", "party_type", "normalized_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountParty {
    public enum PartyType { CUSTOMER, ORGANIZATION, SUPPLIER, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 32)
    private PartyType partyType;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(name = "tax_office", length = 120)
    private String taxOffice;

    @Column(length = 64)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_person", length = 255)
    private String contactPerson;

    @Column(name = "payment_term_days")
    @Builder.Default
    private Integer paymentTermDays = 0;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
