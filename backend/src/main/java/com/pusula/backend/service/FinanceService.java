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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

        private final ServiceTicketRepository ticketRepository;
        private final ExpenseRepository expenseRepository;
        private final DailyClosingRepository dailyClosingRepository;
        private final CustomerRepository customerRepository;
        private final FixedExpenseDefinitionRepository fixedExpenseDefinitionRepository;
        private final AuditLogService auditLogService;

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
                                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
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

        /**
         * Get CUMULATIVE (all-time) financial summary for Boss View cards
         * This dynamically calculates totalCash from ALL completed tickets minus ALL
         * expenses
         */
        @Data
        @Builder
        public static class CumulativeSummary {
                private BigDecimal totalCash; // All-time income - expenses
                private BigDecimal totalInventoryValue; // Will be calculated separately
        }

        public CumulativeSummary getCumulativeSummary(Long companyId) {
                // Calculate ALL-TIME Income from COMPLETED tickets
                // EXCLUDE CURRENT_ACCOUNT payments (not liquid cash - creates debt instead)
                List<ServiceTicket> allTickets = ticketRepository.findByCompanyId(companyId);

                BigDecimal totalIncome = allTickets.stream()
                                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                                .filter(t -> t.getPaymentMethod() != com.pusula.backend.entity.PaymentMethod.CURRENT_ACCOUNT) // Exclude
                                                                                                                              // credit
                                .map(ServiceTicket::getCollectedAmount)
                                .filter(amount -> amount != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calculate ALL-TIME PAID Expenses
                // These are expenses that actually left the cash register
                List<Expense> allExpenses = expenseRepository.findByCompanyId(companyId);

                BigDecimal totalExpenses = allExpenses.stream()
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calculate netCash = Liquid Income - Paid Expenses
                // This should show negative if more money went out than came in
                BigDecimal netCash = totalIncome.subtract(totalExpenses);

                return CumulativeSummary.builder()
                                .totalCash(netCash)
                                .totalInventoryValue(BigDecimal.ZERO) // Will be set by controller
                                .build();
        }

        public Expense addExpense(Expense expense) {
                Expense saved = expenseRepository.save(expense);

                // Log expense creation
                auditLogService.log("CREATE", "EXPENSE", saved.getId(),
                                "Gider eklendi: " + saved.getDescription() + " (" + saved.getAmount() + " ₺)");

                return saved;
        }

        public List<Expense> getRecentExpenses(Long companyId) {
                // Just return all for now, or last 50
                return expenseRepository.findByCompanyId(companyId);
        }

        public Expense updateExpense(Long id, Expense updatedExpense) {
                Expense existing = expenseRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));

                existing.setDate(updatedExpense.getDate());
                existing.setAmount(updatedExpense.getAmount());
                existing.setCategory(updatedExpense.getCategory());
                existing.setDescription(updatedExpense.getDescription());

                return expenseRepository.save(existing);
        }

        public void deleteExpense(Long id) {
                // Log before deletion
                Expense expense = expenseRepository.findById(id).orElse(null);
                if (expense != null) {
                        auditLogService.log("DELETE", "EXPENSE", id,
                                        "Gider silindi: " + expense.getDescription() + " (" + expense.getAmount()
                                                        + " ₺)");
                }
                expenseRepository.deleteById(id);
        }

        /**
         * Get daily summary for a specific date
         */
        public DailySummaryDTO getDailySummary(Long companyId, LocalDate date) {
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

                // Calculate Income from completed tickets on this date
                // EXCLUDE CURRENT_ACCOUNT payments (not liquid cash - those become income when
                // customer pays)
                List<ServiceTicket> tickets = ticketRepository.findByCompanyId(companyId);
                List<ServiceTicket> completedTicketsToday = tickets.stream()
                                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                                .filter(t -> t.getPaymentMethod() != com.pusula.backend.entity.PaymentMethod.CURRENT_ACCOUNT) // Exclude
                                                                                                                              // credit/debt
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

                // Build income details with customer names
                List<DailySummaryDTO.IncomeItemDTO> incomeDetails = new ArrayList<>();
                for (ServiceTicket ticket : completedTicketsToday) {
                        String customerName = "Unknown";

                        if (ticket.getCustomerId() != null) {
                                customerName = customerRepository.findById(ticket.getCustomerId())
                                                .map(customer -> customer.getName())
                                                .orElse("Unknown");
                        }

                        incomeDetails.add(DailySummaryDTO.IncomeItemDTO.builder()
                                        .ticketId(ticket.getId())
                                        .customerName(customerName)
                                        .amount(ticket.getCollectedAmount() != null ? ticket.getCollectedAmount()
                                                        : BigDecimal.ZERO)
                                        .build());
                }

                // Get all expenses for this date
                List<Expense> allExpenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, date, date);

                // Separate DEVICE_SALE (income) from regular expenses
                List<Expense> deviceSales = allExpenses.stream()
                                .filter(e -> ExpenseCategory.DEVICE_SALE.equals(e.getCategory()))
                                .collect(Collectors.toList());

                List<Expense> regularExpenses = allExpenses.stream()
                                .filter(e -> !ExpenseCategory.DEVICE_SALE.equals(e.getCategory()))
                                .collect(Collectors.toList());

                // Add DEVICE_SALE as income (convert negative to positive)
                for (Expense sale : deviceSales) {
                        BigDecimal saleAmount = sale.getAmount().negate(); // Stored as negative, show as positive
                        totalIncome = totalIncome.add(saleAmount);
                        incomeDetails.add(DailySummaryDTO.IncomeItemDTO.builder()
                                        .ticketId(null) // No ticket ID for sales
                                        .customerName("Satış: " + sale.getDescription().replace("Satış: ", ""))
                                        .amount(saleAmount)
                                        .build());
                }

                // Calculate Expenses (excluding DEVICE_SALE)
                BigDecimal totalExpense = regularExpenses.stream()
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Build expense details (excluding DEVICE_SALE)
                List<DailySummaryDTO.ExpenseItemDTO> expenseDetails = regularExpenses.stream()
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

                DailyClosing saved = dailyClosingRepository.save(closing);

                // Log day closing
                auditLogService.log("CREATE", "DAY_CLOSING", saved.getId(),
                                "Gün kapatıldı: " + date + " | Gelir: " + summary.getTotalIncome() + " ₺ | Gider: "
                                                + summary.getTotalExpense() + " ₺ | Net: " + summary.getNetCash()
                                                + " ₺");

                return saved;
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

                // Debug logging
                log.debug("getFixedExpensesWithStatus: month={} to {}, expenses={}",
                                startOfMonth, endOfMonth, monthExpenses.size());

                // Convert to DTOs with payment status
                // First create a map of expense definitions by ID for quick lookup
                java.util.Map<Long, FixedExpenseDefinition> defMap = definitions.stream()
                                .collect(java.util.stream.Collectors.toMap(FixedExpenseDefinition::getId, d -> d));

                return definitions.stream().map(def -> {
                        // Calculate total paid this month using fixedExpenseId
                        BigDecimal paidAmount = monthExpenses.stream()
                                .filter(expense -> def.getId().equals(expense.getFixedExpenseId()))
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        // If fixedExpenseId was not set (legacy data), fallback to string match
                        BigDecimal legacyPaidAmount = monthExpenses.stream()
                                .filter(expense -> expense.getFixedExpenseId() == null)
                                .filter(expense -> {
                                        String expDesc = expense.getDescription() != null
                                                        ? expense.getDescription().toLowerCase()
                                                        : "";
                                        String defName = def.getName() != null ? def.getName().toLowerCase()
                                                        : "";
                                        return expDesc.contains(defName) || expDesc.startsWith(defName);
                                })
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal totalPaid = paidAmount.add(legacyPaidAmount);
                        boolean isPaid = totalPaid.compareTo(def.getDefaultAmount()) >= 0;

                        // Calculate linked payments (for monthly expenses linked to weekly)
                        BigDecimal linkedPaymentsThisMonth = BigDecimal.ZERO;
                        String linkedExpenseName = null;

                        if (def.getLinkedExpenseId() != null) {
                                FixedExpenseDefinition linkedDef = defMap.get(def.getLinkedExpenseId());
                                if (linkedDef != null) {
                                        linkedExpenseName = linkedDef.getName();
                                        // Sum all payments from the linked expense this month
                                        linkedPaymentsThisMonth = monthExpenses.stream()
                                                        .filter(expense -> linkedDef.getId().equals(expense.getFixedExpenseId()))
                                                        .map(Expense::getAmount)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        
                                        // Legacy string match for linked payments
                                        BigDecimal legacyLinkedPayments = monthExpenses.stream()
                                                        .filter(expense -> expense.getFixedExpenseId() == null)
                                                        .filter(expense -> {
                                                                String expDesc = expense.getDescription() != null
                                                                                ? expense.getDescription().toLowerCase()
                                                                                : "";
                                                                String linkedName = linkedDef.getName() != null
                                                                                ? linkedDef.getName().toLowerCase()
                                                                                : "";
                                                                return expDesc.contains(linkedName)
                                                                                || expDesc.startsWith(linkedName);
                                                        })
                                                        .map(Expense::getAmount)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        linkedPaymentsThisMonth = linkedPaymentsThisMonth.add(legacyLinkedPayments);
                                }
                        }

                        return FixedExpenseDefinitionDTO.builder()
                                        .id(def.getId())
                                        .companyId(def.getCompanyId())
                                        .name(def.getName())
                                        .defaultAmount(def.getDefaultAmount())
                                        .category(def.getCategory().name())
                                        .dayOfMonth(def.getDayOfMonth())
                                        .description(def.getDescription())
                                        .paidThisMonth(isPaid)
                                        .paidAmountThisMonth(totalPaid)
                                        .frequency(def.getFrequency() != null ? def.getFrequency().name() : "MONTHLY")
                                        .linkedExpenseId(def.getLinkedExpenseId())
                                        .linkedExpenseName(linkedExpenseName)
                                        .linkedPaymentsThisMonth(linkedPaymentsThisMonth)
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
         * 
         * @param customAmount Optional - if provided, uses this amount instead of the
         *                     default
         */
        public Expense payFixedExpense(Long definitionId, Long companyId, LocalDate paymentDate,
                        BigDecimal customAmount) {
                FixedExpenseDefinition definition = fixedExpenseDefinitionRepository.findById(definitionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Fixed expense definition not found: " + definitionId));

                // Check if this expense has already been paid in the target month
                LocalDate startOfMonth = paymentDate.withDayOfMonth(1);
                LocalDate endOfMonth = paymentDate.withDayOfMonth(paymentDate.lengthOfMonth());

                List<Expense> monthExpenses = expenseRepository.findByCompanyIdAndDateBetween(
                                companyId, startOfMonth, endOfMonth);

                BigDecimal totalPaidThisMonth = monthExpenses.stream()
                                .filter(e -> definition.getId().equals(e.getFixedExpenseId()))
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Legacy fallback for already paid
                BigDecimal legacyPaid = monthExpenses.stream()
                                .filter(e -> e.getFixedExpenseId() == null && e.getDescription().startsWith(definition.getName() + " ("))
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalPaidThisMonth = totalPaidThisMonth.add(legacyPaid);

                if (totalPaidThisMonth.compareTo(definition.getDefaultAmount()) >= 0) {
                        java.time.format.DateTimeFormatter monthFormatter = java.time.format.DateTimeFormatter
                                        .ofPattern("MMMM yyyy", new java.util.Locale("tr", "TR"));
                        String monthYear = paymentDate.format(monthFormatter);
                        throw new IllegalStateException(
                                        definition.getName() + " zaten " + monthYear + " ayı için tamamen ödendi!");
                }

                // Use custom amount if provided, otherwise use the remaining amount
                BigDecimal remainingAmount = definition.getDefaultAmount().subtract(totalPaidThisMonth);
                if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;
                
                BigDecimal paymentAmount = (customAmount != null && customAmount.compareTo(BigDecimal.ZERO) > 0)
                                ? customAmount
                                : (remainingAmount.compareTo(BigDecimal.ZERO) > 0 ? remainingAmount : definition.getDefaultAmount());

                // Format description with month and year (e.g., "Kira (Kasım 2025)")
                java.time.format.DateTimeFormatter monthFormatter = java.time.format.DateTimeFormatter
                                .ofPattern("MMMM yyyy", new java.util.Locale("tr", "TR"));
                String monthYear = paymentDate.format(monthFormatter);

                // Create expense from definition
                Expense expense = Expense.builder()
                                .companyId(companyId)
                                .amount(paymentAmount)
                                .description(definition.getName() + " (" + monthYear + ")")
                                .date(paymentDate)
                                .category(definition.getCategory())
                                .fixedExpenseId(definition.getId())
                                .build();

                Expense saved = expenseRepository.save(expense);

                // Log fixed expense payment
                auditLogService.log("CREATE", "EXPENSE", saved.getId(),
                                "Sabit gider ödendi: " + definition.getName() + " (" + paymentAmount + " ₺)");

                return saved;
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
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Fixed expense definition not found: " + id));

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
                List<Expense> allExpenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, startDate,
                                endDate);

                // Calculate totals for each day
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                        LocalDate currentDate = date;
                        LocalDateTime dayStart = currentDate.atStartOfDay();
                        LocalDateTime dayEnd = currentDate.atTime(LocalTime.MAX);

                        // Income for this day
                        // EXCLUDE CURRENT_ACCOUNT payments (not liquid cash - those become income when
                        // customer pays)
                        BigDecimal dailyIncome = allTickets.stream()
                                        .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                                        .filter(t -> t.getPaymentMethod() != com.pusula.backend.entity.PaymentMethod.CURRENT_ACCOUNT)
                                        .filter(t -> {
                                                LocalDateTime updatedAt = t.getUpdatedAt();
                                                return updatedAt != null && !updatedAt.isBefore(dayStart)
                                                                && !updatedAt.isAfter(dayEnd);
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
                List<FixedExpenseDefinition> allDefinitions = fixedExpenseDefinitionRepository
                                .findByCompanyId(companyId);
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
                                        BigDecimal totalPaidThisMonth = monthExpenses.stream()
                                                        .filter(e -> def.getId().equals(e.getFixedExpenseId()))
                                                        .map(Expense::getAmount)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                        BigDecimal legacyPaid = monthExpenses.stream()
                                                        .filter(e -> e.getFixedExpenseId() == null && e.getDescription().startsWith(def.getName()))
                                                        .map(Expense::getAmount)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                        if (totalPaidThisMonth.add(legacyPaid).compareTo(def.getDefaultAmount()) >= 0) {
                                                return false; // Paid
                                        }

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
                                                .paidThisMonth(false) // Already filtered out paid ones
                                                .build())
                                .collect(Collectors.toList());
        }
}
