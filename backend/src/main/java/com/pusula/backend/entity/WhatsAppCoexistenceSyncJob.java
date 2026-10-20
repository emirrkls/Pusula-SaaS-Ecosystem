package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_coexistence_sync_jobs")
@Getter @Setter
public class WhatsAppCoexistenceSyncJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "integration_id", nullable = false) private Long integrationId;
    @Column(nullable = false, length = 36) private String generation;
    @Column(name = "sync_type", nullable = false, length = 32) private String syncType;
    @Column(nullable = false, length = 24) private String status = "PENDING";
    @Column(name = "request_id", length = 160) private String requestId;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "requested_at") private LocalDateTime requestedAt;
    @Column(name = "deadline_at", nullable = false) private LocalDateTime deadlineAt;
    @Column(nullable = false) private int progress;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
