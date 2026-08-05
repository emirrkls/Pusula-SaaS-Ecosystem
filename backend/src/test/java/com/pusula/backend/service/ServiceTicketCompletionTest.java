package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTicketCompletionTest {
    @Mock ServiceTicketRepository ticketRepository;
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

    private ServiceTicketService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketService(ticketRepository, customerRepository, userRepository,
                inventoryRepository, usedPartRepository, auditLogService, currentAccountRepository,
                vehicleStockRepository, whatsAppNotificationService, featureService, photoRepository,
                fileUploadService, publisher, financeService, "Europe/Istanbul");
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

    private ServiceTicket openTicket(Long id, Long companyId, Long technicianId) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(id);
        ticket.setCompanyId(companyId);
        ticket.setAssignedTechnicianId(technicianId);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        return ticket;
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
