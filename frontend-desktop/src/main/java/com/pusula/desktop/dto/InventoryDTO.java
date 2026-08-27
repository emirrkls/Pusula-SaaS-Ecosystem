package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.util.List;

public class InventoryDTO {
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
    private String location; // DEPO or VEHICLE
    private Long vehicleId; // If location is VEHICLE, which vehicle

    // Stock distribution fields
    private BigDecimal warehouseQuantity;
    private BigDecimal inVehicleQuantity;
    private List<VehicleStockInfo> vehicleDistribution;

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

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

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
}
