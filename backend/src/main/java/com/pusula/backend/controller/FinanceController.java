package com.pusula.backend.controller;

import com.pusula.backend.dto.FixedExpenseDefinitionDTO;
import com.pusula.backend.dto.CategoryReportDTO;
import com.pusula.backend.dto.CloseDayRequest;
import com.pusula.backend.dto.DailySummaryDTO;
import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.FixedExpenseDefinition;
import com.pusula.backend.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceService.FinancialSummary> getSummary(
            @RequestParam(defaultValue = "1") Long companyId,
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(financeService.getSummary(companyId, period));
    }

    @PostMapping("/expenses")
    public ResponseEntity<Expense> addExpense(@RequestBody Expense expense) {
        // Ensure companyId is set (default to 1 for now if missing)
        if (expense.getCompanyId() == null) {
            expense.setCompanyId(1L);
        }
        return ResponseEntity.ok(financeService.addExpense(expense));
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<Expense>> getExpenses(@RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(financeService.getRecentExpenses(companyId));
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<Expense> updateExpense(
            @PathVariable Long id,
            @RequestBody Expense expense) {
        return ResponseEntity.ok(financeService.updateExpense(id, expense));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        financeService.deleteExpense(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<DailySummaryDTO> getDailySummary(
            @RequestParam(defaultValue = "1") Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(financeService.getDailySummary(companyId, targetDate));
    }

    @PostMapping("/close-day")
    public ResponseEntity<DailyClosing> closeDay(@RequestBody CloseDayRequest request) {
        DailyClosing closing = financeService.closeDay(
                request.getCompanyId(),
                request.getDate(),
                request.getUserId());
        return ResponseEntity.ok(closing);
    }

    @GetMapping("/category-report")
    public ResponseEntity<CategoryReportDTO> getCategoryReport(
            @RequestParam(defaultValue = "1") Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Default to current month if dates not provided
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        return ResponseEntity.ok(financeService.getCategoryReport(companyId, start, end));
    }

    // ============= FIXED EXPENSE CRUD ENDPOINTS =============

    @GetMapping("/fixed-expenses")
    public ResponseEntity<List<FixedExpenseDefinitionDTO>> getFixedExpenses(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(financeService.getFixedExpensesWithStatus(companyId));
    }

    @PostMapping("/fixed-expenses")
    public ResponseEntity<FixedExpenseDefinition> createFixedExpense(
            @RequestBody FixedExpenseDefinition definition) {
        return ResponseEntity.ok(financeService.createFixedExpense(definition));
    }

    @PutMapping("/fixed-expenses/{id}")
    public ResponseEntity<FixedExpenseDefinition> updateFixedExpense(
            @PathVariable Long id,
            @RequestBody FixedExpenseDefinition definition) {
        return ResponseEntity.ok(financeService.updateFixedExpense(id, definition));
    }

    @DeleteMapping("/fixed-expenses/{id}")
    public ResponseEntity<Void> deleteFixedExpense(@PathVariable Long id) {
        financeService.deleteFixedExpense(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fixed-expenses/pay/{id}")
    public ResponseEntity<?> payFixedExpense(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long companyId,
            @RequestParam(required = false) String date) {
        try {
            LocalDate paymentDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
            return ResponseEntity.ok(financeService.payFixedExpense(id, companyId, paymentDate));
        } catch (IllegalStateException e) {
            // Duplicate payment error
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/daily-totals")
    public ResponseEntity<List<FinanceService.DailyTotal>> get30DayTotals(
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(financeService.get30DayTotals(companyId));
    }

    @GetMapping("/upcoming-fixed-expenses")
    public ResponseEntity<List<FixedExpenseDefinitionDTO>> getUpcomingFixedExpenses(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "3") int daysThreshold) {
        return ResponseEntity.ok(financeService.getUpcomingFixedExpenses(companyId, daysThreshold));
    }
}
