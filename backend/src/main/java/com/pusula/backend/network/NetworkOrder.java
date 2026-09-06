package com.pusula.backend.network;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Dispatch envelope only: never a financial entry in the sending company. */
@Entity @Table(name = "service_network_orders", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"parent_company_id", "request_key"}),
        @UniqueConstraint(columnNames = "accepted_ticket_id")}) @Getter @Setter
public class NetworkOrder {
    public enum Status { SENT, ACCEPTED, REJECTED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long membershipId;
    @Column(nullable = false) private Long parentCompanyId;
    @Column(nullable = false) private Long childCompanyId;
    @Column(nullable = false) private String parentName;
    @Column(nullable = false) private String childName;
    @Column(nullable = false, length = 64) private String requestKey;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false) private String customerName;
    private String customerPhone;
    private String customerAddress;
    @Column(length = 2000) private String instruction;
    @Column(nullable = false) private LocalDateTime scheduledDate;
    private LocalDateTime scheduledEndDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private Long acceptedTicketId;
    private String reportedTicketStatus;
    private LocalDateTime reportedScheduledDate;
    private LocalDateTime reportedScheduledEndDate;
    @Column(length = 1000) private String resolutionNote;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version private long version;
}
