package com.pusula.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDebtAdditionDTO {
    private Long id;
    private Long debtId;
    private BigDecimal amount;
    private LocalDate additionDate;
    private String notes;
    private LocalDateTime createdAt;
}
