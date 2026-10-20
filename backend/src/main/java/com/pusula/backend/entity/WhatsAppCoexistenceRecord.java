package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_coexistence_records")
@Getter @Setter
public class WhatsAppCoexistenceRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "integration_id", nullable = false) private Long integrationId;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(nullable = false, length = 36) private String generation;
    @Column(nullable = false, length = 16) private String kind;
    @Column(length = 16) private String source;
    @Column(name = "record_key", nullable = false, length = 64) private String recordKey;
    @Column(name = "peer_key", length = 64) private String peerKey;
    @Column(name = "payload_ciphertext", columnDefinition = "TEXT") private String payloadCiphertext;
    @Column(name = "event_at", nullable = false) private LocalDateTime eventAt;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false) private boolean placeholder;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
