package com.pusula.backend.controller;

import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.service.CurrentAccountLedgerService;
import com.pusula.backend.service.CurrentAccountPaymentService;
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
    @Mock CurrentAccountLedgerService ledgerService;
    @Mock CurrentAccountPaymentService currentAccountPaymentService;

    private CurrentAccountController controller;

    @BeforeEach
    void setUp() {
        controller = new CurrentAccountController();
        ReflectionTestUtils.setField(controller, "currentAccountRepository", currentAccountRepository);
        ReflectionTestUtils.setField(controller, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(controller, "ledgerService", ledgerService);
        ReflectionTestUtils.setField(controller, "currentAccountPaymentService", currentAccountPaymentService);
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
        when(currentAccountPaymentService.pay(eq(9L), eq(7L), eq(1L),
                any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Ödeme ve indirim toplamı cari bakiyeyi aşamaz."));

        assertThrows(IllegalArgumentException.class, () -> controller.payDebt(9L, Map.of(
                "paymentAmount", new BigDecimal("900.00"),
                "discount", new BigDecimal("200.00"))));
    }

    @Test
    void paymentUsesRequestedDateMethodAndNotes() {
        CurrentAccount account = CurrentAccount.builder().id(9L).companyId(7L)
                .balance(new BigDecimal("1000.00")).build();
        when(currentAccountPaymentService.pay(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(account);

        controller.payDebt(9L, Map.of(
                "paymentAmount", new BigDecimal("400.00"),
                "discount", BigDecimal.ZERO,
                "collectionDate", "2026-07-03",
                "paymentMethod", "CREDIT_CARD",
                "notes", "Temmuz tahsilatı"));

        verify(currentAccountPaymentService).pay(9L, 7L, 1L,
                new BigDecimal("400.00"), BigDecimal.ZERO, LocalDate.of(2026, 7, 3),
                PaymentMethod.CREDIT_CARD, "Temmuz tahsilatı", null);
    }
}
