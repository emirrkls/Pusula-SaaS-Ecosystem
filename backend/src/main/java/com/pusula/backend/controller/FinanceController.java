package com.pusula.backend.controller;

import com.pusula.backend.entity.Expense;
import com.pusula.backend.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
