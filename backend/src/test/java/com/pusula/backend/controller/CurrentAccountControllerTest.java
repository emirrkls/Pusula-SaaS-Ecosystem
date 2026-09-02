package com.pusula.backend.controller;

import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.service.CurrentAccountLedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentAccountControllerTest {

    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ServiceTicketRepository serviceTicketRepository;
    @Mock CurrentAccountLedgerService ledgerService;

    private CurrentAccountController controller;

    @BeforeEach
    void setUp() {
        controller = new CurrentAccountController();
        ReflectionTestUtils.setField(controller, "currentAccountRepository", currentAccountRepository);
        ReflectionTestUtils.setField(controller, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(controller, "serviceTicketRepository", serviceTicketRepository);
        ReflectionTestUtils.setField(controller, "ledgerService", ledgerService);
        User admin = User.builder().id(1L).companyId(7L).username("admin")
                .passwordHash("secret").role("COMPANY_ADMIN").fullName("Admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerLookupIsTenantScoped() {
        controller.getByCustomer(30L);

        verify(currentAccountRepository).findByCustomerIdAndCompanyId(30L, 7L);
        verify(currentAccountRepository, never()).findByCustomerId(30L);
    }

    @Test
    void paymentCannotExceedCurrentBalance() {
        Customer customer = Customer.builder().id(30L).companyId(7L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder().id(9L).companyId(7L).customer(customer)
                .balance(new BigDecimal("1000.00")).build();
        when(currentAccountRepository.findByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class, () -> controller.payDebt(9L, Map.of(
                "paymentAmount", new BigDecimal("900.00"),
                "discount", new BigDecimal("200.00"))));

        verify(serviceTicketRepository, never()).save(any());
        verify(currentAccountRepository, never()).save(any());
    }

    @Test
    void paymentUsesRequestedDateMethodAndNotes() {
        Customer customer = Customer.builder().id(30L).companyId(7L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder().id(9L).companyId(7L).customer(customer)
                .balance(new BigDecimal("1000.00")).build();
        when(currentAccountRepository.findByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(account));
        when(currentAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.payDebt(9L, Map.of(
                "paymentAmount", new BigDecimal("400.00"),
                "discount", BigDecimal.ZERO,
                "collectionDate", "2026-07-03",
                "paymentMethod", "CREDIT_CARD",
                "notes", "Temmuz tahsilatı"));

        var captor = org.mockito.ArgumentCaptor.forClass(ServiceTicket.class);
        verify(serviceTicketRepository).save(captor.capture());
        ServiceTicket ticket = captor.getValue();
        assertEquals(LocalDate.of(2026, 7, 3), ticket.getCollectionDate());
        assertEquals(LocalDate.of(2026, 7, 3), ticket.getCompletedAt().toLocalDate());
        assertEquals(PaymentMethod.CREDIT_CARD, ticket.getPaymentMethod());
        assertEquals("Cari hesap ödemesi - Müşteri - Temmuz tahsilatı", ticket.getDescription());
        assertEquals(new BigDecimal("600.00"), account.getBalance());
    }
}
