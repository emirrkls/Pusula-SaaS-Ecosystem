package com.pusula.backend.controller;

import com.pusula.backend.dto.CompanyDebtDTO;
import com.pusula.backend.service.CompanyDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company-debts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class CompanyDebtController {

    private final CompanyDebtService debtService;

    /**
     * Get all debts for a company
     */
    @GetMapping
    public ResponseEntity<List<CompanyDebtDTO>> getAllDebts(
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(debtService.getAllDebts(companyId));
    }

    /**
     * Get only unpaid debts
     */
    @GetMapping("/unpaid")
    public ResponseEntity<List<CompanyDebtDTO>> getUnpaidDebts(
            @RequestParam(defaultValue = "1") Long companyId) {
        return ResponseEntity.ok(debtService.getUnpaidDebts(companyId));
    }

    /**
     * Get total unpaid debt amount
     */
    @GetMapping("/total-unpaid")
    public ResponseEntity<Map<String, BigDecimal>> getTotalUnpaidDebt(
            @RequestParam(defaultValue = "1") Long companyId) {
        BigDecimal total = debtService.getTotalUnpaidDebt(companyId);
        return ResponseEntity.ok(Map.of("totalUnpaid", total));
    }

    /**
     * Create a new debt
     */
    @PostMapping
    public ResponseEntity<CompanyDebtDTO> createDebt(@RequestBody CompanyDebtDTO dto) {
        if (dto.getCompanyId() == null) {
            dto.setCompanyId(1L);
        }
        return ResponseEntity.ok(debtService.createDebt(dto));
    }

    /**
     * Update an existing debt
     */
    @PutMapping("/{id}")
    public ResponseEntity<CompanyDebtDTO> updateDebt(
            @PathVariable Long id,
            @RequestBody CompanyDebtDTO dto) {
        return ResponseEntity.ok(debtService.updateDebt(id, dto));
    }

    /**
     * Pay a debt (creates expense record)
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payDebt(
            @PathVariable Long id,
            @RequestParam BigDecimal amount) {
        try {
            CompanyDebtDTO updated = debtService.payDebt(id, amount);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add amount to existing debt
     */
    @PostMapping("/{id}/add")
    public ResponseEntity<?> addAmountToDebt(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String notes) {
        try {
            CompanyDebtDTO updated = debtService.addAmountToDebt(id, amount, notes);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a debt (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebt(@PathVariable Long id) {
        debtService.deleteDebt(id);
        return ResponseEntity.ok().build();
    }
}
