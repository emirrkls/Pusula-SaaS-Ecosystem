package com.pusula.backend.controller;

import com.pusula.backend.dto.VehicleStockDTO;
import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.entity.VehicleStock;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.VehicleRepository;
import com.pusula.backend.repository.VehicleStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicle-stocks")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
@RequiresFeature("VEHICLE_TRACKING")
public class VehicleStockController {

    @Autowired
    private VehicleStockRepository vehicleStockRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private VehicleStockDTO mapToDTO(VehicleStock stock) {
        return new VehicleStockDTO(
                stock.getId(),
                stock.getCompanyId(),
                stock.getVehicle() != null ? stock.getVehicle().getId() : null,
                stock.getInventory() != null ? stock.getInventory().getId() : null,
                stock.getVehicle() != null ? stock.getVehicle().getLicensePlate() : null,
                stock.getInventory() != null ? stock.getInventory().getPartName() : null,
                stock.getQuantity());
    }

    private Long getCompanyId() {
        return ((com.pusula.backend.entity.User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).getCompanyId();
    }

    @GetMapping
    public List<VehicleStockDTO> getAll() {
        Long companyId = getCompanyId();
        return vehicleStockRepository.findByCompanyId(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    public List<VehicleStockDTO> getByVehicle(@PathVariable Long vehicleId) {
        return vehicleStockRepository.findByVehicleIdAndCompanyId(vehicleId, getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-inventory/{inventoryId}")
    public List<VehicleStockDTO> getByInventory(@PathVariable Long inventoryId) {
        return vehicleStockRepository.findByInventoryIdAndCompanyId(inventoryId, getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        Long companyId = getCompanyId();
        if (!(request.get("vehicleId") instanceof Number)
                || !(request.get("inventoryId") instanceof Number)
                || !(request.get("quantity") instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Araç, ürün ve adet zorunludur."));
        }
        Long vehicleId = ((Number) request.get("vehicleId")).longValue();
        Long inventoryId = ((Number) request.get("inventoryId")).longValue();
        Integer quantity = ((Number) request.get("quantity")).intValue();
        if (quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Adet sıfırdan büyük olmalıdır."));
        }

        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, companyId).orElse(null);
        Inventory inventory = inventoryRepository.findByIdAndCompanyId(inventoryId, companyId).orElse(null);

        if (vehicle == null || inventory == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Araç veya ürün bulunamadı."));
        }

        // Check if stock already exists for this vehicle-inventory pair
        VehicleStock existingStock = vehicleStockRepository
                .findForUpdate(vehicleId, inventoryId, companyId)
                .orElse(null);

        if (existingStock != null) {
            // Update existing stock
            existingStock.setQuantity(existingStock.getQuantity() + quantity);
            return ResponseEntity.ok(mapToDTO(vehicleStockRepository.save(existingStock)));
        }

        VehicleStock vehicleStock = VehicleStock.builder()
                .companyId(companyId)
                .vehicle(vehicle)
                .inventory(inventory)
                .quantity(quantity)
                .build();

        return ResponseEntity.ok(mapToDTO(vehicleStockRepository.save(vehicleStock)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VehicleStockDTO> update(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        if (!(request.get("quantity") instanceof Number number) || number.intValue() < 0) {
            return ResponseEntity.badRequest().build();
        }
        return vehicleStockRepository.findByIdAndCompanyId(id, getCompanyId())
                .map(stock -> {
                    Integer quantity = number.intValue();
                    stock.setQuantity(quantity);
                    return ResponseEntity.ok(mapToDTO(vehicleStockRepository.save(stock)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return vehicleStockRepository.findByIdAndCompanyId(id, getCompanyId())
                .map(stock -> {
                    vehicleStockRepository.delete(stock);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
