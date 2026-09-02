package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.Customer;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock FinanceService financeService;
    @Mock UploadUrlSigner uploadUrlSigner;
    @Mock CurrentAccountLedgerService currentAccountLedgerService;
    private ServiceTicketService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketService(ticketRepository, customerRepository, userRepository,
                inventoryRepository, usedPartRepository, auditLogService, currentAccountRepository,
                vehicleStockRepository, whatsAppNotificationService, featureService, photoRepository,
                fileUploadService, publisher, financeService, uploadUrlSigner,
                currentAccountLedgerService, "Europe/Istanbul");
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
        request.setCustomerId(20L);
        request.setAssignedTechnicianId(7L);
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        when(customerRepository.findByIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(customer));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));

        ServiceTicketDTO result = service.createTicket(request);

        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, result.getStatus());
        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 101L));
        verify(whatsAppNotificationService).notifyServiceCreated(101L);
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

    @Test
    void privateAssignmentNoteIsVisibleOnlyToAdminAndCurrentlyAssignedTechnician() {
        ServiceTicket ticket = ticket(100L, 10L, 7L);
        ticket.setTechnicianPrivateNote("Müşteriye önceden 2.500 TL fiyat verildi.");
        User assignedTechnician = user(7L, 10L, "TECHNICIAN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(assignedTechnician));
        when(ticketRepository.findByCompanyId(10L)).thenReturn(List.of(ticket));

        assertEquals(ticket.getTechnicianPrivateNote(), service.getAllTickets().get(0).getTechnicianPrivateNote());

        authenticate(7L, 10L, "TECHNICIAN");
        assertEquals(ticket.getTechnicianPrivateNote(), service.getAllTickets().get(0).getTechnicianPrivateNote());

        authenticate(8L, 10L, "TECHNICIAN");
        assertNull(service.getAllTickets().get(0).getTechnicianPrivateNote());
    }

    @Test
    void adminCanStorePrivateAssignmentNoteWhenCreatingTicket() {
        User technician = user(7L, 10L, "TECHNICIAN");
        Customer customer = Customer.builder().id(20L).companyId(10L).name("Müşteri").build();
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(customerRepository.findByIdAndCompanyId(20L, 10L)).thenReturn(Optional.of(customer));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ServiceTicketDTO request = new ServiceTicketDTO();
        request.setCustomerId(20L);
        request.setAssignedTechnicianId(7L);
        request.setTechnicianPrivateNote("  Ön fiyat 2.500 TL  ");

        ServiceTicketDTO result = service.createTicket(request);

        assertEquals("Ön fiyat 2.500 TL", result.getTechnicianPrivateNote());
        ArgumentCaptor<ServiceTicket> saved = ArgumentCaptor.forClass(ServiceTicket.class);
        verify(ticketRepository).save(saved.capture());
        assertEquals("Ön fiyat 2.500 TL", saved.getValue().getTechnicianPrivateNote());
    }

    @Test
    void bulkAssignsPendingTicketsAndPublishesForEachTicket() {
        ServiceTicket first = ticket(100L, 10L, null);
        ServiceTicket second = ticket(101L, 10L, null);
        User technician = user(7L, 10L, "TECHNICIAN");
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician));
        when(ticketRepository.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(first));
        when(ticketRepository.findByIdAndCompanyIdForUpdate(101L, 10L)).thenReturn(Optional.of(second));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ServiceTicketDTO> result = service.assignTechnicianBulk(List.of(100L, 101L), 7L);

        assertEquals(2, result.size());
        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, first.getStatus());
        assertEquals(ServiceTicket.TicketStatus.ASSIGNED, second.getStatus());
        assertEquals(7L, first.getAssignedTechnicianId());
        assertEquals(7L, second.getAssignedTechnicianId());
        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 100L));
        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 101L));
    }

    @Test
    void bulkAssignmentRejectsStaleSelectionBeforeSavingAnyTicket() {
        ServiceTicket pending = ticket(100L, 10L, null);
        ServiceTicket alreadyAssigned = ticket(101L, 10L, 8L);
        alreadyAssigned.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
        User technician = user(7L, 10L, "TECHNICIAN");
        when(userRepository.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(technician));
        when(ticketRepository.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(pending));
        when(ticketRepository.findByIdAndCompanyIdForUpdate(101L, 10L)).thenReturn(Optional.of(alreadyAssigned));

        assertThrows(IllegalStateException.class,
                () -> service.assignTechnicianBulk(List.of(100L, 101L), 7L));

        verify(ticketRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void bulkAssignmentRejectsEmptyAndOversizedSelections() {
        assertThrows(IllegalArgumentException.class,
                () -> service.assignTechnicianBulk(List.of(), 7L));
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();
        assertThrows(IllegalArgumentException.class,
                () -> service.assignTechnicianBulk(tooMany, 7L));
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
