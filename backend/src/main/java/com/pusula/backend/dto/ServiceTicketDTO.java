package com.pusula.backend.dto;

import com.pusula.backend.entity.ServiceTicket.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class ServiceTicketDTO {
    private UUID id;
    private UUID customerId;
    private UUID assignedTechnicianId;
    private TicketStatus status;
    private LocalDateTime scheduledDate;
    private String description;
    private String notes;
    private LocalDateTime createdAt;

    public ServiceTicketDTO() {
    }

    public ServiceTicketDTO(UUID id, UUID customerId, UUID assignedTechnicianId, TicketStatus status,
            LocalDateTime scheduledDate, String description, String notes, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.description = description;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public static ServiceTicketDTOBuilder builder() {
        return new ServiceTicketDTOBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public static class ServiceTicketDTOBuilder {
        private UUID id;
        private UUID customerId;
        private UUID assignedTechnicianId;
        private TicketStatus status;
        private LocalDateTime scheduledDate;
        private String description;
        private String notes;
        private LocalDateTime createdAt;

        ServiceTicketDTOBuilder() {
        }

        public ServiceTicketDTOBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ServiceTicketDTOBuilder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public ServiceTicketDTOBuilder assignedTechnicianId(UUID assignedTechnicianId) {
            this.assignedTechnicianId = assignedTechnicianId;
            return this;
        }

        public ServiceTicketDTOBuilder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        public ServiceTicketDTOBuilder scheduledDate(LocalDateTime scheduledDate) {
            this.scheduledDate = scheduledDate;
            return this;
        }

        public ServiceTicketDTOBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ServiceTicketDTOBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public ServiceTicketDTOBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ServiceTicketDTO build() {
            return new ServiceTicketDTO(id, customerId, assignedTechnicianId, status, scheduledDate, description, notes,
                    createdAt);
        }
    }
}
