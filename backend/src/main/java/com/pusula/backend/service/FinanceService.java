package com.pusula.backend.service;

import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final ServiceTicketRepository ticketRepository;
    private final ExpenseRepository expenseRepository;

    @Data
    @Builder
    public static class FinancialSummary {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netProfit;
        private Map<ExpenseCategory, BigDecimal> expenseBreakdown;
    }

    public FinancialSummary getSummary(Long companyId, String period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (period.toUpperCase()) {
            case "WEEKLY":
                startDate = endDate.minusWeeks(1);
                break;
            case "MONTHLY":
                startDate = endDate.minusMonths(1);
                break;
            case "YEARLY":
                startDate = endDate.minusYears(1);
                break;
            default: // DAILY
                startDate = endDate;
        }

        // Calculate Income (Completed Tickets)
        // Note: In a real app, we'd filter tickets by date in the DB.
        // For now, fetching all and filtering in memory for simplicity if repository
        // method doesn't exist,
        // but ideally we should add findByCompanyIdAndUpdatedAtBetween to repository.
        // Let's assume we fetch all for now or use a custom query if available.
        List<ServiceTicket> tickets = ticketRepository.findByCompanyId(companyId);

        BigDecimal totalIncome = tickets.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .filter(t -> {
                    LocalDate ticketDate = t.getUpdatedAt().toLocalDate();
                    return !ticketDate.isBefore(startDate) && !ticketDate.isAfter(endDate);
                })
                .map(ServiceTicket::getCollectedAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate Expenses
        List<Expense> expenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, startDate, endDate);

        BigDecimal totalExpense = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expense Breakdown
        Map<ExpenseCategory, BigDecimal> breakdown = new HashMap<>();
        for (Expense expense : expenses) {
            breakdown.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
        }

        return FinancialSummary.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netProfit(totalIncome.subtract(totalExpense))
                .expenseBreakdown(breakdown)
                .build();
    }

    public Expense addExpense(Expense expense) {
        return expenseRepository.save(expense);
    }

    public List<Expense> getRecentExpenses(Long companyId) {
        // Just return all for now, or last 50
        return expenseRepository.findByCompanyId(companyId);
    }
}
