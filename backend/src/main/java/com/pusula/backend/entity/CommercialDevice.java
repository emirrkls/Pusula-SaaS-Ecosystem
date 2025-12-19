package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "commercial_devices")
@SQLDelete(sql = "UPDATE commercial_devices SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class CommercialDevice extends BaseEntity {

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column
    private Integer btu; // BTU value, nullable for non-AC devices

    @Column
    private String gasType; // R32, R410A, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_type_id")
    private DeviceType deviceType;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal buyingPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column
    private LocalDateTime createdAt;

    public CommercialDevice() {
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getBtu() {
        return btu;
    }

    public void setBtu(Integer btu) {
        this.btu = btu;
    }

    public String getGasType() {
        return gasType;
    }

    public void setGasType(String gasType) {
        this.gasType = gasType;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getBuyingPrice() {
        return buyingPrice;
    }

    public void setBuyingPrice(BigDecimal buyingPrice) {
        this.buyingPrice = buyingPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Builder
    public static CommercialDeviceBuilder builder() {
        return new CommercialDeviceBuilder();
    }

    public static class CommercialDeviceBuilder {
        private Long id;
        private Long companyId;
        private String brand;
        private String model;
        private Integer btu;
        private String gasType;
        private DeviceType deviceType;
        private Integer quantity = 0;
        private BigDecimal buyingPrice;
        private BigDecimal sellingPrice;

        public CommercialDeviceBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CommercialDeviceBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public CommercialDeviceBuilder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public CommercialDeviceBuilder model(String model) {
            this.model = model;
            return this;
        }

        public CommercialDeviceBuilder btu(Integer btu) {
            this.btu = btu;
            return this;
        }

        public CommercialDeviceBuilder gasType(String gasType) {
            this.gasType = gasType;
            return this;
        }

        public CommercialDeviceBuilder deviceType(DeviceType deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public CommercialDeviceBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public CommercialDeviceBuilder buyingPrice(BigDecimal buyingPrice) {
            this.buyingPrice = buyingPrice;
            return this;
        }

        public CommercialDeviceBuilder sellingPrice(BigDecimal sellingPrice) {
            this.sellingPrice = sellingPrice;
            return this;
        }

        public CommercialDevice build() {
            CommercialDevice device = new CommercialDevice();
            device.setId(id);
            device.setCompanyId(companyId);
            device.brand = brand;
            device.model = model;
            device.btu = btu;
            device.gasType = gasType;
            device.deviceType = deviceType;
            device.quantity = quantity;
            device.buyingPrice = buyingPrice;
            device.sellingPrice = sellingPrice;
            return device;
        }
    }
}
