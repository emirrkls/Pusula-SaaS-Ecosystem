package com.pusula.backend.controller;

import com.pusula.backend.dto.CompanyDebtDTO;
import com.pusula.backend.dto.CompanyDebtPaymentDTO;
import com.pusula.backend.dto.DebtPaymentRequestDTO;
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
            ) {
        return ResponseEntity.ok(debtService.getAllDebts(getCompanyId()));
    }

    /**
     * Get only unpaid debts
     */
    @GetMapping("/unpaid")
    public ResponseEntity<List<CompanyDebtDTO>> getUnpaidDebts(
            ) {
        return ResponseEntity.ok(debtService.getUnpaidDebts(getCompanyId()));
    }

    /**
     * Get total unpaid debt amount
     */
    @GetMapping("/total-unpaid")
    public ResponseEntity<Map<String, BigDecimal>> getTotalUnpaidDebt(
            ) {
        Long companyId = getCompanyId();
        BigDecimal total = debtService.getTotalUnpaidDebt(companyId);
        return ResponseEntity.ok(Map.of("totalUnpaid", total));
    }

    /**
     * Create a new debt
     */
    @PostMapping
    public ResponseEntity<CompanyDebtDTO> createDebt(@RequestBody CompanyDebtDTO dto) {
        dto.setCompanyId(getCompanyId());
        return ResponseEntity.ok(debtService.createDebt(dto));
    }

    /**
     * Update an existing debt
     */
    @PutMapping("/{id}")
    public ResponseEntity<CompanyDebtDTO> updateDebt(
            @PathVariable Long id,
            @RequestBody CompanyDebtDTO dto) {
        return ResponseEntity.ok(debtService.updateDebt(id, getCompanyId(), dto));
    }

    /**
     * Pay a debt (creates expense record)
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payDebt(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal amount,
            @RequestBody(required = false) DebtPaymentRequestDTO request) {
        try {
            DebtPaymentRequestDTO effectiveRequest = request != null
                    ? request
                    : DebtPaymentRequestDTO.builder().amount(amount).build();
            CompanyDebtDTO updated = debtService.payDebt(id, getCompanyId(), effectiveRequest);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<CompanyDebtPaymentDTO>> getPayments(@PathVariable Long id) {
        return ResponseEntity.ok(debtService.getPayments(id, getCompanyId()));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ResponseEntity<CompanyDebtDTO> deletePayment(
            @PathVariable Long id,
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(debtService.deletePayment(id, paymentId, getCompanyId()));
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
            CompanyDebtDTO updated = debtService.addAmountToDebt(id, getCompanyId(), amount, notes);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a debt (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDebt(@PathVariable Long id) {
        try {
            debtService.deleteDebt(id, getCompanyId());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    private Long getCompanyId() {
        return ((com.pusula.backend.entity.User) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getCompanyId();
    }
}
