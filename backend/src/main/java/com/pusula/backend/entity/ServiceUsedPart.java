package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;

@Entity
@Table(name = "service_used_parts")
@SQLDelete(sql = "UPDATE service_used_parts SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class ServiceUsedPart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private ServiceTicket serviceTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "quantity_used", nullable = false)
    private Integer quantityUsed;

    @Column(name = "selling_price_snapshot")
    private BigDecimal sellingPriceSnapshot;

    public ServiceUsedPart() {
    }

    public ServiceUsedPart(Long id, Long companyId, ServiceTicket serviceTicket, Inventory inventory,
            Integer quantityUsed, BigDecimal sellingPriceSnapshot) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.serviceTicket = serviceTicket;
        this.inventory = inventory;
        this.quantityUsed = quantityUsed;
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public static ServiceUsedPartBuilder builder() {
        return new ServiceUsedPartBuilder();
    }

    public ServiceTicket getServiceTicket() {
        return serviceTicket;
    }

    public void setServiceTicket(ServiceTicket serviceTicket) {
        this.serviceTicket = serviceTicket;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Integer getQuantityUsed() {
        return quantityUsed;
    }

    public void setQuantityUsed(Integer quantityUsed) {
        this.quantityUsed = quantityUsed;
    }

    public BigDecimal getSellingPriceSnapshot() {
        return sellingPriceSnapshot;
    }

    public void setSellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public static class ServiceUsedPartBuilder {
        private Long id;
        private Long companyId;
        private ServiceTicket serviceTicket;
        private Inventory inventory;
        private Integer quantityUsed;
        private BigDecimal sellingPriceSnapshot;

        ServiceUsedPartBuilder() {
        }

        public ServiceUsedPartBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServiceUsedPartBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public ServiceUsedPartBuilder serviceTicket(ServiceTicket serviceTicket) {
            this.serviceTicket = serviceTicket;
            return this;
        }

        public ServiceUsedPartBuilder inventory(Inventory inventory) {
            this.inventory = inventory;
            return this;
        }

        public ServiceUsedPartBuilder quantityUsed(Integer quantityUsed) {
            this.quantityUsed = quantityUsed;
            return this;
        }

        public ServiceUsedPartBuilder sellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
            this.sellingPriceSnapshot = sellingPriceSnapshot;
            return this;
        }

        public ServiceUsedPart build() {
            return new ServiceUsedPart(id, companyId, serviceTicket, inventory, quantityUsed, sellingPriceSnapshot);
        }
    }
}
