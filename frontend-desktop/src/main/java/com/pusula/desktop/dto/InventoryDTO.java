package com.pusula.desktop.dto;

import java.math.BigDecimal;

public class InventoryDTO {
    private Long id;
    private String partName;
    private Integer quantity;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer criticalLevel;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
