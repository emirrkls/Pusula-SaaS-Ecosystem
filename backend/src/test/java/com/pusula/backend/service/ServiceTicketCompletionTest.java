package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTicketCompletionTest {
    @Mock ServiceTicketRepository ticketRepository;
    @Mock ServiceTicketRescheduleRepository rescheduleRepository;
    @Mock CustomerRepository customerRepository;
    @Mock UserRepository userRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock ServiceUsedPartRepository usedPartRepository;
    @Mock AuditLogService auditLogService;
    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock VehicleStockRepository vehicleStockRepository;
    @Mock WhatsAppNotificationService whatsAppNotificationService;
    @Mock FeatureService featureService;
    @Mock ServicePhotoRepository photoRepository;
    @Mock FileUploadService fileUploadService;
    @Mock ApplicationEventPublisher publisher;
    @Mock FinanceService financeService;
    @Mock UploadUrlSigner uploadUrlSigner;
    @Mock CurrentAccountLedgerService currentAccountLedgerService;
    @Mock AdminNotificationService adminNotificationService;

    private ServiceTicketService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketService(ticketRepository, rescheduleRepository, customerRepository, userRepository,
                inventoryRepository, usedPartRepository, auditLogService, currentAccountRepository,
                vehicleStockRepository, whatsAppNotificationService, featureService, photoRepository,
                fileUploadService, publisher, financeService, uploadUrlSigner,
                currentAccountLedgerService, adminNotificationService, "Europe/Istanbul");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanBackdateCompletionAndLiquidCollection() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        LocalDate historicalDate = LocalDate.now().minusDays(4);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTicketDTO result = service.completeService(100L, new BigDecimal("1250.00"),
                PaymentMethod.CASH, historicalDate);

        assertEquals(ServiceTicket.TicketStatus.COMPLETED, ticket.getStatus());
        assertEquals(historicalDate, ticket.getCompletedAt().toLocalDate());
        assertEquals(historicalDate, ticket.getCollectionDate());
        assertNull(ticket.getInvoiceTotal());
        assertEquals(historicalDate, result.getCollectionDate());
        verify(financeService).reconcileClosedDay(10L, historicalDate);
        verify(auditLogService).log(eq("BACKDATED_COMPLETE"), eq("TICKET"), eq(100L), anyString());
    }

    @Test
    void technicianCannotSupplyCompletionDate() {
        authenticate(7L, 10L, "TECHNICIAN");
        ServiceTicket ticket = openTicket(100L, 10L, 7L);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class,
                () -> service.completeService(100L, BigDecimal.TEN, PaymentMethod.CASH, LocalDate.now()));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void technicianCannotCompleteAnotherTechniciansTicket() {
        authenticate(7L, 10L, "TECHNICIAN");
        ServiceTicket ticket = openTicket(100L, 10L, 8L);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class,
                () -> service.completeService(100L, BigDecimal.TEN, PaymentMethod.CASH, null));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void assignedTechnicianCanCompleteWithoutSupplyingDate() {
        authenticate(7L, 10L, "TECHNICIAN");
        ServiceTicket ticket = openTicket(100L, 10L, 7L);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, BigDecimal.TEN, PaymentMethod.CASH, null);

        assertEquals(LocalDate.now(), ticket.getCollectionDate());
        assertEquals(LocalDate.now(), ticket.getCompletedAt().toLocalDate());
        verify(financeService).reconcileClosedDay(10L, LocalDate.now());
    }

    @Test
    void futureCompletionDateIsRejected() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class,
                () -> service.completeService(100L, BigDecimal.TEN, PaymentMethod.CASH,
                        LocalDate.now().plusDays(1)));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void structuredPricingIncludesPartsWithoutRequiringLaborFee() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part("400.00", "500.00")));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, new BigDecimal("500.00"), BigDecimal.ZERO,
                PaymentMethod.CASH, null);

        assertEquals(new BigDecimal("500.00"), ticket.getPartsTotal());
        assertEquals(BigDecimal.ZERO, ticket.getLaborFee());
        assertEquals(new BigDecimal("500.00"), ticket.getInvoiceTotal());
        assertEquals(new BigDecimal("500.00"), ticket.getCollectedAmount());
        assertEquals(new BigDecimal("0.00"), ticket.getOutstandingAmount());
        assertEquals(LocalDate.now(), ticket.getCollectionDate());
        verify(currentAccountRepository, never()).save(any());
    }

    @Test
    void currentAccountStoresInvoiceAsDebtAndNoCashCollection() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setCustomerId(20L);
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder()
                .companyId(10L).customer(customer).balance(new BigDecimal("100.00")).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part("400.00", "500.00")));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(currentAccountRepository.findByCustomerIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(account));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, BigDecimal.ZERO, BigDecimal.ZERO,
                PaymentMethod.CURRENT_ACCOUNT, null);

        assertEquals(BigDecimal.ZERO, ticket.getCollectedAmount());
        assertEquals(new BigDecimal("500.00"), ticket.getOutstandingAmount());
        assertNull(ticket.getCollectionDate());
        assertEquals(new BigDecimal("600.00"), account.getBalance());
        verify(currentAccountRepository).save(account);
    }

    @Test
    void legacyCurrentAccountClientKeepsInvoiceFallbackWithoutRecordingCash() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setCustomerId(20L);
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder()
                .companyId(10L).customer(customer).balance(BigDecimal.ZERO).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(currentAccountRepository.findByCustomerIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(account));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, new BigDecimal("500.00"), PaymentMethod.CURRENT_ACCOUNT, null);

        assertNull(ticket.getInvoiceTotal());
        assertEquals(new BigDecimal("500.00"), ticket.getEffectiveInvoiceTotal());
        assertEquals(new BigDecimal("500.00"), ticket.getCollectedAmount());
        assertNull(ticket.getCollectionDate());
        assertEquals(new BigDecimal("500.00"), account.getBalance());
        verify(whatsAppNotificationService).notifyServiceCompleted(
                100L, BigDecimal.ZERO, new BigDecimal("500.00"));
    }

    @Test
    void partialCardPaymentMovesOnlyRemainderToCurrentAccount() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setCustomerId(20L);
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        CurrentAccount account = CurrentAccount.builder()
                .companyId(10L).customer(customer).balance(BigDecimal.ZERO).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part("400.00", "500.00")));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(currentAccountRepository.findByCustomerIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(account));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, new BigDecimal("300.00"), BigDecimal.ZERO,
                PaymentMethod.CREDIT_CARD, null);

        assertEquals(new BigDecimal("300.00"), ticket.getCollectedAmount());
        assertEquals(new BigDecimal("200.00"), ticket.getOutstandingAmount());
        assertEquals(new BigDecimal("200.00"), account.getBalance());
        assertEquals(LocalDate.now(), ticket.getCollectionDate());
    }

    @Test
    void collectionCannotExceedStructuredInvoiceTotal() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part("400.00", "500.00")));

        assertThrows(IllegalArgumentException.class,
                () -> service.completeService(100L, new BigDecimal("501.00"), BigDecimal.ZERO,
                        PaymentMethod.CASH, null));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void warrantyCompletionCreatesNoRevenueCollectionOrDebtButKeepsStructuredCosting() {
        authenticate(7L, 10L, "TECHNICIAN");
        ServiceTicket ticket = openTicket(100L, 10L, 7L);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part("400.00", "500.00")));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeService(100L, BigDecimal.ZERO, null, PaymentMethod.WARRANTY, null);

        assertEquals(PaymentMethod.WARRANTY, ticket.getPaymentMethod());
        assertTrue(Boolean.TRUE.equals(ticket.isWarrantyCall()));
        assertEquals(BigDecimal.ZERO, ticket.getPartsTotal());
        assertEquals(BigDecimal.ZERO, ticket.getLaborFee());
        assertEquals(BigDecimal.ZERO, ticket.getInvoiceTotal());
        assertEquals(BigDecimal.ZERO, ticket.getCollectedAmount());
        assertEquals(BigDecimal.ZERO, ticket.getOutstandingAmount());
        assertNull(ticket.getCollectionDate());
        verify(currentAccountRepository, never()).save(any());
    }

    @Test
    void warrantyCompletionRejectsAnyCollectionOrLaborCharge() {
        authenticate(7L, 10L, "TECHNICIAN");
        ServiceTicket ticket = openTicket(100L, 10L, 7L);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class,
                () -> service.completeService(100L, BigDecimal.ONE, BigDecimal.ZERO,
                        PaymentMethod.WARRANTY, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.completeService(100L, BigDecimal.ZERO, BigDecimal.ONE,
                        PaymentMethod.WARRANTY, null));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void genericUpdateCannotBypassCompletionWorkflow() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ServiceTicketDTO update = new ServiceTicketDTO();
        update.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class, () -> service.updateTicket(100L, update));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void createCannotBypassCompletionWorkflow() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicketDTO request = new ServiceTicketDTO();
        request.setStatus(ServiceTicket.TicketStatus.COMPLETED);

        assertThrows(IllegalArgumentException.class, () -> service.createTicket(request));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void adminCanSafelyReopenCompletedTicketAndReverseItsOutstandingBalance() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        LocalDate completedDate = LocalDate.now().minusDays(8);
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setCustomerId(20L);
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCompletedAt(completedDate.atTime(16, 30));
        ticket.setLaborFee(new BigDecimal("700.00"));
        ticket.setPartsTotal(new BigDecimal("300.00"));
        ticket.setInvoiceTotal(new BigDecimal("1000.00"));
        ticket.setCollectedAmount(new BigDecimal("700.00"));
        ticket.setOutstandingAmount(new BigDecimal("300.00"));
        ticket.setPaymentMethod(PaymentMethod.CASH);

        Customer customer = Customer.builder().id(20L).companyId(10L).name("Test Müşteri").build();
        CurrentAccount account = CurrentAccount.builder()
                .id(5L).companyId(10L).customer(customer).balance(new BigDecimal("500.00")).build();
        when(ticketRepository.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(ticket));
        when(currentAccountRepository.findByCustomerIdAndCompanyIdForUpdate(20L, 10L))
                .thenReturn(Optional.of(account));
        when(ticketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(currentAccountRepository.findByCustomerId(20L)).thenReturn(Optional.of(account));

        ServiceTicketDTO result = service.reopenCompletedService(100L);

        assertEquals(ServiceTicket.TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertNotNull(ticket.getReopenedAt());
        assertEquals(new BigDecimal("200.00"), account.getBalance());
        assertEquals(new BigDecimal("1000.00"), result.getInvoiceTotal());
        assertEquals(new BigDecimal("700.00"), result.getCollectedAmount());
        assertEquals(ticket.getReopenedAt(), result.getReopenedAt());
        verify(currentAccountRepository).save(account);
        verify(financeService).reconcileClosedDay(10L, completedDate);
        verify(auditLogService).log(eq("REOPEN"), eq("TICKET"), eq(100L), anyString(), anyString(), anyString());
    }

    @Test
    void reopenIsBlockedWhenOriginalCurrentAccountDebtHasAlreadyBeenPaid() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setCustomerId(20L);
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setOutstandingAmount(new BigDecimal("300.00"));
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Test Müşteri").build();
        CurrentAccount account = CurrentAccount.builder()
                .id(5L).companyId(10L).customer(customer).balance(new BigDecimal("100.00")).build();
        when(ticketRepository.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(ticket));
        when(currentAccountRepository.findByCustomerIdAndCompanyIdForUpdate(20L, 10L))
                .thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.reopenCompletedService(100L));
        assertEquals(ServiceTicket.TicketStatus.COMPLETED, ticket.getStatus());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(ticketRepository, never()).save(any());
        verify(currentAccountRepository, never()).save(any());
    }

    @Test
    void technicianCannotReopenCompletedTicket() {
        authenticate(7L, 10L, "TECHNICIAN");

        assertThrows(AccessDeniedException.class, () -> service.reopenCompletedService(100L));
        verify(ticketRepository, never()).findByIdAndCompanyIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void reopenedTicketCannotBeCancelledAndLoseItsHistoricalCollection() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = openTicket(100L, 10L, null);
        ticket.setReopenedAt(LocalDateTime.now().minusMinutes(5));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalStateException.class, () -> service.cancelService(100L));
        verify(ticketRepository, never()).save(any());
        verify(usedPartRepository, never()).findByServiceTicketId(anyLong());
    }

    private ServiceTicket openTicket(Long id, Long companyId, Long technicianId) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(id);
        ticket.setCompanyId(companyId);
        ticket.setAssignedTechnicianId(technicianId);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        return ticket;
    }

    private ServiceUsedPart part(String buyPrice, String sellPrice) {
        Inventory inventory = Inventory.builder()
                .id(30L)
                .companyId(10L)
                .partName("Parça")
                .quantity(5)
                .buyPrice(new BigDecimal(buyPrice))
                .sellPrice(new BigDecimal(sellPrice))
                .build();
        return ServiceUsedPart.builder()
                .companyId(10L)
                .inventory(inventory)
                .quantityUsed(1)
                .buyingPriceSnapshot(new BigDecimal(buyPrice))
                .sellingPriceSnapshot(new BigDecimal(sellPrice))
                .build();
    }

    private void authenticate(Long userId, Long companyId, String role) {
        User user = User.builder()
                .id(userId)
                .companyId(companyId)
                .username("test-user")
                .role(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
