package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.InventoryUnit;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTicketUsedPartsTest {
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
    void addingPartUsesConfirmedCustomSellingPrice() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(50L).companyId(10L).partName("Kondansatör").quantity(5)
                .buyPrice(new BigDecimal("50.00")).sellPrice(new BigDecimal("200.00")).criticalLevel(1)
                .build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByCompanyIdAndClientRequestId(10L, "request-1"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(50L, 10L)).thenReturn(Optional.of(inventory));
        when(usedPartRepository.save(any(ServiceUsedPart.class))).thenAnswer(invocation -> {
            ServiceUsedPart saved = invocation.getArgument(0);
            saved.setId(300L);
            return saved;
        });

        ServiceUsedPartDTO result = service.addUsedPart(100L, ServiceUsedPartDTO.builder()
                .inventoryId(50L)
                .quantityUsed(1)
                .sellingPriceSnapshot(new BigDecimal("150"))
                .clientRequestId("request-1")
                .build());

        assertEquals(new BigDecimal("150.00"), result.getSellingPriceSnapshot());
        assertEquals("request-1", result.getClientRequestId());
        assertEquals(BigDecimal.valueOf(4), inventory.getQuantity());
    }

    @Test
    void fractionalGasUsageDeductsExactQuantityAndKeepsUnit() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(51L).companyId(10L).partName("R32 Gaz").quantity(new BigDecimal("5.000"))
                .buyPrice(new BigDecimal("600.00")).sellPrice(new BigDecimal("1500.00"))
                .criticalLevel(new BigDecimal("1.000")).unitOfMeasure(InventoryUnit.KG).build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(51L, 10L)).thenReturn(Optional.of(inventory));
        when(usedPartRepository.save(any(ServiceUsedPart.class))).thenAnswer(invocation -> {
            ServiceUsedPart saved = invocation.getArgument(0);
            saved.setId(302L);
            return saved;
        });

        ServiceUsedPartDTO result = service.addUsedPart(100L, ServiceUsedPartDTO.builder()
                .inventoryId(51L).quantityUsed(new BigDecimal("0.800")).build());

        assertEquals(0, new BigDecimal("4.200").compareTo(inventory.getQuantity()));
        assertEquals(0, new BigDecimal("0.800").compareTo(result.getQuantityUsed()));
        assertEquals("KG", result.getUnitOfMeasure());
    }

    @Test
    void addingPartDefaultsMissingInventorySellingPriceToZero() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(50L).companyId(10L).partName("Fiyatsız Parça").quantity(2).criticalLevel(0)
                .build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(50L, 10L)).thenReturn(Optional.of(inventory));
        when(usedPartRepository.save(any(ServiceUsedPart.class))).thenAnswer(invocation -> {
            ServiceUsedPart saved = invocation.getArgument(0);
            saved.setId(301L);
            return saved;
        });

        ServiceUsedPartDTO result = service.addUsedPart(100L, ServiceUsedPartDTO.builder()
                .inventoryId(50L)
                .quantityUsed(1)
                .build());

        assertEquals(new BigDecimal("0.00"), result.getSellingPriceSnapshot());
    }

    @Test
    void repeatedClientRequestReturnsOriginalPartWithoutReducingStockAgain() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(100L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        Inventory inventory = Inventory.builder()
                .id(50L).companyId(10L).partName("Kondansatör").quantity(4)
                .sellPrice(new BigDecimal("150.00")).criticalLevel(1).build();
        ServiceUsedPart existing = ServiceUsedPart.builder()
                .id(300L).companyId(10L).serviceTicket(ticket).inventory(inventory)
                .quantityUsed(1).sellingPriceSnapshot(new BigDecimal("150.00"))
                .clientRequestId("request-1").build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByCompanyIdAndClientRequestId(10L, "request-1"))
                .thenReturn(Optional.of(existing));

        ServiceUsedPartDTO result = service.addUsedPart(100L, ServiceUsedPartDTO.builder()
                .inventoryId(50L)
                .quantityUsed(1)
                .sellingPriceSnapshot(new BigDecimal("150.00"))
                .clientRequestId("request-1")
                .build());

        assertEquals(300L, result.getId());
        assertEquals(BigDecimal.valueOf(4), inventory.getQuantity());
        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(usedPartRepository, never()).save(any(ServiceUsedPart.class));
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

        assertEquals(BigDecimal.valueOf(7), inventory.getQuantity());
        assertEquals(BigDecimal.ONE, result.getQuantityUsed());
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

        assertEquals(BigDecimal.valueOf(8), inventory.getQuantity());
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
        when(part.getQuantityUsed()).thenReturn(BigDecimal.ONE);

        when(ticketRepository.findById(75L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(75L)).thenReturn(List.of(part));
        when(inventoryRepository.findIncludingDeletedByIdAndCompanyIdForUpdate(93L, 10L))
                .thenReturn(Optional.of(deletedInventory));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        service.cancelService(75L);

        assertEquals(ServiceTicket.TicketStatus.CANCELLED, ticket.getStatus());
        assertEquals(BigDecimal.ONE, deletedInventory.getQuantity());
        assertFalse(deletedInventory.isDeleted());
        verify(inventoryRepository).save(deletedInventory);
        verify(usedPartRepository).delete(part);
    }

    @Test
    void cancellingTicketMergesReturnIntoActiveBarcodeReplacement() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        ServiceTicket ticket = ticket(75L, 10L, null);
        ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
        Inventory deletedInventory = Inventory.builder()
                .id(93L).companyId(10L).partName("Eski kondansatör").quantity(0)
                .criticalLevel(0).build();
        deletedInventory.setBarcode("ABC-75");
        deletedInventory.setDeleted(true);
        Inventory activeReplacement = Inventory.builder()
                .id(94L).companyId(10L).partName("Kondansatör").quantity(4)
                .criticalLevel(0).build();
        activeReplacement.setBarcode(" abc-75 ");
        ServiceUsedPart part = mock(ServiceUsedPart.class);
        when(part.getInventory()).thenReturn(null);
        when(part.getInventoryId()).thenReturn(93L);
        when(part.getSourceVehicleId()).thenReturn(null);
        when(part.getQuantityUsed()).thenReturn(BigDecimal.ONE);

        when(ticketRepository.findById(75L)).thenReturn(Optional.of(ticket));
        when(usedPartRepository.findByServiceTicketId(75L)).thenReturn(List.of(part));
        when(inventoryRepository.findIncludingDeletedByIdAndCompanyIdForUpdate(93L, 10L))
                .thenReturn(Optional.of(deletedInventory));
        when(inventoryRepository.findActiveBarcodeReplacementForUpdate("ABC-75", 10L, 93L))
                .thenReturn(Optional.of(activeReplacement));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        service.cancelService(75L);

        assertEquals(ServiceTicket.TicketStatus.CANCELLED, ticket.getStatus());
        assertEquals(BigDecimal.valueOf(5), activeReplacement.getQuantity());
        assertTrue(deletedInventory.isDeleted());
        verify(part).setInventory(activeReplacement);
        verify(inventoryRepository).save(activeReplacement);
        verify(inventoryRepository, never()).save(deletedInventory);
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
