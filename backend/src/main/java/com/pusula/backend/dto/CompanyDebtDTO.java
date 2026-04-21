package com.pusula.backend.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for CompanyDebt entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDebtDTO {
    private Long id;
    private Long companyId;
    private String creditorName;
    private String description;
    private BigDecimal originalAmount;
    private BigDecimal remainingAmount;
    private LocalDate debtDate;
    private LocalDate dueDate;
    private String creditorPhone;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
