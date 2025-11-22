package com.pusula.backend.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_photos")
public class ServicePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoType type;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    public enum PhotoType {
        BEFORE, AFTER
    }

    public ServicePhoto() {
    }

    public ServicePhoto(UUID id, UUID ticketId, String url, PhotoType type, LocalDateTime uploadedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.url = url;
        this.type = type;
        this.uploadedAt = uploadedAt;
    }

    public static ServicePhotoBuilder builder() {
        return new ServicePhotoBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
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

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public static class ServicePhotoBuilder {
        private UUID id;
        private UUID ticketId;
        private String url;
        private PhotoType type;
        private LocalDateTime uploadedAt;

        ServicePhotoBuilder() {
        }

        public ServicePhotoBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ServicePhotoBuilder ticketId(UUID ticketId) {
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

        public ServicePhotoBuilder uploadedAt(LocalDateTime uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public ServicePhoto build() {
            return new ServicePhoto(id, ticketId, url, type, uploadedAt);
        }
    }
}
