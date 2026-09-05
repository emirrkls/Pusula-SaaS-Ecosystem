package com.pusula.backend.dto;

import java.time.LocalDateTime;

public class ServiceTicketNoteDTO {
    private Long id;
    private Long serviceTicketId;
    private Long authorUserId;
    private String authorName;
    private String noteType;
    private String content;
    private LocalDateTime createdAt;
    private boolean important;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceTicketId() { return serviceTicketId; }
    public void setServiceTicketId(Long serviceTicketId) { this.serviceTicketId = serviceTicketId; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}
