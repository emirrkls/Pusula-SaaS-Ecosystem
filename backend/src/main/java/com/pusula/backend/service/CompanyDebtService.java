package com.pusula.backend.service;

import com.pusula.backend.dto.CompanyDebtDTO;
import com.pusula.backend.entity.CompanyDebt;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.repository.CompanyDebtRepository;
import com.pusula.backend.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyDebtService {

    private final CompanyDebtRepository debtRepository;
    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;

    /**
     * Get all debts for a company
     */
    public List<CompanyDebtDTO> getAllDebts(Long companyId) {
        return debtRepository.findByCompanyIdAndDeletedFalse(companyId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get unpaid debts only
     */
    public List<CompanyDebtDTO> getUnpaidDebts(Long companyId) {
        return debtRepository.findByCompanyIdAndStatusAndDeletedFalse(companyId, CompanyDebt.DebtStatus.UNPAID)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new debt record
     */
    @Transactional
    public CompanyDebtDTO createDebt(CompanyDebtDTO dto) {
        CompanyDebt debt = CompanyDebt.builder()
                .companyId(dto.getCompanyId())
                .creditorName(dto.getCreditorName())
                .description(dto.getDescription())
                .originalAmount(dto.getOriginalAmount())
                .remainingAmount(dto.getOriginalAmount())
                .debtDate(dto.getDebtDate() != null ? dto.getDebtDate() : LocalDate.now())
                .dueDate(dto.getDueDate())
                .creditorPhone(dto.getCreditorPhone())
                .status(CompanyDebt.DebtStatus.UNPAID)
                .notes(dto.getNotes())
                .build();

        CompanyDebt saved = debtRepository.save(debt);

        auditLogService.log("CREATE", "COMPANY_DEBT", saved.getId(),
                "Borç eklendi: " + saved.getCreditorName() + " (" + saved.getOriginalAmount() + " ₺)");

        return mapToDTO(saved);
    }

    /**
     * Update an existing debt
     */
    @Transactional
    public CompanyDebtDTO updateDebt(Long id, CompanyDebtDTO dto) {
        CompanyDebt debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borç bulunamadı: " + id));

        debt.setCreditorName(dto.getCreditorName());
        debt.setDescription(dto.getDescription());
        debt.setDueDate(dto.getDueDate());
        debt.setCreditorPhone(dto.getCreditorPhone());
        debt.setNotes(dto.getNotes());

        // Only update amounts if remaining is provided
        if (dto.getRemainingAmount() != null) {
            debt.setRemainingAmount(dto.getRemainingAmount());
        }

        return mapToDTO(debtRepository.save(debt));
    }

    /**
     * Pay debt (full or partial) - creates an expense record
     */
    @Transactional
    public CompanyDebtDTO payDebt(Long id, BigDecimal paymentAmount) {
        CompanyDebt debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borç bulunamadı: " + id));

        if (paymentAmount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Ödeme tutarı kalan borçtan fazla olamaz!");
        }

        // Create expense record for the payment
        Expense expense = Expense.builder()
                .companyId(debt.getCompanyId())
                .amount(paymentAmount)
                .description("Borç Ödemesi: " + debt.getCreditorName() +
                        (debt.getDescription() != null ? " - " + debt.getDescription() : ""))
                .date(LocalDate.now())
                .category(ExpenseCategory.OTHER)
                .build();
        expenseRepository.save(expense);

        // Update remaining amount
        debt.setRemainingAmount(debt.getRemainingAmount().subtract(paymentAmount));

        // Update status
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            debt.setStatus(CompanyDebt.DebtStatus.PAID);
        } else {
            debt.setStatus(CompanyDebt.DebtStatus.PARTIAL);
        }

        CompanyDebt saved = debtRepository.save(debt);

        auditLogService.log("UPDATE", "COMPANY_DEBT", saved.getId(),
                "Borç ödendi: " + saved.getCreditorName() + " (" + paymentAmount + " ₺)" +
                        (saved.getStatus() == CompanyDebt.DebtStatus.PAID ? " - TAMAMİ ÖDENDİ" : ""));

        return mapToDTO(saved);
    }

    /**
     * Add amount to existing debt (Top-Up)
     */
    @Transactional
    public CompanyDebtDTO addAmountToDebt(Long id, BigDecimal amountToAdd, String notes) {
        CompanyDebt debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borç bulunamadı: " + id));

        if (amountToAdd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Eklenecek tutar sıfırdan büyük olmalıdır!");
        }

        debt.setOriginalAmount(debt.getOriginalAmount().add(amountToAdd));
        debt.setRemainingAmount(debt.getRemainingAmount().add(amountToAdd));

        if (notes != null && !notes.isBlank()) {
            String currentDesc = debt.getDescription() == null ? "" : debt.getDescription() + " | ";
            debt.setDescription(currentDesc + "İlave Borç (" + amountToAdd + "₺): " + notes);
        } else {
            String currentDesc = debt.getDescription() == null ? "" : debt.getDescription() + " | ";
            debt.setDescription(currentDesc + "İlave Borç eklendi (" + amountToAdd + "₺)");
        }

        if (debt.getRemainingAmount().compareTo(debt.getOriginalAmount()) >= 0) {
            debt.setStatus(CompanyDebt.DebtStatus.UNPAID);
        } else {
            debt.setStatus(CompanyDebt.DebtStatus.PARTIAL);
        }

        CompanyDebt saved = debtRepository.save(debt);

        auditLogService.log("UPDATE", "COMPANY_DEBT", saved.getId(),
                "Borca ilave yapıldı: " + amountToAdd + " ₺. Yeni borç: " + saved.getRemainingAmount() + " ₺");

        return mapToDTO(saved);
    }

    /**
     * Delete a debt (soft delete)
     */
    @Transactional
    public void deleteDebt(Long id) {
        CompanyDebt debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borç bulunamadı: " + id));

        debt.setDeleted(true);
        debtRepository.save(debt);

        auditLogService.log("DELETE", "COMPANY_DEBT", id,
                "Borç silindi: " + debt.getCreditorName());
    }

    /**
     * Get total unpaid debt amount
     */
    public BigDecimal getTotalUnpaidDebt(Long companyId) {
        return debtRepository.findByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(d -> d.getStatus() != CompanyDebt.DebtStatus.PAID)
                .map(CompanyDebt::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CompanyDebtDTO mapToDTO(CompanyDebt debt) {
        return CompanyDebtDTO.builder()
                .id(debt.getId())
                .companyId(debt.getCompanyId())
                .creditorName(debt.getCreditorName())
                .description(debt.getDescription())
                .originalAmount(debt.getOriginalAmount())
                .remainingAmount(debt.getRemainingAmount())
                .debtDate(debt.getDebtDate())
                .dueDate(debt.getDueDate())
                .creditorPhone(debt.getCreditorPhone())
                .status(debt.getStatus().name())
                .notes(debt.getNotes())
                .createdAt(debt.getCreatedAt())
                .updatedAt(debt.getUpdatedAt())
                .build();
    }
}
