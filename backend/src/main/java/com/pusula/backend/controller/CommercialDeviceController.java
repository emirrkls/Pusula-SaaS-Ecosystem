package com.pusula.backend.controller;

import com.pusula.backend.dto.CommercialDeviceDTO;
import com.pusula.backend.dto.SaleRequestDTO;
import com.pusula.backend.dto.SaleResponseDTO;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.service.CommercialDeviceService;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.annotation.RequiresFeature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/commercial-devices")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
@RequiresFeature("COMMERCIAL_DEVICES")
public class CommercialDeviceController {

    private final CommercialDeviceService commercialDeviceService;
    private final UserRepository userRepository;
    private final FeatureService featureService;

    public CommercialDeviceController(CommercialDeviceService commercialDeviceService,
            UserRepository userRepository, FeatureService featureService) {
        this.commercialDeviceService = commercialDeviceService;
        this.userRepository = userRepository;
        this.featureService = featureService;
    }

    @GetMapping
    public ResponseEntity<List<CommercialDeviceDTO>> getAll() {
        User currentUser = getCurrentUser();
        List<CommercialDeviceDTO> devices = commercialDeviceService.getAllByCompany(currentUser.getCompanyId());
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommercialDeviceDTO> getById(@PathVariable Long id) {
        CommercialDeviceDTO device = commercialDeviceService.getById(id);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(device);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CommercialDeviceDTO> create(@RequestBody CommercialDeviceDTO dto) {
        featureService.checkQuota(getCurrentUser().getCompanyId(), "COMMERCIAL_DEVICES");
        CommercialDeviceDTO created = commercialDeviceService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CommercialDeviceDTO> update(@PathVariable Long id, @RequestBody CommercialDeviceDTO dto) {
        CommercialDeviceDTO updated = commercialDeviceService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commercialDeviceService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/sell")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CommercialDeviceDTO> sell(@PathVariable Long id, @RequestBody Map<String, Integer> payload) {
        Integer quantity = payload.get("quantity");
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().build();
        }
        CommercialDeviceDTO result = commercialDeviceService.sellDevice(id, quantity);
        return ResponseEntity.ok(result);
    }

    /**
     * Process a full device sale with customer, service ticket, and payment
     */
    @PostMapping("/sale")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<SaleResponseDTO> processSale(@RequestBody SaleRequestDTO request) {
        SaleResponseDTO response = commercialDeviceService.processSale(request);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
