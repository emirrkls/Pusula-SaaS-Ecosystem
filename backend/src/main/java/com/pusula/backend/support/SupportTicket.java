package com.pusula.backend.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="support_tickets") @Getter @Setter
public class SupportTicket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long companyId;
    private Long userId;
    private String subject;
    private String category;
    private String status;
    private String priority;
    private String platform;
    private String appVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant userReadAt;
    private Instant adminReadAt;
    private Instant lastUserAt;
    private Instant lastAdminAt;
    private Instant closedAt;
}
