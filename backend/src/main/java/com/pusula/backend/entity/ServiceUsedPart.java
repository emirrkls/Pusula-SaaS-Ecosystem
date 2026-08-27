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

    @Column(name = "quantity_used", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 16,
            columnDefinition = "VARCHAR(16) DEFAULT 'ADET'")
    private InventoryUnit unitOfMeasure = InventoryUnit.ADET;

    @Column(name = "selling_price_snapshot")
    private BigDecimal sellingPriceSnapshot;

    @Column(name = "buying_price_snapshot")
    private BigDecimal buyingPriceSnapshot;

    // NULL = from main inventory, set = from specific vehicle's stock
    @Column(name = "source_vehicle_id")
    private Long sourceVehicleId;

    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    public ServiceUsedPart() {
    }

    public ServiceUsedPart(Long id, Long companyId, ServiceTicket serviceTicket, Inventory inventory,
            BigDecimal quantityUsed, BigDecimal sellingPriceSnapshot) {
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

    public BigDecimal getQuantityUsed() {
        return quantityUsed;
    }

    public void setQuantityUsed(BigDecimal quantityUsed) {
        this.quantityUsed = quantityUsed;
    }

    public InventoryUnit getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(InventoryUnit unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure == null ? InventoryUnit.ADET : unitOfMeasure;
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

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public static class ServiceUsedPartBuilder {
        private Long id;
        private Long companyId;
        private ServiceTicket serviceTicket;
        private Inventory inventory;
        private BigDecimal quantityUsed;
        private InventoryUnit unitOfMeasure = InventoryUnit.ADET;
        private BigDecimal sellingPriceSnapshot;
        private BigDecimal buyingPriceSnapshot;
        private Long sourceVehicleId;
        private String clientRequestId;

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

        public ServiceUsedPartBuilder quantityUsed(BigDecimal quantityUsed) {
            this.quantityUsed = quantityUsed;
            return this;
        }

        public ServiceUsedPartBuilder quantityUsed(Integer quantityUsed) {
            return quantityUsed(quantityUsed == null ? null : BigDecimal.valueOf(quantityUsed));
        }

        public ServiceUsedPartBuilder unitOfMeasure(InventoryUnit unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
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

        public ServiceUsedPartBuilder clientRequestId(String clientRequestId) {
            this.clientRequestId = clientRequestId;
            return this;
        }

        public ServiceUsedPart build() {
            ServiceUsedPart part = new ServiceUsedPart(id, companyId, serviceTicket, inventory, quantityUsed,
                    sellingPriceSnapshot);
            part.setUnitOfMeasure(unitOfMeasure);
            part.setBuyingPriceSnapshot(buyingPriceSnapshot);
            part.setSourceVehicleId(sourceVehicleId);
            part.setClientRequestId(clientRequestId);
            return part;
        }
    }
}
