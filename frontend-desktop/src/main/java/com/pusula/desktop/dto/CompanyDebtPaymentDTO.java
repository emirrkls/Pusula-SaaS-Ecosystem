package com.pusula.desktop.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDebtPaymentDTO {
    private Long id;
    private Long debtId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String expenseCategory;
    private String creditorName;
    private String notes;
    private LocalDateTime createdAt;
}
