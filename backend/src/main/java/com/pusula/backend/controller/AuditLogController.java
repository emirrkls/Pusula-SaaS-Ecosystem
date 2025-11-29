package com.pusula.backend.controller;

import com.pusula.backend.entity.AuditLog;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Get paginated audit logs for the current user's company
     */
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        User currentUser = (User) authentication.getPrincipal();
        Long companyId = currentUser.getCompanyId();

        Pageable pageable = PageRequest.of(page, size);

        // Default date range: last 30 days
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Page<AuditLog> logs;

        if (userId != null) {
            logs = auditLogService.getLogsByUser(companyId, userId, startDate, endDate, pageable);
        } else if (actionType != null) {
            logs = auditLogService.getLogsByActionType(companyId, actionType, startDate, endDate, pageable);
        } else {
            logs = auditLogService.getLogsWithDateRange(companyId, startDate, endDate, pageable);
        }

        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit logs for a specific entity
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLog>> getLogsForEntity(
            Authentication authentication,
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        User currentUser = (User) authentication.getPrincipal();
        List<AuditLog> logs = auditLogService.getLogsForEntity(
                currentUser.getCompanyId(), entityType, entityId);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get timeline for a specific ticket (chronological order)
     */
    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<List<AuditLog>> getTicketTimeline(
            Authentication authentication,
            @PathVariable Long ticketId) {

        User currentUser = (User) authentication.getPrincipal();
        List<AuditLog> timeline = auditLogService.getTicketTimeline(
                currentUser.getCompanyId(), ticketId);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get total log count for the company
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getLogCount(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        long count = auditLogService.getLogCount(currentUser.getCompanyId());
        return ResponseEntity.ok(count);
    }
}
