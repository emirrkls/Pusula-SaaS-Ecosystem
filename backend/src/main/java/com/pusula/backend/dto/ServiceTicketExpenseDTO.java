package com.pusula.backend.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for ServiceTicketExpense entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTicketExpenseDTO {
    private Long id;
    private Long serviceTicketId;
    private Long companyId;
    private String description;
    private BigDecimal amount;
    private String supplier;
    private String notes;
    private LocalDateTime createdAt;
}
