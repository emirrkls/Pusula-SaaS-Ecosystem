package com.pusula.desktop.dto;

public class VehicleDTO {
    private Long id;
    private Long companyId;
    private String licensePlate;
    private String driverName;
    private Boolean isActive;

    public VehicleDTO() {
    }

    public VehicleDTO(Long id, Long companyId, String licensePlate, String driverName, Boolean isActive) {
        this.id = id;
        this.companyId = companyId;
        this.licensePlate = licensePlate;
        this.driverName = driverName;
        this.isActive = isActive;
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

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        return licensePlate + " - " + (driverName != null ? driverName : "");
    }
}
