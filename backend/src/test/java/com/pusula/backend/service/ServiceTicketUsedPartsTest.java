package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTicketUsedPartsTest {
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

    private ServiceTicketService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTicketService(ticketRepository, customerRepository, userRepository,
                inventoryRepository, usedPartRepository, auditLogService, currentAccountRepository,
                vehicleStockRepository, whatsAppNotificationService, featureService, photoRepository,
                fileUploadService, publisher, financeService, uploadUrlSigner, "Europe/Istanbul");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingInventoryUsesHistoricalFallbackInsteadOfThrowing() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, 7L);
        ServiceUsedPart part = ServiceUsedPart.builder()
                .id(300L)
                .companyId(10L)
                .serviceTicket(ticket)
                .inventory(null)
                .quantityUsed(2)
                .sellingPriceSnapshot(new BigDecimal("125.50"))
                .build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(part));

        List<ServiceUsedPartDTO> result = service.getUsedParts(100L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getInventoryId());
        assertEquals("Yedek Parça", result.get(0).getPartName());
        assertEquals(new BigDecimal("125.50"), result.get(0).getSellingPriceSnapshot());
    }

    @Test
    void technicianCannotReadPartsForTicketAssignedToSomeoneElse() {
        authenticate(7L, 10L, "TECHNICIAN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket(100L, 10L, 8L)));

        assertThrows(RuntimeException.class, () -> service.getUsedParts(100L));

        verify(usedPartRepository, never()).findByServiceTicketId(100L);
    }

    @Test
    void foreignTenantTicketIsRejected() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket(100L, 20L, null)));

        assertThrows(RuntimeException.class, () -> service.getUsedParts(100L));

        verify(usedPartRepository, never()).findByServiceTicketId(100L);
    }

    @Test
    void missingTicketIsRejected() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getUsedParts(404L));

        verify(usedPartRepository, never()).findByServiceTicketId(404L);
    }

    @Test
    void decreasingUsedPartReturnsDifferenceToInventory() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(50L).companyId(10L).partName("Kompresör").quantity(5)
                .buyPrice(new BigDecimal("100")).sellPrice(new BigDecimal("200")).criticalLevel(1)
                .build();
        ServiceUsedPart part = ServiceUsedPart.builder()
                .id(300L).companyId(10L).serviceTicket(ticket).inventory(inventory)
                .quantityUsed(3).sellingPriceSnapshot(new BigDecimal("200")).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByIdAndCompanyId(300L, 10L)).thenReturn(Optional.of(part));
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(50L, 10L)).thenReturn(Optional.of(inventory));
        when(usedPartRepository.save(part)).thenReturn(part);

        ServiceUsedPartDTO result = service.updateUsedPart(100L, 300L,
                ServiceUsedPartDTO.builder().quantityUsed(1).build());

        assertEquals(7, inventory.getQuantity());
        assertEquals(1, result.getQuantityUsed());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void deletingUsedPartReturnsAllQuantityToInventory() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(50L).companyId(10L).partName("Kompresör").quantity(5)
                .buyPrice(new BigDecimal("100")).sellPrice(new BigDecimal("200")).criticalLevel(1)
                .build();
        ServiceUsedPart part = ServiceUsedPart.builder()
                .id(300L).companyId(10L).serviceTicket(ticket).inventory(inventory)
                .quantityUsed(3).sellingPriceSnapshot(new BigDecimal("200")).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByIdAndCompanyId(300L, 10L)).thenReturn(Optional.of(part));
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(50L, 10L)).thenReturn(Optional.of(inventory));

        service.deleteUsedPart(100L, 300L);

        assertEquals(8, inventory.getQuantity());
        verify(inventoryRepository).save(inventory);
        verify(usedPartRepository).delete(part);
    }

    @Test
    void cancellingTicketRestoresAndRevivesSoftDeletedInventory() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(75L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
        Inventory deletedInventory = Inventory.builder()
                .id(93L).companyId(10L).partName("deneme").quantity(0)
                .criticalLevel(0).build();
        deletedInventory.setDeleted(true);
        ServiceUsedPart part = mock(ServiceUsedPart.class);
        when(part.getInventory()).thenReturn(null);
        when(part.getInventoryId()).thenReturn(93L);
        when(part.getSourceVehicleId()).thenReturn(null);
        when(part.getQuantityUsed()).thenReturn(1);

        when(ticketRepository.findById(75L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(75L)).thenReturn(List.of(part));
        when(inventoryRepository.findIncludingDeletedByIdAndCompanyIdForUpdate(93L, 10L))
                .thenReturn(Optional.of(deletedInventory));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        service.cancelService(75L);

        assertEquals(ServiceTicket.TicketStatus.CANCELLED, ticket.getStatus());
        assertEquals(1, deletedInventory.getQuantity());
        assertFalse(deletedInventory.isDeleted());
        verify(inventoryRepository).save(deletedInventory);
        verify(usedPartRepository).delete(part);
    }

    private ServiceTicket ticket(Long id, Long companyId, Long technicianId) {
        return ServiceTicket.builder()
                .id(id)
                .companyId(companyId)
                .customerId(200L)
                .assignedTechnicianId(technicianId)
                .status(ServiceTicket.TicketStatus.COMPLETED)
                .build();
    }

    private void authenticate(Long id, Long companyId, String role) {
        User user = new User();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setRole(role);
        user.setUsername("test-user");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
