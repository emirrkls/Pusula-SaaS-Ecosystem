package com.pusula.backend.service;

import com.pusula.backend.dto.FixedExpenseDefinitionDTO;
import com.pusula.backend.dto.CategoryReportDTO;
import com.pusula.backend.dto.DailySummaryDTO;
import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.FixedExpenseDefinition;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.DailyClosingRepository;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.repository.FixedExpenseDefinitionRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final ServiceTicketRepository ticketRepository;
    private final ExpenseRepository expenseRepository;
    private final DailyClosingRepository dailyClosingRepository;
    private final CustomerRepository customerRepository;
    private final FixedExpenseDefinitionRepository fixedExpenseDefinitionRepository;

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

    /**
     * Get daily summary for a specific date
     */
    public DailySummaryDTO getDailySummary(Long companyId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // Calculate Income from completed tickets on this date
        List<ServiceTicket> tickets = ticketRepository.findByCompanyId(companyId);
        List<ServiceTicket> completedTicketsToday = tickets.stream()
                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                .filter(t -> {
                    LocalDateTime updatedAt = t.getUpdatedAt();
                    return updatedAt != null
                            && !updatedAt.isBefore(startOfDay)
                            && !updatedAt.isAfter(endOfDay);
                })
                .collect(Collectors.toList());

        BigDecimal totalIncome = completedTicketsToday.stream()
                .map(ServiceTicket::getCollectedAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build income details with customer names (FIXED: proper customer name
        // retrieval)
        List<DailySummaryDTO.IncomeItemDTO> incomeDetails = new ArrayList<>();
        for (ServiceTicket ticket : completedTicketsToday) {
            String customerName = "Unknown";

            // Fetch customer name properly using map() instead of broken ifPresent()
            if (ticket.getCustomerId() != null) {
                customerName = customerRepository.findById(ticket.getCustomerId())
                        .map(customer -> customer.getName())
                        .orElse("Unknown");
            }

            incomeDetails.add(DailySummaryDTO.IncomeItemDTO.builder()
                    .ticketId(ticket.getId())
                    .customerName(customerName)
                    .amount(ticket.getCollectedAmount() != null ? ticket.getCollectedAmount() : BigDecimal.ZERO)
                    .build());
        }

        // Calculate Expenses for this date
        List<Expense> expenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, date, date);

        BigDecimal totalExpense = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build expense details
        List<DailySummaryDTO.ExpenseItemDTO> expenseDetails = expenses.stream()
                .map(expense -> DailySummaryDTO.ExpenseItemDTO.builder()
                        .id(expense.getId())
                        .category(expense.getCategory().name())
                        .description(expense.getDescription())
                        .amount(expense.getAmount())
                        .build())
                .collect(Collectors.toList());

        // Check if day is closed
        boolean isClosed = dailyClosingRepository
                .existsByCompanyIdAndDateAndStatus(companyId, date, DailyClosing.ClosingStatus.CLOSED);

        return DailySummaryDTO.builder()
                .date(date)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netCash(totalIncome.subtract(totalExpense))
                .isClosed(isClosed)
                .incomeDetails(incomeDetails)
                .expenseDetails(expenseDetails)
                .build();
    }

    /**
     * Close the day - create DailyClosing record
     */
    public DailyClosing closeDay(Long companyId, LocalDate date, Long userId) {
        // Check if already closed
        if (dailyClosingRepository.existsByCompanyIdAndDateAndStatus(
                companyId, date, DailyClosing.ClosingStatus.CLOSED)) {
            throw new IllegalStateException("Day is already closed for date: " + date);
        }

        // Get daily summary
        DailySummaryDTO summary = getDailySummary(companyId, date);

        // Create DailyClosing record
        DailyClosing closing = DailyClosing.builder()
                .date(date)
                .companyId(companyId)
                .totalIncome(summary.getTotalIncome())
                .totalExpense(summary.getTotalExpense())
                .netCash(summary.getNetCash())
                .status(DailyClosing.ClosingStatus.CLOSED)
                .closedAt(LocalDateTime.now())
                .closedByUserId(userId)
                .build();

        return dailyClosingRepository.save(closing);
    }

    /**
     * Get category-wise expense report for a date range
     */
    public CategoryReportDTO getCategoryReport(Long companyId, LocalDate startDate, LocalDate endDate) {
        List<Expense> expenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, startDate, endDate);

        Map<String, BigDecimal> breakdown = expenses.stream()
                .collect(Collectors.groupingBy(
                        expense -> expense.getCategory().name(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Expense::getAmount,
                                BigDecimal::add)));

        return CategoryReportDTO.builder()
                .breakdown(breakdown)
                .build();
    }

    /**
     * Get all fixed expense definitions with payment status for current month
     */
    public List<FixedExpenseDefinitionDTO> getFixedExpensesWithStatus(Long companyId) {
        List<FixedExpenseDefinition> definitions = fixedExpenseDefinitionRepository.findByCompanyId(companyId);

        // Get expenses for current month
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        List<Expense> monthExpenses = expenseRepository.findByCompanyIdAndDateBetween(
                companyId, startOfMonth, endOfMonth);

        // Convert to DTOs with payment status
        return definitions.stream().map(def -> {
            // Check if this fixed expense was paid this month (by matching description)
            boolean isPaid = monthExpenses.stream()
                    .anyMatch(expense -> expense.getDescription().startsWith(def.getName()));

            return FixedExpenseDefinitionDTO.builder()
                    .id(def.getId())
                    .companyId(def.getCompanyId())
                    .name(def.getName())
                    .defaultAmount(def.getDefaultAmount())
                    .category(def.getCategory().name())
                    .dayOfMonth(def.getDayOfMonth())
                    .description(def.getDescription())
                    .isPaidThisMonth(isPaid)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * @deprecated Use getFixedExpensesWithStatus instead
     */
    @Deprecated
    public List<FixedExpenseDefinition> getFixedExpenses(Long companyId) {
        return fixedExpenseDefinitionRepository.findByCompanyId(companyId);
    }

    /**
     * Pay a fixed expense - creates an Expense record from the definition
     */
    public Expense payFixedExpense(Long definitionId, Long companyId) {
        FixedExpenseDefinition definition = fixedExpenseDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Fixed expense definition not found: " + definitionId));

        // Create expense from definition
        Expense expense = Expense.builder()
                .companyId(companyId)
                .amount(definition.getDefaultAmount())
                .description(definition.getName()
                        + (definition.getDescription() != null ? " - " + definition.getDescription() : ""))
                .date(LocalDate.now())
                .category(definition.getCategory())
                .build();

        return expenseRepository.save(expense);
    }

    /**
     * Create a new fixed expense definition
     */
    public FixedExpenseDefinition createFixedExpense(FixedExpenseDefinition definition) {
        return fixedExpenseDefinitionRepository.save(definition);
    }

    /**
     * Update an existing fixed expense definition
     */
    public FixedExpenseDefinition updateFixedExpense(Long id, FixedExpenseDefinition definition) {
        FixedExpenseDefinition existing = fixedExpenseDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fixed expense definition not found: " + id));

        existing.setName(definition.getName());
        existing.setDefaultAmount(definition.getDefaultAmount());
        existing.setCategory(definition.getCategory());
        existing.setDayOfMonth(definition.getDayOfMonth());
        existing.setDescription(definition.getDescription());

        return fixedExpenseDefinitionRepository.save(existing);
    }

    /**
     * Delete a fixed expense definition
     */
    public void deleteFixedExpense(Long id) {
        fixedExpenseDefinitionRepository.deleteById(id);
    }

    /**
     * Get daily totals for last N days (for LineChart)
     */
    @Data
    @Builder
    public static class DailyTotal {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expense;
    }

    public List<DailyTotal> get30DayTotals(Long companyId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<DailyTotal> totals = new ArrayList<>();

        // Fetch all tickets and expenses in range once
        List<ServiceTicket> allTickets = ticketRepository.findByCompanyId(companyId);
        List<Expense> allExpenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, startDate, endDate);

        // Calculate totals for each day
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            LocalDateTime dayStart = currentDate.atStartOfDay();
            LocalDateTime dayEnd = currentDate.atTime(LocalTime.MAX);

            // Income for this day
            BigDecimal dailyIncome = allTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .filter(t -> {
                        LocalDateTime updatedAt = t.getUpdatedAt();
                        return updatedAt != null && !updatedAt.isBefore(dayStart) && !updatedAt.isAfter(dayEnd);
                    })
                    .map(ServiceTicket::getCollectedAmount)
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Expense for this day
            BigDecimal dailyExpense = allExpenses.stream()
                    .filter(e -> e.getDate().equals(currentDate))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totals.add(DailyTotal.builder()
                    .date(currentDate)
                    .income(dailyIncome)
                    .expense(dailyExpense)
                    .build());
        }

        return totals;
    }

    /**
     * Get fixed expenses that are due within the specified number of days
     * and have not been paid this month (for alert notifications)
     */
    public List<FixedExpenseDefinitionDTO> getUpcomingFixedExpenses(Long companyId, int daysThreshold) {
        List<FixedExpenseDefinition> allDefinitions = fixedExpenseDefinitionRepository.findByCompanyId(companyId);
        LocalDate today = LocalDate.now();

        // Get expenses for current month to check payment status
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        List<Expense> monthExpenses = expenseRepository.findByCompanyIdAndDateBetween(
                companyId, startOfMonth, endOfMonth);

        return allDefinitions.stream()
                .filter(def -> {
                    if (def.getDayOfMonth() == null)
                        return false;

                    // Check if already paid this month
                    boolean isPaid = monthExpenses.stream()
                            .anyMatch(expense -> expense.getDescription().startsWith(def.getName()));
                    if (isPaid)
                        return false;

                    // Calculate the next due date for this expense
                    LocalDate dueDate;
                    int dueDayOfMonth = def.getDayOfMonth();
                    int currentDayOfMonth = today.getDayOfMonth();

                    if (dueDayOfMonth >= currentDayOfMonth) {
                        // Due date is later this month or today
                        try {
                            dueDate = today.withDayOfMonth(dueDayOfMonth);
                        } catch (Exception e) {
                            // Handle invalid dates (e.g., Feb 30)
                            dueDate = today.withDayOfMonth(today.lengthOfMonth());
                        }
                    } else {
                        // Due date has passed this month, so it's next month
                        LocalDate nextMonth = today.plusMonths(1);
                        try {
                            dueDate = nextMonth.withDayOfMonth(dueDayOfMonth);
                        } catch (Exception e) {
                            // Handle invalid dates (e.g., Feb 30)
                            dueDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                        }
                    }

                    // Calculate days until due
                    long daysUntil = ChronoUnit.DAYS.between(today, dueDate);

                    // Check if due within threshold
                    return daysUntil >= 0 && daysUntil <= daysThreshold;
                })
                .map(def -> FixedExpenseDefinitionDTO.builder()
                        .id(def.getId())
                        .companyId(def.getCompanyId())
                        .name(def.getName())
                        .defaultAmount(def.getDefaultAmount())
                        .category(def.getCategory().name())
                        .dayOfMonth(def.getDayOfMonth())
                        .description(def.getDescription())
                        .isPaidThisMonth(false) // Already filtered out paid ones
                        .build())
                .collect(Collectors.toList());
    }
}
