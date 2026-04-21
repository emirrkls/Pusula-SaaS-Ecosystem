package com.pusula.backend.controller;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.dto.InventoryTechDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.service.InventoryService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;
    private final InventoryRepository repository;

    public InventoryController(InventoryService service, InventoryRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * GET /api/inventory — returns role-appropriate DTO.
     * TECHNICIAN: gets InventoryTechDTO (NO buyPrice)
     * ADMIN: gets full InventoryDTO (with buyPrice)
     */
    @GetMapping
    public ResponseEntity<?> getAllInventory() {
        User user = getCurrentUser();
        if ("TECHNICIAN".equals(user.getRole())) {
            return ResponseEntity.ok(getTechInventory(user.getCompanyId()));
        }
        return ResponseEntity.ok(service.getAllInventory());
    }

    /**
     * GET /api/inventory/barcode/{code} — lookup by barcode (technician flow).
     * Returns InventoryTechDTO for technicians (no cost data).
     */
    @GetMapping("/barcode/{code}")
    public ResponseEntity<?> findByBarcode(@PathVariable String code) {
        User user = getCurrentUser();
        Optional<Inventory> item = repository.findByBarcodeAndCompanyId(code, user.getCompanyId());
        if (item.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Inventory inv = item.get();
        if ("TECHNICIAN".equals(user.getRole())) {
            return ResponseEntity.ok(mapToTechDTO(inv));
        }
        return ResponseEntity.ok(service.mapToFullDTO(inv));
    }

    @PostMapping
    public ResponseEntity<InventoryDTO> createInventory(@RequestBody InventoryDTO dto) {
        return ResponseEntity.ok(service.createInventory(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryDTO> updateInventory(@PathVariable Long id, @RequestBody InventoryDTO dto) {
        return ResponseEntity.ok(service.updateInventory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventory(@PathVariable Long id) {
        service.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }

    // ── Private helpers ──

    private List<InventoryTechDTO> getTechInventory(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(this::mapToTechDTO)
                .collect(Collectors.toList());
    }

    private InventoryTechDTO mapToTechDTO(Inventory inv) {
        return new InventoryTechDTO(
                inv.getId(),
                inv.getPartName(),
                inv.getQuantity(),
                inv.getSellPrice(),  // ONLY sell price — buyPrice intentionally omitted
                inv.getBrand(),
                inv.getCategory(),
                inv.getBarcode()
        );
    }
}
