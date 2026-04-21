package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Web sitesi iletişim formundan gelen servis taleplerini taşıyan DTO.
 * 
 * Bu DTO, /api/public/service-request endpoint'ine gelen
 * kimlik doğrulaması gerektirmeyen (unauthenticated) istekler için kullanılır.
 * 
 * Multi-tenant yapıda companyId, Pusula İklimlendirme'nin kendi ID'sidir.
 */
public class PublicServiceRequestDTO {

    private Long companyId;

    @NotBlank(message = "Ad Soyad alanı boş bırakılamaz")
    @Size(min = 3, max = 100, message = "Ad Soyad 3-100 karakter arasında olmalıdır")
    private String customerName;

    @NotBlank(message = "Telefon numarası boş bırakılamaz")
    @Pattern(
        regexp = "^(\\+90|0)?[0-9]{10}$",
        message = "Geçerli bir Türkiye telefon numarası giriniz"
    )
    private String customerPhone;

    @NotBlank(message = "Adres alanı boş bırakılamaz")
    @Size(min = 10, max = 500, message = "Adres en az 10, en fazla 500 karakter olmalıdır")
    private String customerAddress;

    @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olmalıdır")
    private String description;

    /** Cihaz tipi: Klima, VRF, Kombi, Diger */
    @Size(max = 50, message = "Cihaz tipi en fazla 50 karakter olmalıdır")
    private String deviceType;

    /**
     * Honeypot alanı — Bot tuzağı.
     * Bu alan frontend'de CSS ile gizlenir.
     * Gerçek kullanıcılar bunu doldurmaz, bot'lar doldurur.
     * Dolu gelirse istek sessizce reddedilir.
     */
    private String website;

    // ===== Constructors =====

    public PublicServiceRequestDTO() {
    }

    public PublicServiceRequestDTO(Long companyId, String customerName, String customerPhone,
            String customerAddress, String description, String deviceType) {
        this.companyId = companyId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.description = description;
        this.deviceType = deviceType;
    }

    // ===== Builder =====

    public static PublicServiceRequestDTOBuilder builder() {
        return new PublicServiceRequestDTOBuilder();
    }

    // ===== Getters & Setters =====

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

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    // ===== Builder Class =====

    public static class PublicServiceRequestDTOBuilder {
        private Long companyId;
        private String customerName;
        private String customerPhone;
        private String customerAddress;
        private String description;
        private String deviceType;

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

        public PublicServiceRequestDTOBuilder deviceType(String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public PublicServiceRequestDTO build() {
            return new PublicServiceRequestDTO(companyId, customerName, customerPhone,
                    customerAddress, description, deviceType);
        }
    }
}
