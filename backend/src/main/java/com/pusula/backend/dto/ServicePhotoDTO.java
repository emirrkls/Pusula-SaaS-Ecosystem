package com.pusula.backend.dto;

import com.pusula.backend.entity.ServicePhoto;
import java.time.LocalDateTime;

public class ServicePhotoDTO {
    private Long id;
    private Long ticketId;
    private String url;
    private ServicePhoto.PhotoType type;
    private LocalDateTime uploadedAt;

    public ServicePhotoDTO() {
    }

    public ServicePhotoDTO(Long id, Long ticketId, String url, ServicePhoto.PhotoType type, LocalDateTime uploadedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.url = url;
        this.type = type;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public ServicePhoto.PhotoType getType() { return type; }
    public void setType(ServicePhoto.PhotoType type) { this.type = type; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
