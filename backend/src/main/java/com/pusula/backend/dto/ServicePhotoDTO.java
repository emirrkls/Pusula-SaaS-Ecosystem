package com.pusula.backend.dto;

import com.pusula.backend.entity.ServicePhoto;
import java.time.LocalDateTime;

public class ServicePhotoDTO {
    private Long id;
    private Long ticketId;
    private String url;
    private String thumbnailUrl;
    private ServicePhoto.PhotoType type;
    private String note;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private LocalDateTime serviceDate;
    private String customerName;
    private String ticketDescription;

    public ServicePhotoDTO() {
    }

    public ServicePhotoDTO(Long id, Long ticketId, String url, String thumbnailUrl,
                           ServicePhoto.PhotoType type, String note,
                           String uploadedByName, LocalDateTime uploadedAt, LocalDateTime serviceDate,
                           String customerName, String ticketDescription) {
        this.id = id;
        this.ticketId = ticketId;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.type = type;
        this.note = note;
        this.uploadedByName = uploadedByName;
        this.uploadedAt = uploadedAt;
        this.serviceDate = serviceDate;
        this.customerName = customerName;
        this.ticketDescription = ticketDescription;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public ServicePhoto.PhotoType getType() { return type; }
    public void setType(ServicePhoto.PhotoType type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public LocalDateTime getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDateTime serviceDate) { this.serviceDate = serviceDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getTicketDescription() { return ticketDescription; }
    public void setTicketDescription(String ticketDescription) { this.ticketDescription = ticketDescription; }
}
