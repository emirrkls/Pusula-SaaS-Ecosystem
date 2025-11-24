package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_tickets")
@SQLDelete(sql = "UPDATE service_tickets SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class ServiceTicket extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    private String description;

    private String notes;

    @Column(name = "collected_amount")
    private BigDecimal collectedAmount;

    @Column(name = "parent_ticket_id")
    private Long parentTicketId;

    @Column(name = "is_warranty_call")
    private boolean isWarrantyCall = false;

    public ServiceTicket() {
    }

    public ServiceTicket(Long id, Long companyId, Long customerId, Long assignedTechnicianId, TicketStatus status,
            LocalDateTime scheduledDate, String description, String notes, BigDecimal collectedAmount,
            LocalDateTime createdAt) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.customerId = customerId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.description = description;
        this.notes = notes;
        this.collectedAmount = collectedAmount;
        this.setCreatedAt(createdAt);
    }

    public static ServiceTicketBuilder builder() {
        return new ServiceTicketBuilder();
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public void setAssignedTechnicianId(Long assignedTechnicianId) {
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

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public Long getParentTicketId() {
        return parentTicketId;
    }

    public void setParentTicketId(Long parentTicketId) {
        this.parentTicketId = parentTicketId;
    }

    public boolean isWarrantyCall() {
        return isWarrantyCall;
    }

    public void setWarrantyCall(boolean warrantyCall) {
        isWarrantyCall = warrantyCall;
    }

    public enum TicketStatus {
        PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public static class ServiceTicketBuilder {
        private Long id;
        private Long companyId;
        private Long customerId;
        private Long assignedTechnicianId;
        private TicketStatus status;
        private LocalDateTime scheduledDate;
        private String description;
        private String notes;
        private BigDecimal collectedAmount;
        private LocalDateTime createdAt;

        ServiceTicketBuilder() {
        }

        public ServiceTicketBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServiceTicketBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public ServiceTicketBuilder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public ServiceTicketBuilder assignedTechnicianId(Long assignedTechnicianId) {
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

        public ServiceTicketBuilder collectedAmount(BigDecimal collectedAmount) {
            this.collectedAmount = collectedAmount;
            return this;
        }

        public ServiceTicketBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ServiceTicket build() {
            return new ServiceTicket(id, companyId, customerId, assignedTechnicianId, status, scheduledDate,
                    description, notes, collectedAmount, createdAt);
        }
    }
}
