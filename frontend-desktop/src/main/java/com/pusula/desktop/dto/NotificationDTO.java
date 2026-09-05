package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private String severity;
    private String category;
    private String referenceType;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getSeverity() { return severity; }
    public String getCategory() { return category; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setRead(boolean read) { this.read = read; }
}
