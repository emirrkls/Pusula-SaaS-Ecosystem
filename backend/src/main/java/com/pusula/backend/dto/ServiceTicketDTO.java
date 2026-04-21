package com.pusula.backend.dto;

import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ServiceTicketDTO {
    private Long id;
    private Long customerId;
    private Long assignedTechnicianId;
    private TicketStatus status;
    private LocalDateTime scheduledDate;
    private String description;
    private String notes;
    private BigDecimal collectedAmount;
    private LocalDateTime createdAt;
    private Long parentTicketId;
    private boolean isWarrantyCall;
    private String customerName; // Customer full name for frontend display
    private PaymentMethod paymentMethod;
    // Enriched fields for mobile technician view
    private String customerPhone;
    private String customerAddress;
    private String customerCoordinates;
    private java.math.BigDecimal customerBalance; // Outstanding cari balance
    private String assignedTechnicianName;

    public ServiceTicketDTO() {
    }

    public ServiceTicketDTO(Long id, Long customerId, Long assignedTechnicianId, TicketStatus status,
            LocalDateTime scheduledDate, String description, String notes, BigDecimal collectedAmount,
            LocalDateTime createdAt, Long parentTicketId, boolean isWarrantyCall, String customerName,
            PaymentMethod paymentMethod) {
        this.id = id;
        this.customerId = customerId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.description = description;
        this.notes = notes;
        this.collectedAmount = collectedAmount;
        this.createdAt = createdAt;
        this.parentTicketId = parentTicketId;
        this.isWarrantyCall = isWarrantyCall;
        this.customerName = customerName;
        this.paymentMethod = paymentMethod;
    }

    public static ServiceTicketDTOBuilder builder() {
        return new ServiceTicketDTOBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }

    public String getCustomerCoordinates() { return customerCoordinates; }
    public void setCustomerCoordinates(String customerCoordinates) { this.customerCoordinates = customerCoordinates; }

    public java.math.BigDecimal getCustomerBalance() { return customerBalance; }
    public void setCustomerBalance(java.math.BigDecimal customerBalance) { this.customerBalance = customerBalance; }

    public String getAssignedTechnicianName() { return assignedTechnicianName; }
    public void setAssignedTechnicianName(String assignedTechnicianName) { this.assignedTechnicianName = assignedTechnicianName; }

    public static class ServiceTicketDTOBuilder {
        private Long id;
        private Long customerId;
        private Long assignedTechnicianId;
        private TicketStatus status;
        private LocalDateTime scheduledDate;
        private String description;
        private String notes;
        private BigDecimal collectedAmount;
        private LocalDateTime createdAt;
        private Long parentTicketId;
        private boolean isWarrantyCall;
        private String customerName;
        private PaymentMethod paymentMethod;

        ServiceTicketDTOBuilder() {
        }

        public ServiceTicketDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServiceTicketDTOBuilder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public ServiceTicketDTOBuilder assignedTechnicianId(Long assignedTechnicianId) {
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

        public ServiceTicketDTOBuilder collectedAmount(BigDecimal collectedAmount) {
            this.collectedAmount = collectedAmount;
            return this;
        }

        public ServiceTicketDTOBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ServiceTicketDTOBuilder parentTicketId(Long parentTicketId) {
            this.parentTicketId = parentTicketId;
            return this;
        }

        public ServiceTicketDTOBuilder isWarrantyCall(boolean isWarrantyCall) {
            this.isWarrantyCall = isWarrantyCall;
            return this;
        }

        public ServiceTicketDTOBuilder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public ServiceTicketDTOBuilder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public ServiceTicketDTO build() {
            return new ServiceTicketDTO(id, customerId, assignedTechnicianId, status, scheduledDate, description, notes,
                    collectedAmount, createdAt, parentTicketId, isWarrantyCall, customerName, paymentMethod);
        }
    }
}
