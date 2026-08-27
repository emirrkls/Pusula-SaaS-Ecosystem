package com.pusula.backend.service;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.VehicleStockRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTenantSecurityTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private VehicleStockRepository vehicleStockRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FeatureService featureService;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(
                inventoryRepository,
                vehicleStockRepository,
                auditLogService,
                featureService);
        authenticate(10L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAlwaysUsesAuthenticatedCompany() {
        InventoryDTO request = inventoryDto("Filter", 5);
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inventory = invocation.getArgument(0);
            inventory.setId(1L);
            return inventory;
        });
        when(vehicleStockRepository.findByInventoryIdAndCompanyId(1L, 10L)).thenReturn(List.of());

        inventoryService.createInventory(request);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getCompanyId());
        verify(featureService).incrementUsage(10L, "INVENTORY");
    }

    @Test
    void ownTenantInventoryCanBeUpdatedAndDeleted() {
        Inventory inventory = inventory(1L, 10L, "Old Filter", 2);
        when(inventoryRepository.findByIdAndCompanyId(1L, 10L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(vehicleStockRepository.findByInventoryIdAndCompanyId(1L, 10L)).thenReturn(List.of());

        Optional<InventoryDTO> updated = inventoryService.updateInventory(1L, inventoryDto("New Filter", 7));
        boolean deleted = inventoryService.deleteInventory(1L);

        assertTrue(updated.isPresent());
        assertEquals("New Filter", inventory.getPartName());
        assertEquals(BigDecimal.valueOf(7), inventory.getQuantity());
        assertTrue(deleted);
        verify(inventoryRepository).save(inventory);
        verify(inventoryRepository).delete(inventory);
    }

    @Test
    void foreignTenantUpdateAndDeleteDoNotMutateInventory() {
        when(inventoryRepository.findByIdAndCompanyId(77L, 10L)).thenReturn(Optional.empty());

        Optional<InventoryDTO> updated = inventoryService.updateInventory(77L, inventoryDto("Changed", 99));
        boolean deleted = inventoryService.deleteInventory(77L);

        assertTrue(updated.isEmpty());
        assertFalse(deleted);
        verify(inventoryRepository, never()).save(any());
        verify(inventoryRepository, never()).delete(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void adetRejectsFractionalQuantityWhileKilogramAcceptsIt() {
        InventoryDTO invalid = inventoryDto("Vida", 1);
        invalid.setQuantity(new BigDecimal("0.800"));
        invalid.setUnitOfMeasure("ADET");
        assertThrows(IllegalArgumentException.class, () -> inventoryService.createInventory(invalid));

        InventoryDTO gas = inventoryDto("R32 Gaz", 1);
        gas.setQuantity(new BigDecimal("5.800"));
        gas.setCriticalLevel(new BigDecimal("1.500"));
        gas.setUnitOfMeasure("KG");
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(vehicleStockRepository.findByInventoryIdAndCompanyId(2L, 10L)).thenReturn(List.of());

        InventoryDTO created = inventoryService.createInventory(gas);
        assertEquals(0, new BigDecimal("5.800").compareTo(created.getQuantity()));
        assertEquals("KG", created.getUnitOfMeasure());
    }

    private void authenticate(Long companyId) {
        User principal = new User();
        principal.setCompanyId(companyId);
        principal.setRole("COMPANY_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private InventoryDTO inventoryDto(String partName, int quantity) {
        return InventoryDTO.builder()
                .partName(partName)
                .quantity(quantity)
                .buyPrice(new BigDecimal("10.00"))
                .sellPrice(new BigDecimal("20.00"))
                .criticalLevel(1)
                .build();
    }

    private Inventory inventory(Long id, Long companyId, String partName, int quantity) {
        return Inventory.builder()
                .id(id)
                .companyId(companyId)
                .partName(partName)
                .quantity(quantity)
                .buyPrice(new BigDecimal("10.00"))
                .sellPrice(new BigDecimal("20.00"))
                .criticalLevel(1)
                .build();
    }
}
