package com.pusula.backend.service;

import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CurrentAccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentAccountLedgerServiceTest {
    @Mock CurrentAccountRepository accountRepository;
    @Mock CurrentAccountTransactionRepository transactionRepository;

    @Test
    void historyIsNewestFirstAndKeepsChronologicalRunningBalance() {
        Customer customer = Customer.builder().id(4L).companyId(7L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder().id(9L).companyId(7L).customer(customer)
                .balance(new BigDecimal("600.00")).build();
        CurrentAccountTransaction charge = transaction(1L, "1000.00", "CHARGE", "2026-07-01");
        CurrentAccountTransaction payment = transaction(2L, "-400.00", "PAYMENT", "2026-07-03");
        when(accountRepository.findByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByCurrentAccountIdAndCompanyIdOrderByEffectiveDateAscCreatedAtAscIdAsc(9L, 7L))
                .thenReturn(List.of(charge, payment));

        var result = new CurrentAccountLedgerService(accountRepository, transactionRepository).getHistory(9L, 7L);

        assertEquals(2, result.transactions().size());
        assertEquals("PAYMENT", result.transactions().get(0).type());
        assertEquals(new BigDecimal("600.00"), result.transactions().get(0).balanceAfter());
        assertEquals(new BigDecimal("1000.00"), result.transactions().get(1).balanceAfter());
    }

    private CurrentAccountTransaction transaction(Long id, String amount, String type, String date) {
        CurrentAccountTransaction transaction = new CurrentAccountTransaction();
        ReflectionTestUtils.setField(transaction, "id", id);
        ReflectionTestUtils.setField(transaction, "createdAt", LocalDateTime.parse(date + "T12:00:00"));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionType(CurrentAccountTransaction.TransactionType.valueOf(type));
        transaction.setEffectiveDate(LocalDate.parse(date));
        return transaction;
    }
}
