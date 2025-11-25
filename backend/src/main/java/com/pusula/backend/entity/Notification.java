package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    public Notification() {
    }

    public Notification(Long id, Long companyId, Long userId, String title, String message, boolean isRead,
            NotificationType type) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.type = type;
    }

    public static NotificationBuilder builder() {
        return new NotificationBuilder();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public enum NotificationType {
        INFO, WARNING, CRITICAL
    }

    public static class NotificationBuilder {
        private Long id;
        private Long companyId;
        private Long userId;
        private String title;
        private String message;
        private boolean isRead;
        private NotificationType type;

        NotificationBuilder() {
        }

        public NotificationBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public NotificationBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public NotificationBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public NotificationBuilder title(String title) {
            this.title = title;
            return this;
        }

        public NotificationBuilder message(String message) {
            this.message = message;
            return this;
        }

        public NotificationBuilder isRead(boolean isRead) {
            this.isRead = isRead;
            return this;
        }

        public NotificationBuilder type(NotificationType type) {
            this.type = type;
            return this;
        }

        public Notification build() {
            return new Notification(id, companyId, userId, title, message, isRead, type);
        }
    }
}
