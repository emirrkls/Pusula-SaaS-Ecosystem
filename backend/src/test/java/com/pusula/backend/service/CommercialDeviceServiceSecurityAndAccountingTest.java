package com.pusula.backend.service;

import com.pusula.backend.dto.SaleRequestDTO;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercialDeviceServiceSecurityAndAccountingTest {
    @Mock CommercialDeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;
    @Mock UserRepository userRepository;
    @Mock ServiceTicketRepository ticketRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock AuditLogService auditLogService;
    @Mock CurrentAccountLedgerService currentAccountLedgerService;

    private CommercialDeviceService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new CommercialDeviceService(deviceRepository, deviceTypeRepository, userRepository,
                ticketRepository, customerRepository, currentAccountRepository, expenseRepository, auditLogService,
                currentAccountLedgerService);
        admin = User.builder().id(1L).companyId(10L).username("admin").passwordHash("hash")
                .role("COMPANY_ADMIN").fullName("Admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lookupIsAlwaysScopedToAuthenticatedCompany() {
        when(deviceRepository.findByIdAndCompanyId(99L, 10L)).thenReturn(Optional.empty());
        assertNull(service.getById(99L));
        verify(deviceRepository, never()).findById(99L);
    }

    @Test
    void cardSaleRecordsFullRevenueAndSeparateCostWithoutCreatingCariDebt() {
        CommercialDevice device = CommercialDevice.builder().id(5L).companyId(10L)
                .brand("Test").model("VRF").quantity(2)
                .buyingPrice(new BigDecimal("400")).sellingPrice(new BigDecimal("500")).build();
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        when(deviceRepository.findByIdAndCompanyIdForUpdate(5L, 10L)).thenReturn(Optional.of(device));
        when(customerRepository.findByIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> {
            ServiceTicket ticket = invocation.getArgument(0);
            ticket.setId(30L);
            return ticket;
        });

        service.processSale(SaleRequestDTO.builder().deviceId(5L).customerId(20L)
                .sellingPrice(new BigDecimal("500")).paymentMethod("CREDIT_CARD")
                .saleDate(LocalDate.of(2026, 8, 12)).build());

        ArgumentCaptor<Expense> expenses = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository, times(2)).save(expenses.capture());
        Expense revenue = expenses.getAllValues().stream()
                .filter(row -> row.getCategory() == ExpenseCategory.DEVICE_SALE).findFirst().orElseThrow();
        Expense cost = expenses.getAllValues().stream()
                .filter(row -> row.getSourceType().equals("COMMERCIAL_DEVICE_COST")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("-500"), revenue.getAmount());
        assertEquals(new BigDecimal("400"), cost.getAmount());
        assertEquals(PaymentMethod.CREDIT_CARD, revenue.getPaymentMethod());
        assertEquals(1, device.getQuantity());
        verifyNoInteractions(currentAccountRepository);
    }
}
