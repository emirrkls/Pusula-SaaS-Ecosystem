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

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "buy_price")
    private BigDecimal buyPrice;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "critical_level")
    private Integer criticalLevel;

    public Inventory() {
    }

    public Inventory(Long id, Long companyId, String partName, Integer quantity, BigDecimal buyPrice,
            BigDecimal sellPrice, Integer criticalLevel) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.partName = partName;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.criticalLevel = criticalLevel;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
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

    public Integer getCriticalLevel() {
        return criticalLevel;
    }

    public void setCriticalLevel(Integer criticalLevel) {
        this.criticalLevel = criticalLevel;
    }

    public static class InventoryBuilder {
        private Long id;
        private Long companyId;
        private String partName;
        private Integer quantity;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private Integer criticalLevel;

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

        public InventoryBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public InventoryBuilder buyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
            return this;
        }

        public InventoryBuilder sellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
            return this;
        }

        public InventoryBuilder criticalLevel(Integer criticalLevel) {
            this.criticalLevel = criticalLevel;
            return this;
        }

        public Inventory build() {
            return new Inventory(id, companyId, partName, quantity, buyPrice, sellPrice, criticalLevel);
        }
    }
}
