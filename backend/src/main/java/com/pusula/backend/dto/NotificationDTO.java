package com.pusula.backend.dto;

import java.time.LocalDateTime;

public record NotificationDTO(Long id, String title, String message, String severity, String category,
        String referenceType, Long referenceId, boolean read, LocalDateTime createdAt) {}
