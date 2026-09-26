package com.pusula.backend.service;

import com.pusula.backend.entity.AccountParty;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.entity.FinancialTransaction;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentAccountPaymentServiceTest {
    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock CurrentAccountLedgerService ledgerService;
    @Mock FinancialTransactionRepository financialTransactionRepository;
    @Mock FinanceService financeService;
    @Mock AuditLogService auditLogService;

    private CurrentAccountPaymentService service;

    @BeforeEach
    void setUp() {
        service = new CurrentAccountPaymentService(currentAccountRepository, ledgerService,
                financialTransactionRepository, financeService, auditLogService);
    }

    @Test
    void organizationCollectionDoesNotRequireSyntheticCustomerOrServiceTicket() {
        AccountParty institution = AccountParty.builder()
                .id(486L).companyId(1L).partyType(AccountParty.PartyType.ORGANIZATION)
                .displayName("Termodinamik Isıtma Sistemleri")
                .normalizedName("termodinamik isitma sistemleri").active(true).build();
        CurrentAccount account = CurrentAccount.builder()
                .id(38L).companyId(1L).party(institution).customer(null)
                .balance(new BigDecimal("3125.00")).version(0L).build();
        when(currentAccountRepository.findByIdAndCompanyIdForUpdate(38L, 1L))
                .thenReturn(Optional.of(account));
        when(currentAccountRepository.save(account)).thenReturn(account);
        when(financialTransactionRepository.save(any())).thenAnswer(invocation -> {
            FinancialTransaction movement = invocation.getArgument(0);
            movement.setId(77L);
            return movement;
        });

        CurrentAccount result = service.pay(38L, 1L, 9L,
                new BigDecimal("1000.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 9, 26), PaymentMethod.CREDIT_CARD, "Eylül ödemesi",
                "collection-request-1");

        assertEquals(new BigDecimal("2125.00"), result.getBalance());
        ArgumentCaptor<FinancialTransaction> movementCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(movementCaptor.capture());
        FinancialTransaction movement = movementCaptor.getValue();
        assertEquals(486L, movement.getPartyId());
        assertEquals(38L, movement.getCurrentAccountId());
        assertEquals("Termodinamik Isıtma Sistemleri", movement.getCounterpartyName());
        assertEquals(new BigDecimal("1000.00"), movement.getAmount());
        assertEquals(FinancialTransaction.Category.CURRENT_ACCOUNT_COLLECTION, movement.getCategory());
        assertEquals("collection-request-1", movement.getIdempotencyKey());
        verify(ledgerService).record(account, CurrentAccountTransaction.TransactionType.PAYMENT,
                new BigDecimal("-1000.00"), LocalDate.of(2026, 9, 26),
                "Cari hesap tahsilatı - Eylül ödemesi", PaymentMethod.CREDIT_CARD,
                "FINANCIAL_TRANSACTION", 77L);
        verify(financeService).reconcileClosedDay(1L, LocalDate.of(2026, 9, 26));
    }

    @Test
    void repeatedClientRequestDoesNotReduceBalanceTwice() {
        AccountParty institution = AccountParty.builder()
                .id(488L).companyId(16L).partyType(AccountParty.PartyType.ORGANIZATION)
                .displayName("ASEM SİSTEM").normalizedName("asem sistem").active(true).build();
        CurrentAccount account = CurrentAccount.builder()
                .id(40L).companyId(16L).party(institution)
                .balance(new BigDecimal("3000.00")).version(0L).build();
        FinancialTransaction existing = new FinancialTransaction();
        existing.setId(80L);
        existing.setCompanyId(16L);
        existing.setCurrentAccountId(40L);
        existing.setIdempotencyKey("same-request");
        when(currentAccountRepository.findByIdAndCompanyIdForUpdate(40L, 16L))
                .thenReturn(Optional.of(account));
        when(financialTransactionRepository.findByCompanyIdAndIdempotencyKey(16L, "same-request"))
                .thenReturn(Optional.of(existing));

        CurrentAccount result = service.pay(40L, 16L, 7L,
                new BigDecimal("3000.00"), BigDecimal.ZERO, LocalDate.of(2026, 9, 26),
                PaymentMethod.CASH, "", "same-request");

        assertEquals(new BigDecimal("3000.00"), result.getBalance());
        verify(currentAccountRepository, never()).save(any());
        verify(financialTransactionRepository, never()).save(any());
        verifyNoInteractions(ledgerService, financeService, auditLogService);
    }
}
