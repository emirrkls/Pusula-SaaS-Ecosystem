package com.pusula.backend.service;

import com.pusula.backend.dto.NotificationDTO;
import com.pusula.backend.entity.Notification;
import com.pusula.backend.entity.User;
import com.pusula.backend.event.AdminNotificationCreatedEvent;
import com.pusula.backend.repository.NotificationRepository;
import com.pusula.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminNotificationService {
    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminNotificationService(NotificationRepository repository, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void notifyCompanyAdmins(Long companyId, String title, String message,
            Notification.NotificationType severity, Notification.NotificationCategory category,
            String referenceType, Long referenceId, Long excludedUserId) {
        userRepository.findByCompanyIdAndRole(companyId, "COMPANY_ADMIN").stream()
                .filter(admin -> excludedUserId == null || !excludedUserId.equals(admin.getId()))
                .forEach(admin -> createForAdmin(admin, title, message, severity, category, referenceType, referenceId));
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> listMine() {
        User user = currentAdmin();
        return repository.findTop100ByCompanyIdAndUserIdOrderByCreatedAtDesc(user.getCompanyId(), user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        User user = currentAdmin();
        return repository.countByCompanyIdAndUserIdAndIsReadFalse(user.getCompanyId(), user.getId());
    }

    @Transactional
    public NotificationDTO markRead(Long notificationId) {
        User user = currentAdmin();
        Notification notification = repository.findByIdAndCompanyIdAndUserId(
                        notificationId, user.getCompanyId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Bildirim bulunamadı."));
        notification.setRead(true);
        return toDto(repository.save(notification));
    }

    @Transactional
    public void markAllRead() {
        User user = currentAdmin();
        List<Notification> notifications = repository.findByUserIdAndIsReadFalse(user.getId()).stream()
                .filter(item -> user.getCompanyId().equals(item.getCompanyId())).toList();
        notifications.forEach(item -> item.setRead(true));
        repository.saveAll(notifications);
    }

    private void createForAdmin(User admin, String title, String message,
            Notification.NotificationType severity, Notification.NotificationCategory category,
            String referenceType, Long referenceId) {
        Notification notification = Notification.builder()
                .companyId(admin.getCompanyId()).userId(admin.getId()).title(title).message(message)
                .type(severity).build();
        notification.setCategory(category);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        Notification saved = repository.save(notification);
        eventPublisher.publishEvent(new AdminNotificationCreatedEvent(
                admin.getCompanyId(), admin.getId(), saved.getId()));
    }

    private User currentAdmin() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!("COMPANY_ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole()))) {
            throw new org.springframework.security.access.AccessDeniedException("Bildirim merkezi yalnızca yöneticilere açıktır.");
        }
        return user;
    }

    private NotificationDTO toDto(Notification item) {
        return new NotificationDTO(item.getId(), item.getTitle(), item.getMessage(), item.getType().name(),
                item.getCategory().name(), item.getReferenceType(), item.getReferenceId(),
                item.isRead(), item.getCreatedAt());
    }
}
