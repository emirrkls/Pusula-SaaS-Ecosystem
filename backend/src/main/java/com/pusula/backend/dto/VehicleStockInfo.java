package com.pusula.backend.dto;

import java.math.BigDecimal;

/**
 * DTO to show stock distribution per vehicle
 */
public class VehicleStockInfo {
    private Long vehicleId;
    private String vehiclePlate;
    private BigDecimal quantity;

    public VehicleStockInfo() {
    }

    public VehicleStockInfo(Long vehicleId, String vehiclePlate, BigDecimal quantity) {
        this.vehicleId = vehicleId;
        this.vehiclePlate = vehiclePlate;
        this.quantity = quantity;
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
