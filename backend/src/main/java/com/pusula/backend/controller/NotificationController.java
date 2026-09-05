package com.pusula.backend.controller;

import com.pusula.backend.dto.NotificationDTO;
import com.pusula.backend.service.AdminNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class NotificationController {
    private final AdminNotificationService service;
    public NotificationController(AdminNotificationService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<NotificationDTO>> list() { return ResponseEntity.ok(service.listMine()); }
    @GetMapping("/unread-count") public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", service.unreadCount()));
    }
    @PatchMapping("/{id}/read") public ResponseEntity<NotificationDTO> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(service.markRead(id));
    }
    @PatchMapping("/read-all") public ResponseEntity<Void> markAllRead() {
        service.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
