package com.pusula.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthlySummaryDTO {
    private String period; // "2025-11" (for backend)
    private String displayPeriod; // "Kasım 2025" (for display)
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
}
