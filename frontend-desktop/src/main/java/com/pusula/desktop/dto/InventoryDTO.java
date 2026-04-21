package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.util.List;

public class InventoryDTO {
    private Long id;
    private String partName;
    private Integer quantity;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer criticalLevel;
    private String brand;
    private String category;
    private String location; // DEPO or VEHICLE
    private Long vehicleId; // If location is VEHICLE, which vehicle

    // Stock distribution fields
    private Integer warehouseQuantity;
    private Integer inVehicleQuantity;
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

    public Integer getWarehouseQuantity() {
        return warehouseQuantity;
    }

    public void setWarehouseQuantity(Integer warehouseQuantity) {
        this.warehouseQuantity = warehouseQuantity;
    }

    public Integer getInVehicleQuantity() {
        return inVehicleQuantity;
    }

    public void setInVehicleQuantity(Integer inVehicleQuantity) {
        this.inVehicleQuantity = inVehicleQuantity;
    }

    public List<VehicleStockInfo> getVehicleDistribution() {
        return vehicleDistribution;
    }

    public void setVehicleDistribution(List<VehicleStockInfo> vehicleDistribution) {
        this.vehicleDistribution = vehicleDistribution;
    }
}
