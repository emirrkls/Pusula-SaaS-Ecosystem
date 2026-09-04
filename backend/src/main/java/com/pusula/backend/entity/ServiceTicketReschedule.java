package com.pusula.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_ticket_reschedules")
public class ServiceTicketReschedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "service_ticket_id", nullable = false) private Long serviceTicketId;
    @Column(name = "old_scheduled_date") private LocalDateTime oldScheduledDate;
    @Column(name = "old_scheduled_end_date") private LocalDateTime oldScheduledEndDate;
    @Column(name = "new_scheduled_date", nullable = false) private LocalDateTime newScheduledDate;
    @Column(name = "new_scheduled_end_date") private LocalDateTime newScheduledEndDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ServiceTicket.WorkProgressReason reason;
    @Column(nullable = false, columnDefinition = "TEXT") private String note;
    @Column(name = "changed_by_user_id", nullable = false) private Long changedByUserId;
    @Column(name = "changed_by_name", nullable = false, length = 255) private String changedByName;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public void setCompanyId(Long value) { companyId = value; }
    public void setServiceTicketId(Long value) { serviceTicketId = value; }
    public void setOldScheduledDate(LocalDateTime value) { oldScheduledDate = value; }
    public void setOldScheduledEndDate(LocalDateTime value) { oldScheduledEndDate = value; }
    public void setNewScheduledDate(LocalDateTime value) { newScheduledDate = value; }
    public void setNewScheduledEndDate(LocalDateTime value) { newScheduledEndDate = value; }
    public void setReason(ServiceTicket.WorkProgressReason value) { reason = value; }
    public void setNote(String value) { note = value; }
    public void setChangedByUserId(Long value) { changedByUserId = value; }
    public void setChangedByName(String value) { changedByName = value; }
}
