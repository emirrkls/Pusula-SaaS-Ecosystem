package com.pusula.desktop.dto;

/**
 * DTO for vehicle stock distribution info
 */
public class VehicleStockInfo {
    private Long vehicleId;
    private String vehiclePlate;
    private Integer quantity;

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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
