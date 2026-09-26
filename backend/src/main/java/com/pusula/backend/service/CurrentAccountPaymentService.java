package com.pusula.backend.service;

import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.entity.FinancialTransaction;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CurrentAccountPaymentService {
    private final CurrentAccountRepository currentAccountRepository;
    private final CurrentAccountLedgerService ledgerService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinanceService financeService;
    private final AuditLogService auditLogService;

    @Transactional
    public CurrentAccount pay(Long accountId, Long companyId, Long userId,
            BigDecimal paymentAmount, BigDecimal discount, LocalDate collectionDate,
            PaymentMethod paymentMethod, String notes, String idempotencyKey) {
        validateNonNegative(paymentAmount, "Ödeme tutarı");
        validateNonNegative(discount, "İndirim");
        if (paymentMethod != PaymentMethod.CASH && paymentMethod != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("Cari tahsilat ödeme yöntemi nakit veya kart olmalıdır.");
        }

        CurrentAccount account = currentAccountRepository.findByIdAndCompanyIdForUpdate(accountId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cari hesap bulunamadı."));
        String cleanIdempotencyKey = idempotencyKey != null ? idempotencyKey.trim() : "";
        if (!cleanIdempotencyKey.isBlank()) {
            FinancialTransaction existing = financialTransactionRepository
                    .findByCompanyIdAndIdempotencyKey(companyId, cleanIdempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!accountId.equals(existing.getCurrentAccountId())) {
                    throw new IllegalArgumentException("Tahsilat istek anahtarı başka bir cari hesapta kullanılmış.");
                }
                return account;
            }
        }
        if (account.getParty() != null && !account.getParty().isActive()) {
            throw new IllegalArgumentException("Pasif cari karta tahsilat yapılamaz.");
        }

        BigDecimal totalReduction = paymentAmount.add(discount);
        if (totalReduction.signum() <= 0) {
            throw new IllegalArgumentException("Ödeme veya indirim tutarı sıfırdan büyük olmalıdır.");
        }
        if (totalReduction.compareTo(account.getBalance()) > 0) {
            throw new IllegalArgumentException("Ödeme ve indirim toplamı cari bakiyeyi aşamaz.");
        }

        LocalDate effectiveDate = collectionDate != null ? collectionDate : LocalDate.now();
        String cleanNotes = notes != null ? notes.trim() : "";
        String accountName = account.getParty() != null
                ? account.getParty().getDisplayName()
                : account.getCustomer() != null ? account.getCustomer().getName() : "Bilinmeyen cari";

        account.setBalance(account.getBalance().subtract(totalReduction));
        CurrentAccount saved = currentAccountRepository.save(account);

        if (paymentAmount.signum() > 0) {
            FinancialTransaction movement = new FinancialTransaction();
            movement.setCompanyId(companyId);
            movement.setDirection(FinancialTransaction.Direction.INCOME);
            movement.setCategory(FinancialTransaction.Category.CURRENT_ACCOUNT_COLLECTION);
            movement.setAmount(paymentAmount);
            movement.setEffectiveDate(effectiveDate);
            movement.setPaymentMethod(paymentMethod);
            movement.setPartyId(account.getParty() != null ? account.getParty().getId() : null);
            movement.setCurrentAccountId(account.getId());
            movement.setCounterpartyName(accountName);
            movement.setDescription("Cari hesap tahsilatı - " + accountName
                    + (cleanNotes.isBlank() ? "" : " - " + cleanNotes));
            movement.setSourceType("CURRENT_ACCOUNT");
            movement.setSourceId(account.getId());
            movement.setIdempotencyKey(cleanIdempotencyKey.isBlank() ? null : cleanIdempotencyKey);
            movement.setCreatedByUserId(userId);
            movement = financialTransactionRepository.save(movement);

            ledgerService.record(saved, CurrentAccountTransaction.TransactionType.PAYMENT,
                    paymentAmount.negate(), effectiveDate,
                    cleanNotes.isBlank() ? "Cari hesap tahsilatı" : "Cari hesap tahsilatı - " + cleanNotes,
                    paymentMethod, "FINANCIAL_TRANSACTION", movement.getId());
            financeService.reconcileClosedDay(companyId, effectiveDate);
            auditLogService.log("CREATE", "FINANCIAL_TRANSACTION", movement.getId(),
                    accountName + " carisinden " + paymentAmount + " ₺ tahsil edildi.");
        }

        if (discount.signum() > 0) {
            ledgerService.record(saved, CurrentAccountTransaction.TransactionType.DISCOUNT,
                    discount.negate(), effectiveDate,
                    cleanNotes.isBlank() ? "Cari hesap indirimi" : "Cari hesap indirimi - " + cleanNotes,
                    null, null, null);
        }
        return saved;
    }

    private void validateNonNegative(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(label + " negatif olamaz.");
        }
    }
}
