package com.pusula.desktop.dto;

import java.math.BigDecimal;

/**
 * DTO for vehicle stock distribution info
 */
public class VehicleStockInfo {
    private Long vehicleId;
    private String vehiclePlate;
    private BigDecimal quantity;

    public VehicleStockInfo() {
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
