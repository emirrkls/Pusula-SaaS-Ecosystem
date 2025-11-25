package com.pusula.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySummaryDTO {
    private LocalDate date;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netCash;
    private boolean isClosed;
    private List<IncomeItemDTO> incomeDetails;
    private List<ExpenseItemDTO> expenseDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncomeItemDTO {
        private Long ticketId;
        private String customerName;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseItemDTO {
        private Long id;
        private String category;
        private String description;
        private BigDecimal amount;
    }
}
