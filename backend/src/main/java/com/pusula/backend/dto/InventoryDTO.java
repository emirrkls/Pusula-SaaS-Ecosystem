package com.pusula.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public class InventoryDTO {
    private Long id;

    @NotBlank(message = "Parca adi bos olamaz")
    private String partName;

    @NotNull(message = "Miktar zorunludur")
    @PositiveOrZero(message = "Miktar negatif olamaz")
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "Alis fiyati negatif olamaz")
    private BigDecimal buyPrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Satis fiyati negatif olamaz")
    private BigDecimal sellPrice;

    @PositiveOrZero(message = "Kritik seviye negatif olamaz")
    private BigDecimal criticalLevel;
    private String unitOfMeasure = "ADET";
    private String brand;
    private String category;
    private String barcode;

    // Stock distribution fields
    private BigDecimal warehouseQuantity; // Parts in main warehouse (quantity - inVehicle)
    private BigDecimal inVehicleQuantity; // Total parts in all vehicles
    private List<VehicleStockInfo> vehicleDistribution; // Per-vehicle breakdown

    public InventoryDTO() {
    }

    public InventoryDTO(Long id, String partName, BigDecimal quantity, BigDecimal buyPrice, BigDecimal sellPrice,
            BigDecimal criticalLevel, String unitOfMeasure, String brand, String category) {
        this.id = id;
        this.partName = partName;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.criticalLevel = criticalLevel;
        this.unitOfMeasure = unitOfMeasure;
        this.brand = brand;
        this.category = category;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
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

    public BigDecimal getCriticalLevel() {
        return criticalLevel;
    }

    public void setCriticalLevel(BigDecimal criticalLevel) {
        this.criticalLevel = criticalLevel;
    }

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

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

    public BigDecimal getWarehouseQuantity() {
        return warehouseQuantity;
    }

    public void setWarehouseQuantity(BigDecimal warehouseQuantity) {
        this.warehouseQuantity = warehouseQuantity;
    }

    public BigDecimal getInVehicleQuantity() {
        return inVehicleQuantity;
    }

    public void setInVehicleQuantity(BigDecimal inVehicleQuantity) {
        this.inVehicleQuantity = inVehicleQuantity;
    }

    public List<VehicleStockInfo> getVehicleDistribution() {
        return vehicleDistribution;
    }

    public void setVehicleDistribution(List<VehicleStockInfo> vehicleDistribution) {
        this.vehicleDistribution = vehicleDistribution;
    }

    public static class InventoryDTOBuilder {
        private Long id;
        private String partName;
        private BigDecimal quantity;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private BigDecimal criticalLevel;
        private String unitOfMeasure = "ADET";
        private String brand;
        private String category;
        private String barcode;

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

        public InventoryDTOBuilder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public InventoryDTOBuilder quantity(Integer quantity) {
            return quantity(quantity == null ? null : BigDecimal.valueOf(quantity));
        }

        public InventoryDTOBuilder buyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
            return this;
        }

        public InventoryDTOBuilder sellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
            return this;
        }

        public InventoryDTOBuilder criticalLevel(BigDecimal criticalLevel) {
            this.criticalLevel = criticalLevel;
            return this;
        }

        public InventoryDTOBuilder criticalLevel(Integer criticalLevel) {
            return criticalLevel(criticalLevel == null ? null : BigDecimal.valueOf(criticalLevel));
        }

        public InventoryDTOBuilder unitOfMeasure(String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            return this;
        }

        public InventoryDTOBuilder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public InventoryDTOBuilder category(String category) {
            this.category = category;
            return this;
        }

        public InventoryDTOBuilder barcode(String barcode) {
            this.barcode = barcode;
            return this;
        }

        public InventoryDTO build() {
            InventoryDTO dto = new InventoryDTO(id, partName, quantity, buyPrice, sellPrice, criticalLevel,
                    unitOfMeasure, brand, category);
            dto.setBarcode(barcode);
            return dto;
        }
    }
}
