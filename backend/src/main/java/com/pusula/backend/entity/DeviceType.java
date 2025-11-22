package com.pusula.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "device_types")
public class DeviceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    public DeviceType() {
    }

    public DeviceType(UUID id, UUID companyId, String name) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
    }

    public static DeviceTypeBuilder builder() {
        return new DeviceTypeBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static class DeviceTypeBuilder {
        private UUID id;
        private UUID companyId;
        private String name;

        DeviceTypeBuilder() {
        }

        public DeviceTypeBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public DeviceTypeBuilder companyId(UUID companyId) {
            this.companyId = companyId;
            return this;
        }

        public DeviceTypeBuilder name(String name) {
            this.name = name;
            return this;
        }

        public DeviceType build() {
            return new DeviceType(id, companyId, name);
        }
    }
}
