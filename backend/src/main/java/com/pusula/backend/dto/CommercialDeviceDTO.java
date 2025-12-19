package com.pusula.backend.dto;

import java.math.BigDecimal;

public class CommercialDeviceDTO {
    private Long id;
    private Long companyId;
    private String brand;
    private String model;
    private Integer btu;
    private String gasType;
    private Long deviceTypeId;
    private String deviceTypeName;
    private Integer quantity;
    private BigDecimal buyingPrice;
    private BigDecimal sellingPrice;
    private String formattedProfit; // Calculated field, "-" for technicians

    public CommercialDeviceDTO() {
    }

    // Getters and Setters
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

    public Long getDeviceTypeId() {
        return deviceTypeId;
    }

    public void setDeviceTypeId(Long deviceTypeId) {
        this.deviceTypeId = deviceTypeId;
    }

    public String getDeviceTypeName() {
        return deviceTypeName;
    }

    public void setDeviceTypeName(String deviceTypeName) {
        this.deviceTypeName = deviceTypeName;
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

    public String getFormattedProfit() {
        return formattedProfit;
    }

    public void setFormattedProfit(String formattedProfit) {
        this.formattedProfit = formattedProfit;
    }

    // Builder
    public static CommercialDeviceDTOBuilder builder() {
        return new CommercialDeviceDTOBuilder();
    }

    public static class CommercialDeviceDTOBuilder {
        private Long id;
        private Long companyId;
        private String brand;
        private String model;
        private Integer btu;
        private String gasType;
        private Long deviceTypeId;
        private String deviceTypeName;
        private Integer quantity;
        private BigDecimal buyingPrice;
        private BigDecimal sellingPrice;
        private String formattedProfit;

        public CommercialDeviceDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CommercialDeviceDTOBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public CommercialDeviceDTOBuilder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public CommercialDeviceDTOBuilder model(String model) {
            this.model = model;
            return this;
        }

        public CommercialDeviceDTOBuilder btu(Integer btu) {
            this.btu = btu;
            return this;
        }

        public CommercialDeviceDTOBuilder gasType(String gasType) {
            this.gasType = gasType;
            return this;
        }

        public CommercialDeviceDTOBuilder deviceTypeId(Long deviceTypeId) {
            this.deviceTypeId = deviceTypeId;
            return this;
        }

        public CommercialDeviceDTOBuilder deviceTypeName(String deviceTypeName) {
            this.deviceTypeName = deviceTypeName;
            return this;
        }

        public CommercialDeviceDTOBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public CommercialDeviceDTOBuilder buyingPrice(BigDecimal buyingPrice) {
            this.buyingPrice = buyingPrice;
            return this;
        }

        public CommercialDeviceDTOBuilder sellingPrice(BigDecimal sellingPrice) {
            this.sellingPrice = sellingPrice;
            return this;
        }

        public CommercialDeviceDTOBuilder formattedProfit(String formattedProfit) {
            this.formattedProfit = formattedProfit;
            return this;
        }

        public CommercialDeviceDTO build() {
            CommercialDeviceDTO dto = new CommercialDeviceDTO();
            dto.id = id;
            dto.companyId = companyId;
            dto.brand = brand;
            dto.model = model;
            dto.btu = btu;
            dto.gasType = gasType;
            dto.deviceTypeId = deviceTypeId;
            dto.deviceTypeName = deviceTypeName;
            dto.quantity = quantity;
            dto.buyingPrice = buyingPrice;
            dto.sellingPrice = sellingPrice;
            dto.formattedProfit = formattedProfit;
            return dto;
        }
    }
}
