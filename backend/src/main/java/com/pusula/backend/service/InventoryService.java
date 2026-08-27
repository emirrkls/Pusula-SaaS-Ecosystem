package com.pusula.backend.service;

import com.pusula.backend.annotation.CheckQuota;
import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.dto.VehicleStockInfo;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.InventoryUnit;
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
import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository repository;
    private final VehicleStockRepository vehicleStockRepository;
    private final AuditLogService auditLogService;
    private final FeatureService featureService;

    public InventoryService(InventoryRepository repository,
            VehicleStockRepository vehicleStockRepository,
            AuditLogService auditLogService,
            FeatureService featureService) {
        this.repository = repository;
        this.vehicleStockRepository = vehicleStockRepository;
        this.auditLogService = auditLogService;
        this.featureService = featureService;
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

    @CheckQuota("INVENTORY")
    public InventoryDTO createInventory(InventoryDTO dto) {
        User user = getCurrentUser();
        validateInventory(dto, user.getCompanyId(), null);
        Inventory inventory = Inventory.builder()
                .companyId(user.getCompanyId())
                .partName(dto.getPartName())
                .quantity(normalizeQuantity(dto.getQuantity()))
                .buyPrice(dto.getBuyPrice())
                .sellPrice(dto.getSellPrice())
                .criticalLevel(defaultCriticalLevel(dto.getCriticalLevel()))
                .unitOfMeasure(InventoryUnit.fromNullable(dto.getUnitOfMeasure()))
                .build();
        inventory.setBrand(dto.getBrand());
        inventory.setCategory(dto.getCategory());
        inventory.setBarcode(normalizeBarcode(dto.getBarcode()));
        Inventory saved = repository.save(inventory);
        featureService.incrementUsage(user.getCompanyId(), "INVENTORY");

        // Log inventory creation
        auditLogService.log("CREATE", "INVENTORY", saved.getId(),
                "Yeni stok kalemi: " + saved.getPartName() + " (Adet: " + saved.getQuantity() + ")");

        return mapToDTO(saved);
    }

    public Optional<InventoryDTO> updateInventory(Long id, InventoryDTO dto) {
        User user = getCurrentUser();
        Optional<Inventory> existing = repository.findByIdAndCompanyId(id, user.getCompanyId());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Inventory inventory = existing.get();
        validateInventory(dto, user.getCompanyId(), id);

        // Capture old values for audit
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("partName", inventory.getPartName());
        oldValues.put("quantity", inventory.getQuantity());
        oldValues.put("buyPrice", inventory.getBuyPrice());
        oldValues.put("sellPrice", inventory.getSellPrice());
        oldValues.put("criticalLevel", inventory.getCriticalLevel());

        BigDecimal oldQuantity = inventory.getQuantity();

        inventory.setPartName(dto.getPartName());
        inventory.setQuantity(normalizeQuantity(dto.getQuantity()));
        inventory.setBuyPrice(dto.getBuyPrice());
        inventory.setSellPrice(dto.getSellPrice());
        inventory.setCriticalLevel(defaultCriticalLevel(dto.getCriticalLevel()));
        inventory.setUnitOfMeasure(InventoryUnit.fromNullable(dto.getUnitOfMeasure()));
        inventory.setBrand(dto.getBrand());
        inventory.setCategory(dto.getCategory());
        inventory.setBarcode(normalizeBarcode(dto.getBarcode()));

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
        if (oldQuantity.compareTo(inventory.getQuantity()) != 0) {
            description += " (Stok: " + oldQuantity + " → " + dto.getQuantity() + ")";
        }
        auditLogService.logChange("UPDATE", "INVENTORY", saved.getId(), description, oldValues, newValues);

        return Optional.of(mapToDTO(saved));
    }

    public boolean deleteInventory(Long id) {
        User user = getCurrentUser();
        Optional<Inventory> existing = repository.findByIdAndCompanyId(id, user.getCompanyId());
        if (existing.isEmpty()) {
            return false;
        }
        Inventory inventory = existing.get();

        // Log before deletion
        auditLogService.log("DELETE", "INVENTORY", id,
                "Stok kalemi silindi: " + inventory.getPartName() + " (Kalan: " + inventory.getQuantity() + ")");

        repository.delete(inventory);
        return true;
    }

    public InventoryDTO mapToFullDTO(Inventory inventory) {
        return mapToDTO(inventory);
    }

    public InventoryDTO mapToDTO(Inventory inventory) {
        // Get vehicle stock distribution for this inventory item
        List<VehicleStock> vehicleStocks = vehicleStockRepository
                .findByInventoryIdAndCompanyId(inventory.getId(), inventory.getCompanyId());

        BigDecimal inVehicleTotal = BigDecimal.ZERO;
        List<VehicleStockInfo> distribution = new ArrayList<>();

        for (VehicleStock vs : vehicleStocks) {
            if (vs.getQuantity().signum() > 0 && vs.getVehicle() != null) {
                inVehicleTotal = inVehicleTotal.add(vs.getQuantity());
                String plate = vs.getVehicle().getLicensePlate() != null ? vs.getVehicle().getLicensePlate()
                        : "Unknown";
                distribution.add(new VehicleStockInfo(vs.getVehicle().getId(), plate, vs.getQuantity()));
            }
        }

        BigDecimal warehouseQty = inventory.getQuantity().subtract(inVehicleTotal);
        if (warehouseQty.signum() < 0)
            warehouseQty = BigDecimal.ZERO; // Safety check

        InventoryDTO dto = InventoryDTO.builder()
                .id(inventory.getId())
                .partName(inventory.getPartName())
                .quantity(inventory.getQuantity())
                .buyPrice(inventory.getBuyPrice())
                .sellPrice(inventory.getSellPrice())
                .criticalLevel(inventory.getCriticalLevel())
                .unitOfMeasure(inventory.getUnitOfMeasure().name())
                .brand(inventory.getBrand())
                .category(inventory.getCategory())
                .barcode(inventory.getBarcode())
                .build();

        // Set distribution fields
        dto.setWarehouseQuantity(warehouseQty);
        dto.setInVehicleQuantity(inVehicleTotal);
        dto.setVehicleDistribution(distribution);

        return dto;
    }

    private static String normalizeBarcode(String barcode) {
        if (barcode == null) {
            return null;
        }
        String trimmed = barcode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateInventory(InventoryDTO dto, Long companyId, Long currentId) {
        if (dto.getPartName() == null || dto.getPartName().isBlank()) {
            throw new IllegalArgumentException("Ürün adı zorunludur.");
        }
        if (dto.getQuantity() == null || dto.getQuantity().signum() < 0) {
            throw new IllegalArgumentException("Stok adedi negatif olamaz.");
        }
        InventoryUnit unit = InventoryUnit.fromNullable(dto.getUnitOfMeasure());
        validateQuantityForUnit(dto.getQuantity(), unit);
        if (dto.getCriticalLevel() != null) {
            if (dto.getCriticalLevel().signum() < 0) {
                throw new IllegalArgumentException("Kritik seviye negatif olamaz.");
            }
            validateQuantityForUnit(dto.getCriticalLevel(), unit);
        }
        if (dto.getBuyPrice() != null && dto.getBuyPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Alış fiyatı negatif olamaz.");
        }
        if (dto.getSellPrice() != null && dto.getSellPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Satış fiyatı negatif olamaz.");
        }
        String barcode = normalizeBarcode(dto.getBarcode());
        if (barcode != null) {
            repository.findByBarcodeNormalized(barcode, companyId)
                    .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Bu barkod başka bir aktif üründe kullanılıyor.");
                    });
        }
    }

    private static BigDecimal defaultCriticalLevel(BigDecimal criticalLevel) {
        return criticalLevel == null ? BigDecimal.ZERO : normalizeQuantity(criticalLevel);
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static void validateQuantityForUnit(BigDecimal value, InventoryUnit unit) {
        if (!unit.allowsFractionalQuantity() && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Adet birimli ürünlerde miktar tam sayı olmalıdır.");
        }
        if (value.compareTo(new BigDecimal("99999999999.999")) > 0) {
            throw new IllegalArgumentException("Miktar izin verilen üst sınırı aşıyor.");
        }
    }
}
