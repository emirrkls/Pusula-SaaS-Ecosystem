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
    private BigDecimal currentAccountTransferred;
    private BigDecimal cashCardCollections;
    private BigDecimal currentAccountCollections;
    private BigDecimal otherCashIncome;
    private BigDecimal totalCollected;
    private BigDecimal serviceDirectCost;
    private BigDecimal otherOperatingExpenses;
    private BigDecimal totalProfitExpenses;
    private BigDecimal serviceCashExpenses;
    private BigDecimal otherCashExpenses;
    private BigDecimal totalCashExpenses;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private BigDecimal netCash;
    private BigDecimal closingCumulativeProfit;
    private BigDecimal openingCashBalance;
    private BigDecimal closingCashBalance;
    private BigDecimal carryOver; // Önceki aydan devreden tutar
}
