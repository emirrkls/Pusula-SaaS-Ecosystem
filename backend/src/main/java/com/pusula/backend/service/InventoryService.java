package com.pusula.backend.service;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository repository;

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
        Inventory saved = repository.save(inventory);
        return mapToDTO(saved);
    }

    public InventoryDTO updateInventory(UUID id, InventoryDTO dto) {
        User user = getCurrentUser();
        Inventory inventory = repository.findById(id)
                .filter(inv -> inv.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory not found or access denied"));

        inventory.setPartName(dto.getPartName());
        inventory.setQuantity(dto.getQuantity());
        inventory.setBuyPrice(dto.getBuyPrice());
        inventory.setSellPrice(dto.getSellPrice());
        inventory.setCriticalLevel(dto.getCriticalLevel());

        Inventory saved = repository.save(inventory);
        return mapToDTO(saved);
    }

    public void deleteInventory(UUID id) {
        User user = getCurrentUser();
        Inventory inventory = repository.findById(id)
                .filter(inv -> inv.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory not found or access denied"));
        repository.delete(inventory);
    }

    private InventoryDTO mapToDTO(Inventory inventory) {
        return InventoryDTO.builder()
                .id(inventory.getId())
                .partName(inventory.getPartName())
                .quantity(inventory.getQuantity())
                .buyPrice(inventory.getBuyPrice())
                .sellPrice(inventory.getSellPrice())
                .criticalLevel(inventory.getCriticalLevel())
                .build();
    }
}
