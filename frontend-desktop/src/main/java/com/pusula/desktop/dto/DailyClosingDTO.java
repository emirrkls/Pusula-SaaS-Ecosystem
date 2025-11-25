package com.pusula.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingDTO {
    private Long id;
    private LocalDate date;
    private Long companyId;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netCash;
    private String status;
    private LocalDateTime closedAt;
    private Long closedByUserId;
}
