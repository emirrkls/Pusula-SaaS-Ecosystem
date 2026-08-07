package com.pusula.desktop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlySummaryDTO {
    private String period; // "2025-11"
    private String displayPeriod; // "Kasım 2025"
    private BigDecimal totalIncome;
    private BigDecimal currentAccountTransferred;
    private BigDecimal totalCollected;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private BigDecimal netCash;
    private BigDecimal carryOver; // Önceki aydan devreden tutar
}
