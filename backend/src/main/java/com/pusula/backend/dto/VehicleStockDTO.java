package com.pusula.backend.dto;

public class VehicleStockDTO {
    private Long id;
    private Long companyId;
    private Long vehicleId;
    private Long inventoryId;
    private String vehicleLicensePlate;
    private String partName;
    private Integer quantity;

    public VehicleStockDTO() {
    }

    public VehicleStockDTO(Long id, Long companyId, Long vehicleId, Long inventoryId,
            String vehicleLicensePlate, String partName, Integer quantity) {
        this.id = id;
        this.companyId = companyId;
        this.vehicleId = vehicleId;
        this.inventoryId = inventoryId;
        this.vehicleLicensePlate = vehicleLicensePlate;
        this.partName = partName;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getVehicleLicensePlate() {
        return vehicleLicensePlate;
    }

    public void setVehicleLicensePlate(String vehicleLicensePlate) {
        this.vehicleLicensePlate = vehicleLicensePlate;
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
}
