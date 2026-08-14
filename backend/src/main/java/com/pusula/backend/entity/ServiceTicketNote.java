package com.pusula.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "service_ticket_notes")
@SQLDelete(sql = "UPDATE service_ticket_notes SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class ServiceTicketNote extends BaseEntity {

    @Column(name = "service_ticket_id", nullable = false)
    private Long serviceTicketId;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 32)
    private NoteType noteType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public Long getServiceTicketId() { return serviceTicketId; }
    public void setServiceTicketId(Long serviceTicketId) { this.serviceTicketId = serviceTicketId; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public NoteType getNoteType() { return noteType; }
    public void setNoteType(NoteType noteType) { this.noteType = noteType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public enum NoteType {
        WORK_LOG,
        CLOSURE
    }
}
