package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketExpenseDTO;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceTicketExpense;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.repository.ServiceTicketExpenseRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTicketExpenseServiceTest {

    @Mock ServiceTicketExpenseRepository repository;
    @Mock ExpenseRepository expenseRepository;
    @Mock ServiceTicketRepository ticketRepository;
    @Mock AuditLogService auditLogService;
    @Mock FinanceService financeService;

    private ServiceTicketExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketExpenseService(repository, expenseRepository, ticketRepository,
                auditLogService, financeService, "Europe/Istanbul");
        User user = new User();
        user.setId(1L);
        user.setCompanyId(7L);
        user.setRole("COMPANY_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addedExpenseUsesTicketCompletionDateAndServerSideCompany() {
        LocalDate completionDate = LocalDate.of(2026, 6, 15);
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(101L);
        ticket.setCompanyId(7L);
        ticket.setCompletedAt(completionDate.atTime(16, 30));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(expenseRepository.save(org.mockito.ArgumentMatchers.any(Expense.class)))
                .thenAnswer(invocation -> {
                    Expense expense = invocation.getArgument(0);
                    expense.setId(901L);
                    return expense;
                });
        when(repository.save(org.mockito.ArgumentMatchers.any(ServiceTicketExpense.class)))
                .thenAnswer(invocation -> {
                    ServiceTicketExpense expense = invocation.getArgument(0);
                    expense.setId(501L);
                    return expense;
                });

        ServiceTicketExpenseDTO result = service.addExpense(ServiceTicketExpenseDTO.builder()
                .serviceTicketId(101L)
                .companyId(999L)
                .description("Dış kompresör")
                .amount(new BigDecimal("10000.00"))
                .build());

        assertEquals(7L, result.getCompanyId());
        assertEquals(completionDate, result.getExpenseDate());
        verify(expenseRepository).save(org.mockito.ArgumentMatchers.argThat(expense ->
                expense.getCompanyId().equals(7L) && expense.getDate().equals(completionDate)));
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(expense ->
                expense.getFinanceExpenseId().equals(901L)
                        && expense.getExpenseDate().equals(completionDate)));
        verify(financeService).reconcileClosedDay(7L, completionDate);
    }

    @Test
    void deletingExpenseAlsoDeletesLinkedFinanceRow() {
        LocalDate expenseDate = LocalDate.of(2026, 5, 20);
        ServiceTicketExpense expense = ServiceTicketExpense.builder()
                .id(501L)
                .serviceTicketId(101L)
                .companyId(7L)
                .description("Dış parça")
                .amount(new BigDecimal("2500.00"))
                .expenseDate(expenseDate)
                .financeExpenseId(901L)
                .build();
        when(repository.findByIdAndServiceTicketId(501L, 101L)).thenReturn(Optional.of(expense));
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(101L);
        ticket.setCompanyId(7L);
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));

        service.deleteExpense(101L, 501L);

        verify(repository).deleteById(501L);
        verify(expenseRepository).deleteById(901L);
        verify(financeService).reconcileClosedDay(7L, expenseDate);
    }

    @Test
    void rejectsNonPositiveExpenseBeforeWritingFinanceData() {
        ServiceTicketExpenseDTO dto = ServiceTicketExpenseDTO.builder()
                .serviceTicketId(101L)
                .description("Geçersiz gider")
                .amount(BigDecimal.ZERO)
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.addExpense(dto));
        verify(expenseRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void updatingExpenseAlsoUpdatesLinkedFinanceRow() {
        LocalDate expenseDate = LocalDate.of(2026, 5, 20);
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(101L);
        ticket.setCompanyId(7L);
        ServiceTicketExpense expense = ServiceTicketExpense.builder()
                .id(501L).serviceTicketId(101L).companyId(7L)
                .description("Eski gider").amount(new BigDecimal("2500.00"))
                .expenseDate(expenseDate).financeExpenseId(901L).build();
        Expense financeExpense = Expense.builder()
                .id(901L).companyId(7L).description("Eski gider")
                .amount(new BigDecimal("2500.00")).date(expenseDate).build();
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(repository.findByIdAndServiceTicketId(501L, 101L)).thenReturn(Optional.of(expense));
        when(expenseRepository.findByIdAndCompanyId(901L, 7L)).thenReturn(Optional.of(financeExpense));
        when(repository.save(expense)).thenReturn(expense);

        ServiceTicketExpenseDTO result = service.updateExpense(101L, 501L,
                ServiceTicketExpenseDTO.builder()
                        .description("Yeni gider").amount(new BigDecimal("3200.00"))
                        .supplier("ZT").build());

        assertEquals("Yeni gider", result.getDescription());
        assertEquals(new BigDecimal("3200.00"), financeExpense.getAmount());
        assertEquals("Servis Gideri #101: Yeni gider", financeExpense.getDescription());
        verify(expenseRepository).save(financeExpense);
        verify(financeService).reconcileClosedDay(7L, expenseDate);
    }
}
