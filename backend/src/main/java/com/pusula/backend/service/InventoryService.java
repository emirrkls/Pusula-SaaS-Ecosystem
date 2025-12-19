package com.pusula.backend.service;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.dto.VehicleStockInfo;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.VehicleStock;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.VehicleStockRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository repository;
    private final VehicleStockRepository vehicleStockRepository;
    private final AuditLogService auditLogService;

    public InventoryService(InventoryRepository repository,
            VehicleStockRepository vehicleStockRepository,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.vehicleStockRepository = vehicleStockRepository;
        this.auditLogService = auditLogService;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public List<InventoryDTO> getAllInventory() {
        User user = getCurrentUser();
        return repository.findByCompanyId(user.getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public InventoryDTO createInventory(InventoryDTO dto) {
        User user = getCurrentUser();
        Inventory inventory = Inventory.builder()
                .companyId(user.getCompanyId())
                .partName(dto.getPartName())
                .quantity(dto.getQuantity())
                .buyPrice(dto.getBuyPrice())
                .sellPrice(dto.getSellPrice())
                .criticalLevel(dto.getCriticalLevel())
                .build();
        inventory.setBrand(dto.getBrand());
        inventory.setCategory(dto.getCategory());
        Inventory saved = repository.save(inventory);

        // Log inventory creation
        auditLogService.log("CREATE", "INVENTORY", saved.getId(),
                "Yeni stok kalemi: " + saved.getPartName() + " (Adet: " + saved.getQuantity() + ")");

        return mapToDTO(saved);
    }

    public InventoryDTO updateInventory(Long id, InventoryDTO dto) {
        User user = getCurrentUser();
        Inventory inventory = repository.findById(id)
                .filter(inv -> inv.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory not found or access denied"));

        // Capture old values for audit
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("partName", inventory.getPartName());
        oldValues.put("quantity", inventory.getQuantity());
        oldValues.put("buyPrice", inventory.getBuyPrice());
        oldValues.put("sellPrice", inventory.getSellPrice());
        oldValues.put("criticalLevel", inventory.getCriticalLevel());

        int oldQuantity = inventory.getQuantity();

        inventory.setPartName(dto.getPartName());
        inventory.setQuantity(dto.getQuantity());
        inventory.setBuyPrice(dto.getBuyPrice());
        inventory.setSellPrice(dto.getSellPrice());
        inventory.setCriticalLevel(dto.getCriticalLevel());
        inventory.setBrand(dto.getBrand());
        inventory.setCategory(dto.getCategory());

        Inventory saved = repository.save(inventory);

        // Capture new values for audit
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("partName", saved.getPartName());
        newValues.put("quantity", saved.getQuantity());
        newValues.put("buyPrice", saved.getBuyPrice());
        newValues.put("sellPrice", saved.getSellPrice());
        newValues.put("criticalLevel", saved.getCriticalLevel());

        // Log with quantity change details
        String description = saved.getPartName() + " güncellendi";
        if (oldQuantity != dto.getQuantity()) {
            description += " (Stok: " + oldQuantity + " → " + dto.getQuantity() + ")";
        }
        auditLogService.logChange("UPDATE", "INVENTORY", saved.getId(), description, oldValues, newValues);

        return mapToDTO(saved);
    }

    public void deleteInventory(Long id) {
        User user = getCurrentUser();
        Inventory inventory = repository.findById(id)
                .filter(inv -> inv.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory not found or access denied"));

        // Log before deletion
        auditLogService.log("DELETE", "INVENTORY", id,
                "Stok kalemi silindi: " + inventory.getPartName() + " (Kalan: " + inventory.getQuantity() + ")");

        repository.delete(inventory);
    }

    private InventoryDTO mapToDTO(Inventory inventory) {
        // Get vehicle stock distribution for this inventory item
        List<VehicleStock> vehicleStocks = vehicleStockRepository.findByInventoryId(inventory.getId());

        int inVehicleTotal = 0;
        List<VehicleStockInfo> distribution = new ArrayList<>();

        for (VehicleStock vs : vehicleStocks) {
            if (vs.getQuantity() > 0 && vs.getVehicle() != null) {
                inVehicleTotal += vs.getQuantity();
                String plate = vs.getVehicle().getLicensePlate() != null ? vs.getVehicle().getLicensePlate()
                        : "Unknown";
                distribution.add(new VehicleStockInfo(vs.getVehicle().getId(), plate, vs.getQuantity()));
            }
        }

        int warehouseQty = inventory.getQuantity() - inVehicleTotal;
        if (warehouseQty < 0)
            warehouseQty = 0; // Safety check

        InventoryDTO dto = InventoryDTO.builder()
                .id(inventory.getId())
                .partName(inventory.getPartName())
                .quantity(inventory.getQuantity())
                .buyPrice(inventory.getBuyPrice())
                .sellPrice(inventory.getSellPrice())
                .criticalLevel(inventory.getCriticalLevel())
                .brand(inventory.getBrand())
                .category(inventory.getCategory())
                .build();

        // Set distribution fields
        dto.setWarehouseQuantity(warehouseQty);
        dto.setInVehicleQuantity(inVehicleTotal);
        dto.setVehicleDistribution(distribution);

        return dto;
    }
}
