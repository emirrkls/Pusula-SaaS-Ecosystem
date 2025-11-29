package com.pusula.desktop.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogDTO {
    private Long id;
    private Long companyId;
    private Long userId;
    private String userName;
    private String actionType;
    private String entityType;
    private Long entityId;
    private String description;
    private String oldValue;
    private String newValue;
    private LocalDateTime timestamp;
    private String ipAddress;
}
