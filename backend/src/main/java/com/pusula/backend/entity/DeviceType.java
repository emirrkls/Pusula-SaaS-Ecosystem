package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "device_types")
@SQLDelete(sql = "UPDATE device_types SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class DeviceType extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public DeviceType() {
    }

    public DeviceType(Long id, Long companyId, String name) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.name = name;
    }

    public static DeviceTypeBuilder builder() {
        return new DeviceTypeBuilder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static class DeviceTypeBuilder {
        private Long id;
        private Long companyId;
        private String name;

        DeviceTypeBuilder() {
        }

        public DeviceTypeBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public DeviceTypeBuilder companyId(Long companyId) {
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
