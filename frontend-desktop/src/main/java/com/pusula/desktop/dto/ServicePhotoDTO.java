package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public class ServicePhotoDTO {
    private Long id;
    private Long ticketId;
    private String url;
    private String thumbnailUrl;
    private String type;
    private String note;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private LocalDateTime serviceDate;
    private String customerName;
    private String ticketDescription;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
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

    public String getTypeLabel() {
        return switch (type == null ? "" : type) {
            case "BEFORE" -> "İşlem Öncesi";
            case "AFTER" -> "İşlem Sonrası";
            case "INDOOR_UNIT_SERIAL" -> "İç Ünite Seri No";
            case "OUTDOOR_UNIT_SERIAL" -> "Dış Ünite Seri No";
            case "DEVICE_LABEL" -> "Cihaz Etiketi";
            case "FAULT_DETAIL" -> "Arıza Detayı";
            case "INSTALLATION" -> "Montaj / Tesisat";
            default -> "Diğer";
        };
    }
}
