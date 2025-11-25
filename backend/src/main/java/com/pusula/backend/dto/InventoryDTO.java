package com.pusula.backend.dto;

import java.math.BigDecimal;

public class InventoryDTO {
    private Long id;
    private String partName;
    private Integer quantity;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer criticalLevel;

    public InventoryDTO() {
    }

    public InventoryDTO(Long id, String partName, Integer quantity, BigDecimal buyPrice, BigDecimal sellPrice,
            Integer criticalLevel) {
        this.id = id;
        this.partName = partName;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.criticalLevel = criticalLevel;
    }

    public static InventoryDTOBuilder builder() {
        return new InventoryDTOBuilder();
    }

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

    public static class InventoryDTOBuilder {
        private Long id;
        private String partName;
        private Integer quantity;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private Integer criticalLevel;

        InventoryDTOBuilder() {
        }

        public InventoryDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public InventoryDTOBuilder partName(String partName) {
            this.partName = partName;
            return this;
        }

        public InventoryDTOBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public InventoryDTOBuilder buyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
            return this;
        }

        public InventoryDTOBuilder sellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
            return this;
        }

        public InventoryDTOBuilder criticalLevel(Integer criticalLevel) {
            this.criticalLevel = criticalLevel;
            return this;
        }

        public InventoryDTO build() {
            return new InventoryDTO(id, partName, quantity, buyPrice, sellPrice, criticalLevel);
        }
    }
}
