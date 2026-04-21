package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pusula.backend.entity.AuditLog;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Log an action with Object serialization for before/after comparison
     */
    @Async
    @Transactional
    public void logChange(String actionType, String entityType, Long entityId,
            String description, Object oldObj, Object newObj) {
        String oldJson = toJson(oldObj);
        String newJson = toJson(newObj);
        log(actionType, entityType, entityId, description, oldJson, newJson);
    }

    /**
     * Log authentication event (login success/failure) - doesn't require
     * authenticated user
     */
    @Async
    @Transactional
    public void logAuth(Long companyId, Long userId, String userName, String actionType,
            String description, String ipAddress) {
        try {
            AuditLog log = new AuditLog(
                    companyId,
                    userId != null ? userId : 0L,
                    userName != null ? userName : "Unknown",
                    actionType,
                    "AUTH",
                    null,
                    description);
            log.setIpAddress(ipAddress);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to create auth audit log: {}", e.getMessage());
        }
    }

    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    /**
     * Log an action asynchronously
     */
    @Async
    @Transactional
    public void log(String actionType, String entityType, Long entityId, String description) {
        log(actionType, entityType, entityId, description, null, null);
    }

    /**
     * Log an action with before/after values asynchronously
     */
    @Async
    @Transactional
    public void log(String actionType, String entityType, Long entityId, String description,
            String oldValue, String newValue) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return; // Skip logging if no authenticated user
            }

            User currentUser = (User) auth.getPrincipal();

            AuditLog log = new AuditLog(
                    currentUser.getCompanyId(),
                    currentUser.getId(),
                    currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUsername(),
                    actionType,
                    entityType,
                    entityId,
                    description);

            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            log.setIpAddress(getClientIpAddress());

            auditLogRepository.save(log);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            logger.error("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Get logs for a company with pagination
     */
    public Page<AuditLog> getLogsForCompany(Long companyId, Pageable pageable) {
        return auditLogRepository.findByCompanyIdOrderByTimestampDesc(companyId, pageable);
    }

    /**
     * Get logs with date range filter
     */
    public Page<AuditLog> getLogsWithDateRange(Long companyId, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate, pageable);
    }

    /**
     * Get logs filtered by user
     */
    public Page<AuditLog> getLogsByUser(Long companyId, Long userId, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndUserIdAndDateRange(
                companyId, userId, startDate, endDate, pageable);
    }

    /**
     * Get logs filtered by action type
     */
    public Page<AuditLog> getLogsByActionType(Long companyId, String actionType,
            LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndActionTypeAndDateRange(
                companyId, actionType, startDate, endDate, pageable);
    }

    /**
     * Get logs filtered by both user AND action type
     */
    public Page<AuditLog> getLogsByUserAndActionType(Long companyId, Long userId, String actionType,
            LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndUserIdAndActionTypeAndDateRange(
                companyId, userId, actionType, startDate, endDate, pageable);
    }

    /**
     * Get logs for a specific entity
     */
    public List<AuditLog> getLogsForEntity(Long companyId, String entityType, Long entityId) {
        return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
                companyId, entityType, entityId);
    }

    /**
     * Get timeline for a specific ticket (chronological order - oldest first)
     */
    public List<AuditLog> getTicketTimeline(Long companyId, Long ticketId) {
        return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByTimestampAsc(
                companyId, "TICKET", ticketId);
    }

    /**
     * Get total log count for a company
     */
    public long getLogCount(Long companyId) {
        return auditLogRepository.countByCompanyId(companyId);
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
