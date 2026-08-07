package com.pusula.desktop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlySummaryDTO {
    private String period; // "2025-11"
    private String displayPeriod; // "Kasım 2025"
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
