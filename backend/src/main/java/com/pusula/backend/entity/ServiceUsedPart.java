package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Inventory inventory;

    /**
     * Keeps the historical inventory identifier readable even when the inventory
     * row was physically removed or is no longer visible through soft-delete
     * filtering. The association remains the only writable mapping.
     */
    @Column(name = "inventory_id", insertable = false, updatable = false)
    private Long inventoryId;

    @Column(name = "quantity_used", nullable = false)
    private Integer quantityUsed;

    @Column(name = "selling_price_snapshot")
    private BigDecimal sellingPriceSnapshot;

    @Column(name = "buying_price_snapshot")
    private BigDecimal buyingPriceSnapshot;

    // NULL = from main inventory, set = from specific vehicle's stock
    @Column(name = "source_vehicle_id")
    private Long sourceVehicleId;

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

    public Long getInventoryId() {
        return inventoryId;
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

    public BigDecimal getBuyingPriceSnapshot() {
        return buyingPriceSnapshot;
    }

    public void setBuyingPriceSnapshot(BigDecimal buyingPriceSnapshot) {
        this.buyingPriceSnapshot = buyingPriceSnapshot;
    }

    public Long getSourceVehicleId() {
        return sourceVehicleId;
    }

    public void setSourceVehicleId(Long sourceVehicleId) {
        this.sourceVehicleId = sourceVehicleId;
    }

    public static class ServiceUsedPartBuilder {
        private Long id;
        private Long companyId;
        private ServiceTicket serviceTicket;
        private Inventory inventory;
        private Integer quantityUsed;
        private BigDecimal sellingPriceSnapshot;
        private BigDecimal buyingPriceSnapshot;
        private Long sourceVehicleId;

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

        public ServiceUsedPartBuilder buyingPriceSnapshot(BigDecimal buyingPriceSnapshot) {
            this.buyingPriceSnapshot = buyingPriceSnapshot;
            return this;
        }

        public ServiceUsedPartBuilder sourceVehicleId(Long sourceVehicleId) {
            this.sourceVehicleId = sourceVehicleId;
            return this;
        }

        public ServiceUsedPart build() {
            ServiceUsedPart part = new ServiceUsedPart(id, companyId, serviceTicket, inventory, quantityUsed,
                    sellingPriceSnapshot);
            part.setBuyingPriceSnapshot(buyingPriceSnapshot);
            part.setSourceVehicleId(sourceVehicleId);
            return part;
        }
    }
}
