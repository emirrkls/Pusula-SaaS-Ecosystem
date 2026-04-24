package com.pusula.backend.controller;

import com.pusula.backend.dto.VehicleStockDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.entity.VehicleStock;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.VehicleRepository;
import com.pusula.backend.repository.VehicleStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicle-stocks")
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

    @GetMapping
    public List<VehicleStockDTO> getAll() {
        Long companyId = ((com.pusula.backend.entity.User) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return vehicleStockRepository.findByCompanyId(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    public List<VehicleStockDTO> getByVehicle(@PathVariable Long vehicleId) {
        return vehicleStockRepository.findByVehicleId(vehicleId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-inventory/{inventoryId}")
    public List<VehicleStockDTO> getByInventory(@PathVariable Long inventoryId) {
        return vehicleStockRepository.findByInventoryId(inventoryId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        Long companyId = ((com.pusula.backend.entity.User) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        Long vehicleId = ((Number) request.get("vehicleId")).longValue();
        Long inventoryId = ((Number) request.get("inventoryId")).longValue();
        Integer quantity = ((Number) request.get("quantity")).intValue();

        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        Inventory inventory = inventoryRepository.findById(inventoryId).orElse(null);

        if (vehicle == null || inventory == null) {
            return ResponseEntity.badRequest().body("Vehicle or Inventory not found");
        }

        // Check if stock already exists for this vehicle-inventory pair
        VehicleStock existingStock = vehicleStockRepository
                .findByVehicleIdAndInventoryId(vehicleId, inventoryId)
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
    public ResponseEntity<VehicleStockDTO> update(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        return vehicleStockRepository.findById(id)
                .map(stock -> {
                    Integer quantity = ((Number) request.get("quantity")).intValue();
                    stock.setQuantity(quantity);
                    return ResponseEntity.ok(mapToDTO(vehicleStockRepository.save(stock)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return vehicleStockRepository.findById(id)
                .map(stock -> {
                    vehicleStockRepository.delete(stock);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
