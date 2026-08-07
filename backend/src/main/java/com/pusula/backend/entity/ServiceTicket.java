package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Customer-facing labor/service fee. Uses the legacy labor_cost column. */
    @Column(name = "labor_cost")
    private BigDecimal laborFee;

    /** Sum of part selling-price snapshots at completion time. */
    @Column(name = "parts_total")
    private BigDecimal partsTotal;

    /** Total amount charged to the customer: parts + labor/service fee. */
    @Column(name = "invoice_total")
    private BigDecimal invoiceTotal;

    /** Portion of the invoice that remains receivable after completion. */
    @Column(name = "outstanding_amount")
    private BigDecimal outstandingAmount;

    /** Business timestamp at which the service was actually completed. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Business date on which liquid payment was collected. Null for current account. */
    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "parent_ticket_id")
    private Long parentTicketId;

    @Column(name = "is_warranty_call")
    private Boolean isWarrantyCall;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod = PaymentMethod.CASH; // Default to cash

    /**
     * True when this row represents cash collected against an earlier current
     * account sale rather than a new service sale. These rows remain part of cash
     * collection reports but must not be recognized as revenue a second time in
     * completion-based sales/profit reports.
     */
    @Column(name = "current_account_payment", nullable = false, columnDefinition = "boolean default false")
    private boolean currentAccountPayment = false;

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

    public BigDecimal getLaborFee() {
        return laborFee;
    }

    public void setLaborFee(BigDecimal laborFee) {
        this.laborFee = laborFee;
    }

    public BigDecimal getPartsTotal() {
        return partsTotal;
    }

    public void setPartsTotal(BigDecimal partsTotal) {
        this.partsTotal = partsTotal;
    }

    public BigDecimal getInvoiceTotal() {
        return invoiceTotal;
    }

    public void setInvoiceTotal(BigDecimal invoiceTotal) {
        this.invoiceTotal = invoiceTotal;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    /** Legacy tickets have no invoice_total and retain collected_amount semantics. */
    @Transient
    public BigDecimal getEffectiveInvoiceTotal() {
        if (invoiceTotal != null) {
            return invoiceTotal;
        }
        return collectedAmount != null ? collectedAmount : BigDecimal.ZERO;
    }

    @Transient
    public boolean usesStructuredPricing() {
        return invoiceTotal != null;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public void setCollectionDate(LocalDate collectionDate) {
        this.collectionDate = collectionDate;
    }

    /** Transitional fallback for rows created before completed_at was introduced. */
    @Transient
    public LocalDateTime getEffectiveCompletedAt() {
        return completedAt != null ? completedAt : getUpdatedAt();
    }

    /** Transitional fallback for rows created before collection_date was introduced. */
    @Transient
    public LocalDate getEffectiveCollectionDate() {
        if (getPaymentMethod() == PaymentMethod.CURRENT_ACCOUNT) {
            return null;
        }
        if (collectionDate != null) {
            return collectionDate;
        }
        LocalDateTime effectiveCompletion = getEffectiveCompletedAt();
        return effectiveCompletion != null ? effectiveCompletion.toLocalDate() : null;
    }

    public Long getParentTicketId() {
        return parentTicketId;
    }

    public void setParentTicketId(Long parentTicketId) {
        this.parentTicketId = parentTicketId;
    }

    public Boolean isWarrantyCall() {
        return isWarrantyCall != null ? isWarrantyCall : false;
    }

    public void setWarrantyCall(Boolean warrantyCall) {
        isWarrantyCall = warrantyCall;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod != null ? paymentMethod : PaymentMethod.CASH;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public boolean isCurrentAccountPayment() {
        return currentAccountPayment;
    }

    public void setCurrentAccountPayment(boolean currentAccountPayment) {
        this.currentAccountPayment = currentAccountPayment;
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
