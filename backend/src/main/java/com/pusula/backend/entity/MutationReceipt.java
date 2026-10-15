package com.pusula.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Minimal, bounded-lived receipt; deliberately not a business entity or audit log. */
@Entity
@Table(name = "mutation_receipts", uniqueConstraints = @UniqueConstraint(
        name = "uq_mutation_receipt_actor_key", columnNames = {"company_id", "user_id", "request_key"}))
public class MutationReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "request_key", nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 80) private String operation;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(name = "resource_id") private Long resourceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long value) { resourceId = value; }
    public String getOperation() { return operation; }
    public String getFingerprint() { return fingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
