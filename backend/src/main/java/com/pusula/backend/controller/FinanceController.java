package com.pusula.backend.controller;

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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
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

    @GetMapping("/fixed-expenses")
    public ResponseEntity<List<FixedExpenseDefinition>> getFixedExpenses(
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(financeService.getFixedExpenses(companyId));
    }

    @PostMapping("/fixed-expenses/pay/{id}")
    public ResponseEntity<Expense> payFixedExpense(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(financeService.payFixedExpense(id, companyId));
    }

    @GetMapping("/daily-totals")
    public ResponseEntity<List<FinanceService.DailyTotal>> get30DayTotals(
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(financeService.get30DayTotals(companyId));
    }
}
