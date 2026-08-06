package com.pusula.backend.service;

import com.pusula.backend.dto.DailySummaryDTO;
import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceCollectionDateTest {
    @Mock ServiceTicketRepository ticketRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock DailyClosingRepository dailyClosingRepository;
    @Mock CustomerRepository customerRepository;
    @Mock FixedExpenseDefinitionRepository fixedExpenseDefinitionRepository;
    @Mock AuditLogService auditLogService;
    @Mock CompanyDebtPaymentRepository companyDebtPaymentRepository;
    @Mock ServiceTicketExpenseRepository serviceTicketExpenseRepository;

    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(ticketRepository, expenseRepository, dailyClosingRepository,
                customerRepository, fixedExpenseDefinitionRepository, auditLogService,
                companyDebtPaymentRepository, serviceTicketExpenseRepository);
    }

    @Test
    void dailyIncomeUsesCollectionDateNotTechnicalUpdateDate() {
        LocalDate historicalDate = LocalDate.now().minusDays(5);
        ServiceTicket ticket = completedCashTicket(new BigDecimal("750.00"), historicalDate);
        ticket.setUpdatedAt(LocalDateTime.now());
        when(ticketRepository.findByCompanyId(10L)).thenReturn(List.of(ticket));
        when(expenseRepository.findByCompanyIdAndDateBetween(10L, historicalDate, historicalDate))
                .thenReturn(List.of());

        DailySummaryDTO historical = service.getDailySummary(10L, historicalDate);
        DailySummaryDTO today = service.getDailySummary(10L, LocalDate.now());

        assertEquals(new BigDecimal("750.00"), historical.getTotalIncome());
        assertEquals(BigDecimal.ZERO, today.getTotalIncome());
    }

    @Test
    void backdatedIncomeReconcilesAlreadyClosedSnapshot() {
        LocalDate historicalDate = LocalDate.now().minusDays(5);
        ServiceTicket ticket = completedCashTicket(new BigDecimal("750.00"), historicalDate);
        DailyClosing closing = DailyClosing.builder()
                .id(9L)
                .companyId(10L)
                .date(historicalDate)
                .status(DailyClosing.ClosingStatus.CLOSED)
                .totalIncome(BigDecimal.ZERO)
                .totalExpense(BigDecimal.ZERO)
                .netCash(BigDecimal.ZERO)
                .build();
        when(dailyClosingRepository.findByCompanyIdAndDate(10L, historicalDate))
                .thenReturn(Optional.of(closing));
        when(ticketRepository.findByCompanyId(10L)).thenReturn(List.of(ticket));
        when(expenseRepository.findByCompanyIdAndDateBetween(10L, historicalDate, historicalDate))
                .thenReturn(List.of());
        when(dailyClosingRepository.existsByCompanyIdAndDateAndStatus(
                10L, historicalDate, DailyClosing.ClosingStatus.CLOSED)).thenReturn(true);

        service.reconcileClosedDay(10L, historicalDate);

        assertEquals(new BigDecimal("750.00"), closing.getTotalIncome());
        assertEquals(new BigDecimal("750.00"), closing.getNetCash());
        verify(dailyClosingRepository).save(closing);
        verify(auditLogService).log(eq("RECONCILE"), eq("DAY_CLOSING"), eq(9L), anyString());
    }

    @Test
    void linkedDebtPaymentExpenseCannotBeDeletedFromGenericFinanceFlow() {
        when(companyDebtPaymentRepository.existsByExpenseId(88L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deleteExpense(88L));

        verify(expenseRepository, never()).deleteById(88L);
    }

    private ServiceTicket completedCashTicket(BigDecimal amount, LocalDate collectionDate) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(100L);
        ticket.setCompanyId(10L);
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setPaymentMethod(PaymentMethod.CASH);
        ticket.setCollectedAmount(amount);
        ticket.setCompletedAt(collectionDate.atTime(12, 0));
        ticket.setCollectionDate(collectionDate);
        return ticket;
    }
}
