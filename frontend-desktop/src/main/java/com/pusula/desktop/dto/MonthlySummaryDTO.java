package com.pusula.desktop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlySummaryDTO {
    private String period; // "2025-11"
    private String displayPeriod; // "Kasım 2025"
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
}
