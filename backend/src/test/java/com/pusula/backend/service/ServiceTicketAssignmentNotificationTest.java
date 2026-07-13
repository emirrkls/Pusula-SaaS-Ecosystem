package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTicketAssignmentNotificationTest {
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
    private ServiceTicketService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketService(ticketRepository, customerRepository, userRepository,
                inventoryRepository, usedPartRepository, auditLogService, currentAccountRepository,
                vehicleStockRepository, whatsAppNotificationService, featureService, photoRepository,
                fileUploadService, publisher, "Europe/Istanbul");
        authenticate(1L, 10L, "COMPANY_ADMIN");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void assignPublishesCorrectRecipientAndTicketAfterSaving() {
        ServiceTicket ticket = ticket(100L, 10L, null);
        User technician = user(7L, 10L, "TECHNICIAN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        service.assignTechnician(100L, 7L);

        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, ticket.getStatus());
        ArgumentCaptor<TicketAssignedEvent> event = ArgumentCaptor.forClass(TicketAssignedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(10L, event.getValue().companyId());
        assertEquals(7L, event.getValue().technicianId());
        assertEquals(100L, event.getValue().ticketId());
    }

    @Test
    void assigningSameTechnicianDoesNotPublishDuplicate() {
        ServiceTicket ticket = ticket(100L, 10L, 7L);
        User technician = user(7L, 10L, "TECHNICIAN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        service.assignTechnician(100L, 7L);

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void directCreateWithTechnicianIsAssignedAndPublishes() {
        User technician = user(7L, 10L, "TECHNICIAN");
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(any())).thenAnswer(invocation -> {
            ServiceTicket saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });
        ServiceTicketDTO request = new ServiceTicketDTO();
        request.setAssignedTechnicianId(7L);

        ServiceTicketDTO result = service.createTicket(request);

        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, result.getStatus());
        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 101L));
    }

    @Test
    void updatePublishesOnlyWhenTechnicianActuallyChanges() {
        ServiceTicket ticket = ticket(100L, 10L, 6L);
        User technician = user(7L, 10L, "TECHNICIAN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        ServiceTicketDTO request = new ServiceTicketDTO();
        request.setAssignedTechnicianId(7L);

        service.updateTicket(100L, request);

        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, ticket.getStatus());
        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 100L));
    }

    @Test
    void rejectsForeignTenantAndNonTechnicianUsers() {
        ServiceTicket ticket = ticket(100L, 10L, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.assignTechnician(100L, 7L));

        User admin = user(8L, 10L, "COMPANY_ADMIN");
        when(userRepository.findByIdAndCompanyId(8L, 10L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.assignTechnician(100L, 8L));
        verify(ticketRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    private ServiceTicket ticket(Long id, Long companyId, Long technicianId) {
        return ServiceTicket.builder().id(id).companyId(companyId)
                .assignedTechnicianId(technicianId).status(ServiceTicket.TicketStatus.PENDING).build();
    }

    private User user(Long id, Long companyId, String role) {
        User user = new User();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setRole(role);
        user.setFullName("Tech");
        return user;
    }

    private void authenticate(Long id, Long companyId, String role) {
        User user = user(id, companyId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
