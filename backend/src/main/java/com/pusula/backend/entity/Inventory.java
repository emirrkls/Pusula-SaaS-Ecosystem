package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory")
@SQLDelete(sql = "UPDATE inventory SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Inventory extends BaseEntity {

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "buy_price")
    private BigDecimal buyPrice;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "critical_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal criticalLevel = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 16,
            columnDefinition = "VARCHAR(16) DEFAULT 'ADET'")
    private InventoryUnit unitOfMeasure = InventoryUnit.ADET;

    @Column(name = "brand")
    private String brand;

    @Column(name = "category")
    private String category;

    @Column(name = "barcode")
    private String barcode;

    /**
     * Location where this item is stored: DEPO or VEHICLE
     */
    @Column(name = "location")
    private String location = "DEPO";

    /**
     * If location is VEHICLE, this references which vehicle
     */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    public Inventory() {
    }

    public Inventory(Long id, Long companyId, String partName, BigDecimal quantity, BigDecimal buyPrice,
            BigDecimal sellPrice, BigDecimal criticalLevel, InventoryUnit unitOfMeasure) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.partName = partName;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.criticalLevel = criticalLevel;
        this.unitOfMeasure = unitOfMeasure == null ? InventoryUnit.ADET : unitOfMeasure;
    }

    public static InventoryBuilder builder() {
        return new InventoryBuilder();
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity == null ? null : BigDecimal.valueOf(quantity);
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }

    public BigDecimal getCriticalLevel() {
        return criticalLevel;
    }

    public void setCriticalLevel(BigDecimal criticalLevel) {
        this.criticalLevel = criticalLevel;
    }

    public void setCriticalLevel(Integer criticalLevel) {
        this.criticalLevel = criticalLevel == null ? null : BigDecimal.valueOf(criticalLevel);
    }

    public InventoryUnit getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(InventoryUnit unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure == null ? InventoryUnit.ADET : unitOfMeasure;
    }

    public Inventory(Long id, Long companyId, String partName, Integer quantity, BigDecimal buyPrice,
            BigDecimal sellPrice, Integer criticalLevel) {
        this(id, companyId, partName,
                quantity == null ? null : BigDecimal.valueOf(quantity), buyPrice, sellPrice,
                criticalLevel == null ? null : BigDecimal.valueOf(criticalLevel), InventoryUnit.ADET);
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public static class InventoryBuilder {
        private Long id;
        private Long companyId;
        private String partName;
        private BigDecimal quantity;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private BigDecimal criticalLevel;
        private InventoryUnit unitOfMeasure = InventoryUnit.ADET;

        InventoryBuilder() {
        }

        public InventoryBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public InventoryBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public InventoryBuilder partName(String partName) {
            this.partName = partName;
            return this;
        }

        public InventoryBuilder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public InventoryBuilder quantity(Integer quantity) {
            return quantity(quantity == null ? null : BigDecimal.valueOf(quantity));
        }

        public InventoryBuilder buyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
            return this;
        }

        public InventoryBuilder sellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
            return this;
        }

        public InventoryBuilder criticalLevel(BigDecimal criticalLevel) {
            this.criticalLevel = criticalLevel;
            return this;
        }

        public InventoryBuilder criticalLevel(Integer criticalLevel) {
            return criticalLevel(criticalLevel == null ? null : BigDecimal.valueOf(criticalLevel));
        }

        public InventoryBuilder unitOfMeasure(InventoryUnit unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            return this;
        }

        public Inventory build() {
            return new Inventory(id, companyId, partName, quantity, buyPrice, sellPrice, criticalLevel, unitOfMeasure);
        }
    }
}
