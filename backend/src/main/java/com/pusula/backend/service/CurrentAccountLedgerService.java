package com.pusula.backend.service;

import com.pusula.backend.dto.CurrentAccountHistoryDTO;
import com.pusula.backend.dto.CurrentAccountTransactionDTO;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CurrentAccountTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CurrentAccountLedgerService {
    private final CurrentAccountRepository accountRepository;
    private final CurrentAccountTransactionRepository transactionRepository;

    public CurrentAccountLedgerService(CurrentAccountRepository accountRepository,
            CurrentAccountTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public void record(CurrentAccount account, CurrentAccountTransaction.TransactionType type,
            BigDecimal signedAmount, LocalDate effectiveDate, String description,
            PaymentMethod paymentMethod, String sourceType, Long sourceId) {
        if (account == null || signedAmount == null || signedAmount.signum() == 0) return;
        CurrentAccountTransaction transaction = new CurrentAccountTransaction();
        transaction.setCompanyId(account.getCompanyId());
        transaction.setCurrentAccountId(account.getId());
        transaction.setCustomerId(account.getCustomer().getId());
        transaction.setTransactionType(type);
        transaction.setAmount(signedAmount);
        transaction.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        transaction.setDescription(description);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setSourceType(sourceType);
        transaction.setSourceId(sourceId);
        transactionRepository.save(transaction);
    }

    public CurrentAccountHistoryDTO getHistory(Long accountId, Long companyId) {
        CurrentAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cari hesap bulunamadı."));
        List<CurrentAccountTransaction> rows = transactionRepository
                .findByCurrentAccountIdAndCompanyIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId, companyId);
        BigDecimal runningBalance = BigDecimal.ZERO;
        List<CurrentAccountTransactionDTO> mapped = new ArrayList<>();
        for (CurrentAccountTransaction row : rows) {
            runningBalance = runningBalance.add(row.getAmount());
            mapped.add(new CurrentAccountTransactionDTO(
                    row.getId(), row.getTransactionType().name(), row.getAmount(), runningBalance,
                    row.getEffectiveDate(), row.getDescription(),
                    row.getPaymentMethod() != null ? row.getPaymentMethod().name() : null,
                    row.getSourceType(), row.getSourceId(), row.getCreatedAt()));
        }
        Collections.reverse(mapped);
        return new CurrentAccountHistoryDTO(
                account.getId(), account.getCustomer().getId(), account.getCustomer().getName(),
                account.getBalance(), mapped);
    }
}
