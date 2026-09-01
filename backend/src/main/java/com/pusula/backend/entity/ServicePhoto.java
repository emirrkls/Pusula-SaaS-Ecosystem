package com.pusula.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_photos")
public class ServicePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoType type;

    @Column(length = 500)
    private String note;

    @Column(name = "uploaded_by_name", length = 255)
    private String uploadedByName;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    public enum PhotoType {
        BEFORE,
        AFTER,
        INDOOR_UNIT_SERIAL,
        OUTDOOR_UNIT_SERIAL,
        DEVICE_LABEL,
        FAULT_DETAIL,
        INSTALLATION,
        OTHER
    }

    public ServicePhoto() {
    }

    public ServicePhoto(Long id, Long ticketId, String url, PhotoType type, String note,
                        String uploadedByName, LocalDateTime uploadedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.url = url;
        this.type = type;
        this.note = note;
        this.uploadedByName = uploadedByName;
        this.uploadedAt = uploadedAt;
    }

    public static ServicePhotoBuilder builder() {
        return new ServicePhotoBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public PhotoType getType() {
        return type;
    }

    public void setType(PhotoType type) {
        this.type = type;
    }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public static class ServicePhotoBuilder {
        private Long id;
        private Long ticketId;
        private String url;
        private PhotoType type;
        private String note;
        private String uploadedByName;
        private LocalDateTime uploadedAt;

        ServicePhotoBuilder() {
        }

        public ServicePhotoBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServicePhotoBuilder ticketId(Long ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public ServicePhotoBuilder url(String url) {
            this.url = url;
            return this;
        }

        public ServicePhotoBuilder type(PhotoType type) {
            this.type = type;
            return this;
        }

        public ServicePhotoBuilder note(String note) {
            this.note = note;
            return this;
        }

        public ServicePhotoBuilder uploadedByName(String uploadedByName) {
            this.uploadedByName = uploadedByName;
            return this;
        }

        public ServicePhotoBuilder uploadedAt(LocalDateTime uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public ServicePhoto build() {
            return new ServicePhoto(id, ticketId, url, type, note, uploadedByName, uploadedAt);
        }
    }
}
