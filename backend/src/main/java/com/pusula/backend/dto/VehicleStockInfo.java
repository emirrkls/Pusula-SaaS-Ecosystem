package com.pusula.backend.dto;

/**
 * DTO to show stock distribution per vehicle
 */
public class VehicleStockInfo {
    private Long vehicleId;
    private String vehiclePlate;
    private Integer quantity;

    public VehicleStockInfo() {
    }

    public VehicleStockInfo(Long vehicleId, String vehiclePlate, Integer quantity) {
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
