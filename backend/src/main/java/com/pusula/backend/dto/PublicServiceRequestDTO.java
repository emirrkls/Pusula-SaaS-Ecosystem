package com.pusula.backend.dto;

public class PublicServiceRequestDTO {
    private Long companyId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private String description;

    public PublicServiceRequestDTO() {
    }

    public PublicServiceRequestDTO(Long companyId, String customerName, String customerPhone, String customerAddress,
            String description) {
        this.companyId = companyId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.description = description;
    }

    public static PublicServiceRequestDTOBuilder builder() {
        return new PublicServiceRequestDTOBuilder();
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static class PublicServiceRequestDTOBuilder {
        private Long companyId;
        private String customerName;
        private String customerPhone;
        private String customerAddress;
        private String description;

        PublicServiceRequestDTOBuilder() {
        }

        public PublicServiceRequestDTOBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public PublicServiceRequestDTOBuilder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public PublicServiceRequestDTOBuilder customerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
            return this;
        }

        public PublicServiceRequestDTOBuilder customerAddress(String customerAddress) {
            this.customerAddress = customerAddress;
            return this;
        }

        public PublicServiceRequestDTOBuilder description(String description) {
            this.description = description;
            return this;
        }

        public PublicServiceRequestDTO build() {
            return new PublicServiceRequestDTO(companyId, customerName, customerPhone, customerAddress, description);
        }
    }
}
