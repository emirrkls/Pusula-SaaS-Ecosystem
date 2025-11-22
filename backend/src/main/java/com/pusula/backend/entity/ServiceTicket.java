package com.pusula.backend.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_tickets")
public class ServiceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "assigned_technician_id")
    private UUID assignedTechnicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    private String description;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ServiceTicket() {
    }

    public ServiceTicket(UUID id, UUID companyId, UUID customerId, UUID assignedTechnicianId, TicketStatus status,
            LocalDateTime scheduledDate, String description, String notes, LocalDateTime createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.customerId = customerId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.description = description;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public static ServiceTicketBuilder builder() {
        return new ServiceTicketBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public void setAssignedTechnicianId(UUID assignedTechnicianId) {
        this.assignedTechnicianId = assignedTechnicianId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDateTime scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public enum TicketStatus {
        PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public static class ServiceTicketBuilder {
        private UUID id;
        private UUID companyId;
        private UUID customerId;
        private UUID assignedTechnicianId;
        private TicketStatus status;
        private LocalDateTime scheduledDate;
        private String description;
        private String notes;
        private LocalDateTime createdAt;

        ServiceTicketBuilder() {
        }

        public ServiceTicketBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ServiceTicketBuilder companyId(UUID companyId) {
            this.companyId = companyId;
            return this;
        }

        public ServiceTicketBuilder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public ServiceTicketBuilder assignedTechnicianId(UUID assignedTechnicianId) {
            this.assignedTechnicianId = assignedTechnicianId;
            return this;
        }

        public ServiceTicketBuilder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        public ServiceTicketBuilder scheduledDate(LocalDateTime scheduledDate) {
            this.scheduledDate = scheduledDate;
            return this;
        }

        public ServiceTicketBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ServiceTicketBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public ServiceTicketBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ServiceTicket build() {
            return new ServiceTicket(id, companyId, customerId, assignedTechnicianId, status, scheduledDate,
                    description, notes, createdAt);
        }
    }
}
