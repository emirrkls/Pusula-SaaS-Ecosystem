package com.pusula.backend.service;

import com.pusula.backend.dto.DebtPaymentRequestDTO;
import com.pusula.backend.entity.CompanyDebt;
import com.pusula.backend.entity.CompanyDebtPayment;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ExpenseTreatment;
import com.pusula.backend.repository.CompanyDebtPaymentRepository;
import com.pusula.backend.repository.CompanyDebtRepository;
import com.pusula.backend.repository.ExpenseRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyDebtServicePaymentDateTest {

    @Mock CompanyDebtRepository debtRepository;
    @Mock CompanyDebtPaymentRepository paymentRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock AuditLogService auditLogService;
    @Mock FinanceService financeService;

    private CompanyDebtService service;

    @BeforeEach
    void setUp() {
        service = new CompanyDebtService(debtRepository, paymentRepository, expenseRepository,
                auditLogService, financeService, "Europe/Istanbul");
    }

    @Test
    void partialPaymentUsesSelectedDateCategoryAndCreditorInReportExpense() {
        CompanyDebt debt = debt(new BigDecimal("100000.00"));
        LocalDate paymentDate = LocalDate.of(2026, 5, 15);
        when(debtRepository.findByIdAndCompanyIdAndDeletedFalse(20L, 7L)).thenReturn(Optional.of(debt));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(800L);
            return expense;
        });
        when(paymentRepository.save(any(CompanyDebtPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(debtRepository.save(any(CompanyDebt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.payDebt(20L, 7L, DebtPaymentRequestDTO.builder()
                .amount(new BigDecimal("30000.00"))
                .paymentDate(paymentDate)
                .notes("İlk taksit")
                .build());

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        Expense reportExpense = expenseCaptor.getValue();
        assertEquals(paymentDate, reportExpense.getDate());
        assertEquals(ExpenseCategory.MATERIAL, reportExpense.getCategory());
        assertEquals(ExpenseTreatment.CASH_ONLY, reportExpense.getFinancialTreatment());
        assertEquals("Borç Ödemesi: ABC Klima - Dış ünite alımı", reportExpense.getDescription());

        verify(paymentRepository).save(argThat(payment -> payment.getExpenseId().equals(800L)
                && payment.getPaymentDate().equals(paymentDate)
                && payment.getAmount().compareTo(new BigDecimal("30000.00")) == 0));
        assertEquals(new BigDecimal("70000.00"), debt.getRemainingAmount());
        assertEquals(CompanyDebt.DebtStatus.PARTIAL, debt.getStatus());
        verify(financeService).reconcileClosedDay(7L, paymentDate);
    }

    @Test
    void deletingPaymentRestoresDebtAndDeletesLinkedReportExpense() {
        CompanyDebt debt = debt(new BigDecimal("70000.00"));
        debt.setStatus(CompanyDebt.DebtStatus.PARTIAL);
        CompanyDebtPayment payment = CompanyDebtPayment.builder()
                .id(50L)
                .companyId(7L)
                .debtId(20L)
                .expenseId(800L)
                .amount(new BigDecimal("30000.00"))
                .paymentDate(LocalDate.of(2026, 5, 15))
                .expenseCategory(ExpenseCategory.MATERIAL)
                .build();
        when(debtRepository.findByIdAndCompanyIdAndDeletedFalse(20L, 7L)).thenReturn(Optional.of(debt));
        when(paymentRepository.findByIdAndDebtIdAndCompanyId(50L, 20L, 7L)).thenReturn(Optional.of(payment));
        when(debtRepository.save(any(CompanyDebt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deletePayment(20L, 50L, 7L);

        assertEquals(new BigDecimal("100000.00"), debt.getRemainingAmount());
        assertEquals(CompanyDebt.DebtStatus.UNPAID, debt.getStatus());
        verify(paymentRepository).delete(payment);
        verify(paymentRepository).flush();
        verify(expenseRepository).deleteById(800L);
        verify(financeService).reconcileClosedDay(7L, LocalDate.of(2026, 5, 15));
    }

    @Test
    void rejectsFuturePaymentWithoutWritingExpense() {
        CompanyDebt debt = debt(new BigDecimal("100000.00"));
        when(debtRepository.findByIdAndCompanyIdAndDeletedFalse(20L, 7L)).thenReturn(Optional.of(debt));

        assertThrows(IllegalArgumentException.class, () -> service.payDebt(20L, 7L,
                DebtPaymentRequestDTO.builder()
                        .amount(new BigDecimal("1000.00"))
                        .paymentDate(LocalDate.now().plusDays(1))
                        .build()));

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void debtWithPaymentHistoryCannotBeDeleted() {
        CompanyDebt debt = debt(new BigDecimal("70000.00"));
        when(debtRepository.findByIdAndCompanyIdAndDeletedFalse(20L, 7L)).thenReturn(Optional.of(debt));
        when(paymentRepository.existsByDebtIdAndCompanyId(20L, 7L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deleteDebt(20L, 7L));

        verify(debtRepository, never()).save(debt);
    }

    private CompanyDebt debt(BigDecimal remainingAmount) {
        return CompanyDebt.builder()
                .id(20L)
                .companyId(7L)
                .creditorName("ABC Klima")
                .description("Dış ünite alımı")
                .originalAmount(new BigDecimal("100000.00"))
                .remainingAmount(remainingAmount)
                .expenseCategory(ExpenseCategory.MATERIAL)
                .debtDate(LocalDate.of(2026, 5, 1))
                .status(CompanyDebt.DebtStatus.UNPAID)
                .deleted(false)
                .build();
    }
}
