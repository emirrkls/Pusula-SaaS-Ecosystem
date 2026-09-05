package com.pusula.backend.service;

import com.pusula.backend.entity.Notification;
import com.pusula.backend.entity.User;
import com.pusula.backend.event.AdminNotificationCreatedEvent;
import com.pusula.backend.repository.NotificationRepository;
import com.pusula.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {
    @Mock NotificationRepository repository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher publisher;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void fansOutOnlyToCompanyAdminsAndPublishesAfterPersisting() {
        AdminNotificationService service = new AdminNotificationService(repository, userRepository, publisher);
        User first = admin(11L, 7L); User second = admin(12L, 7L);
        when(userRepository.findByCompanyIdAndRole(7L, "COMPANY_ADMIN")).thenReturn(List.of(first, second));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification item = invocation.getArgument(0);
            item.setId(item.getUserId() + 100);
            return item;
        });

        service.notifyCompanyAdmins(7L, "Başlık", "Mesaj", Notification.NotificationType.WARNING,
                Notification.NotificationCategory.IMPORTANT_NOTE, "TICKET", 44L, 11L);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertEquals(12L, saved.getValue().getUserId());
        assertEquals(44L, saved.getValue().getReferenceId());
        verify(publisher).publishEvent(new AdminNotificationCreatedEvent(7L, 12L, 112L));
    }

    @Test
    void unreadCountIsScopedToAuthenticatedCompanyAndUser() {
        AdminNotificationService service = new AdminNotificationService(repository, userRepository, publisher);
        User user = admin(21L, 9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(repository.countByCompanyIdAndUserIdAndIsReadFalse(9L, 21L)).thenReturn(3L);
        assertEquals(3L, service.unreadCount());
    }

    private User admin(Long id, Long companyId) {
        return User.builder().id(id).companyId(companyId).username("admin" + id)
                .passwordHash("x").role("COMPANY_ADMIN").fullName("Admin").build();
    }
}
