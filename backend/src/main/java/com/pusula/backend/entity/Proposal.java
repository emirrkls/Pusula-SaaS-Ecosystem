package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "proposals")
@SQLDelete(sql = "UPDATE proposals SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Proposal extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProposalItem> items = new ArrayList<>();

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    public Proposal() {
    }

    public Proposal(Long id, Long companyId, Long customerId, List<ProposalItem> items, BigDecimal totalPrice,
            ProposalStatus status, LocalDate validUntil) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.customerId = customerId;
        this.items = items;
        this.totalPrice = totalPrice;
        this.status = status;
        this.validUntil = validUntil;
    }

    public static ProposalBuilder builder() {
        return new ProposalBuilder();
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<ProposalItem> getItems() {
        return items;
    }

    public void setItems(List<ProposalItem> items) {
        this.items = items;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public enum ProposalStatus {
        DRAFT, SENT, APPROVED, REJECTED
    }

    public static class ProposalBuilder {
        private Long id;
        private Long companyId;
        private Long customerId;
        private List<ProposalItem> items;
        private BigDecimal totalPrice;
        private ProposalStatus status;
        private LocalDate validUntil;

        ProposalBuilder() {
        }

        public ProposalBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProposalBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public ProposalBuilder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public ProposalBuilder items(List<ProposalItem> items) {
            this.items = items;
            return this;
        }

        public ProposalBuilder totalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }

        public ProposalBuilder status(ProposalStatus status) {
            this.status = status;
            return this;
        }

        public ProposalBuilder validUntil(LocalDate validUntil) {
            this.validUntil = validUntil;
            return this;
        }

        public Proposal build() {
            return new Proposal(id, companyId, customerId, items, totalPrice, status, validUntil);
        }
    }
}
