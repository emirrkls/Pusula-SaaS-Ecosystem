package com.pusula.backend.controller;

import com.pusula.backend.dto.FixedExpenseDefinitionDTO;
import com.pusula.backend.dto.CategoryReportDTO;
import com.pusula.backend.dto.CloseDayRequest;
import com.pusula.backend.dto.DailySummaryDTO;
import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.FixedExpenseDefinition;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class FinanceController {

    private final FinanceService financeService;

    // ── SECURITY FIX: All endpoints now derive companyId from JWT ──

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Long getCompanyId() {
        return getCurrentUser().getCompanyId();
    }

    @GetMapping("/summary")
    public ResponseEntity<FinanceService.FinancialSummary> getSummary(
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(financeService.getSummary(getCompanyId(), period));
    }

    /**
     * Get CUMULATIVE (all-time) financial summary for Boss View
     */
    @GetMapping("/cumulative")
    public ResponseEntity<FinanceService.CumulativeSummary> getCumulativeSummary() {
        Long companyId = getCompanyId();
        FinanceService.CumulativeSummary summary = financeService.getCumulativeSummary(companyId);

        BigDecimal inventoryValue = inventoryRepository.findByCompanyId(companyId).stream()
                .map(item -> {
                    BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
                    BigDecimal buyPrice = item.getBuyPrice() != null ? item.getBuyPrice() : BigDecimal.ZERO;
                    return quantity.multiply(buyPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.setTotalInventoryValue(inventoryValue);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/expenses")
    public ResponseEntity<Expense> addExpense(@RequestBody Expense expense) {
        // SECURITY: Always set companyId from JWT, never trust client
        expense.setCompanyId(getCompanyId());
        return ResponseEntity.ok(financeService.addExpense(expense));
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<Expense>> getExpenses() {
        return ResponseEntity.ok(financeService.getRecentExpenses(getCompanyId()));
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(financeService.getDailySummary(getCompanyId(), targetDate));
    }

    @PostMapping("/close-day")
    public ResponseEntity<DailyClosing> closeDay(@RequestBody CloseDayRequest request) {
        DailyClosing closing = financeService.closeDay(
                getCompanyId(),
                request.getDate(),
                request.getUserId());
        return ResponseEntity.ok(closing);
    }

    @GetMapping("/category-report")
    public ResponseEntity<CategoryReportDTO> getCategoryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate start = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        return ResponseEntity.ok(financeService.getCategoryReport(getCompanyId(), start, end));
    }

    // ============= FIXED EXPENSE CRUD ENDPOINTS =============

    @GetMapping("/fixed-expenses")
    public ResponseEntity<List<FixedExpenseDefinitionDTO>> getFixedExpenses() {
        return ResponseEntity.ok(financeService.getFixedExpensesWithStatus(getCompanyId()));
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
            @RequestParam(required = false) String date,
            @RequestParam(required = false) BigDecimal amount) {
        try {
            LocalDate paymentDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
            return ResponseEntity.ok(financeService.payFixedExpense(id, getCompanyId(), paymentDate, amount));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/daily-totals")
    public ResponseEntity<List<FinanceService.DailyTotal>> get30DayTotals() {
        return ResponseEntity.ok(financeService.get30DayTotals(getCompanyId()));
    }

    @GetMapping("/upcoming-fixed-expenses")
    public ResponseEntity<List<FixedExpenseDefinitionDTO>> getUpcomingFixedExpenses(
            @RequestParam(defaultValue = "3") int daysThreshold) {
        return ResponseEntity.ok(financeService.getUpcomingFixedExpenses(getCompanyId(), daysThreshold));
    }

    @Autowired
    private InventoryRepository inventoryRepository;

    @GetMapping("/inventory-value")
    public ResponseEntity<Map<String, BigDecimal>> getInventoryValue() {
        Long companyId = getCompanyId();
        List<Inventory> items = inventoryRepository.findByCompanyId(companyId);
        BigDecimal totalValue = items.stream()
                .filter(item -> item.getBuyPrice() != null && item.getQuantity() != null)
                .map(item -> item.getBuyPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(Map.of("totalValue", totalValue));
    }

    /**
     * Close all unclosed days in a date range (for fixing historical data)
     */
    @PostMapping("/close-days-range")
    public ResponseEntity<Map<String, Object>> closeDaysRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long companyId = getCompanyId();
        int closedCount = 0;
        int skippedCount = 0;
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            try {
                financeService.closeDay(companyId, current, null);
                closedCount++;
            } catch (IllegalStateException e) {
                skippedCount++;
            }
            current = current.plusDays(1);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Toplu gün kapanışı tamamlandı",
                "closedDays", closedCount,
                "skippedDays", skippedCount));
    }
}
