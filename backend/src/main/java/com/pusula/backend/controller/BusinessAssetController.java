package com.pusula.backend.controller;

import com.pusula.backend.dto.BusinessAssetDTO;
import com.pusula.backend.service.BusinessAssetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business-assets")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class BusinessAssetController {
    private final BusinessAssetService service;

    public BusinessAssetController(BusinessAssetService service) {
        this.service = service;
    }

    @GetMapping
    public List<BusinessAssetDTO> getAll() {
        return service.getAll();
    }

    @PostMapping
    public BusinessAssetDTO create(@Valid @RequestBody BusinessAssetDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessAssetDTO> update(@PathVariable Long id,
            @Valid @RequestBody BusinessAssetDTO dto) {
        return service.update(id, dto).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
