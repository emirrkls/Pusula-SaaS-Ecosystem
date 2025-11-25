package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * FinancialSummaryDTO - Financial summary data
 */
public class FinancialSummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private Map<String, BigDecimal> expenseBreakdown; // Category -> Amount

    public FinancialSummaryDTO() {
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getNetProfit() {
        return netProfit;
    }

    public void setNetProfit(BigDecimal netProfit) {
        this.netProfit = netProfit;
    }

    public Map<String, BigDecimal> getExpenseBreakdown() {
        return expenseBreakdown;
    }

    public void setExpenseBreakdown(Map<String, BigDecimal> expenseBreakdown) {
        this.expenseBreakdown = expenseBreakdown;
    }
}
