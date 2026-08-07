package com.pusula.backend.service;

import com.pusula.backend.dto.BusinessAssetDTO;
import com.pusula.backend.entity.BusinessAsset;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.BusinessAssetRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BusinessAssetService {
    private final BusinessAssetRepository repository;
    private final AuditLogService auditLogService;

    public BusinessAssetService(BusinessAssetRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BusinessAssetDTO> getAll() {
        Long companyId = currentUser().getCompanyId();
        return repository.findByCompanyIdOrderByAssetNameAsc(companyId).stream().map(this::toDto).toList();
    }

    @Transactional
    public BusinessAssetDTO create(BusinessAssetDTO dto) {
        User user = currentUser();
        BusinessAsset asset = new BusinessAsset();
        asset.setCompanyId(user.getCompanyId());
        apply(dto, asset);
        BusinessAsset saved = repository.save(asset);
        auditLogService.log("CREATE", "BUSINESS_ASSET", saved.getId(),
                "Demirbaş eklendi: " + saved.getAssetName());
        return toDto(saved);
    }

    @Transactional
    public Optional<BusinessAssetDTO> update(Long id, BusinessAssetDTO dto) {
        Long companyId = currentUser().getCompanyId();
        return repository.findByIdAndCompanyId(id, companyId).map(asset -> {
            apply(dto, asset);
            BusinessAsset saved = repository.save(asset);
            auditLogService.log("UPDATE", "BUSINESS_ASSET", saved.getId(),
                    "Demirbaş güncellendi: " + saved.getAssetName());
            return toDto(saved);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        Long companyId = currentUser().getCompanyId();
        return repository.findByIdAndCompanyId(id, companyId).map(asset -> {
            repository.delete(asset);
            auditLogService.log("DELETE", "BUSINESS_ASSET", asset.getId(),
                    "Demirbaş silindi: " + asset.getAssetName());
            return true;
        }).orElse(false);
    }

    private void apply(BusinessAssetDTO dto, BusinessAsset asset) {
        asset.setAssetName(dto.getAssetName().trim());
        asset.setCategory(clean(dto.getCategory()));
        asset.setQuantity(dto.getQuantity() == null ? 1 : dto.getQuantity());
        asset.setCondition(dto.getCondition() == null ? "ACTIVE" : dto.getCondition());
        asset.setSerialNumber(clean(dto.getSerialNumber()));
        asset.setLocation(clean(dto.getLocation()));
        asset.setAssignedTo(clean(dto.getAssignedTo()));
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setPurchasePrice(dto.getPurchasePrice());
        asset.setNotes(clean(dto.getNotes()));
    }

    private BusinessAssetDTO toDto(BusinessAsset asset) {
        BusinessAssetDTO dto = new BusinessAssetDTO();
        dto.setId(asset.getId());
        dto.setAssetName(asset.getAssetName());
        dto.setCategory(asset.getCategory());
        dto.setQuantity(asset.getQuantity());
        dto.setCondition(asset.getCondition());
        dto.setSerialNumber(asset.getSerialNumber());
        dto.setLocation(asset.getLocation());
        dto.setAssignedTo(asset.getAssignedTo());
        dto.setPurchaseDate(asset.getPurchaseDate());
        dto.setPurchasePrice(asset.getPurchasePrice());
        dto.setNotes(asset.getNotes());
        return dto;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
