package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_items")
@SQLDelete(sql = "UPDATE proposal_items SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class ProposalItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    public ProposalItem() {
    }

    public ProposalItem(Long id, Long companyId, Proposal proposal, String description, Integer quantity,
            BigDecimal unitPrice, BigDecimal totalPrice) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.proposal = proposal;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public static ProposalItemBuilder builder() {
        return new ProposalItemBuilder();
    }

    public Proposal getProposal() {
        return proposal;
    }

    public void setProposal(Proposal proposal) {
        this.proposal = proposal;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public static class ProposalItemBuilder {
        private Long id;
        private Long companyId;
        private Proposal proposal;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        ProposalItemBuilder() {
        }

        public ProposalItemBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProposalItemBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public ProposalItemBuilder proposal(Proposal proposal) {
            this.proposal = proposal;
            return this;
        }

        public ProposalItemBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ProposalItemBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public ProposalItemBuilder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public ProposalItemBuilder totalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }

        public ProposalItem build() {
            return new ProposalItem(id, companyId, proposal, description, quantity, unitPrice, totalPrice);
        }
    }
}
