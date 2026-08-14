package com.pusula.backend.controller;

import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.VehicleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.service.FeatureService;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiresFeature("VEHICLE_TRACKING")
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final FeatureService featureService;

    public VehicleController(VehicleRepository vehicleRepository, FeatureService featureService) {
        this.vehicleRepository = vehicleRepository;
        this.featureService = featureService;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public List<Vehicle> getAll() {
        return vehicleRepository.findByCompanyId(getCurrentUser().getCompanyId());
    }

    @GetMapping("/active")
    public List<Vehicle> getActive() {
        return vehicleRepository.findByCompanyIdAndIsActiveTrue(getCurrentUser().getCompanyId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getById(@PathVariable Long id) {
        return vehicleRepository.findByIdAndCompanyId(id, getCurrentUser().getCompanyId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public Vehicle create(@RequestBody Vehicle vehicle) {
        if (!Boolean.FALSE.equals(vehicle.getIsActive())) {
            featureService.checkQuota(getCurrentUser().getCompanyId(), "VEHICLES");
        }
        vehicle.setCompanyId(getCurrentUser().getCompanyId());
        return vehicleRepository.save(vehicle);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Vehicle> update(@PathVariable Long id,
            @RequestBody Vehicle vehicleDetails) {
        return vehicleRepository.findByIdAndCompanyId(id, getCurrentUser().getCompanyId())
                .map(vehicle -> {
                    if (!Boolean.TRUE.equals(vehicle.getIsActive())
                            && Boolean.TRUE.equals(vehicleDetails.getIsActive())) {
                        featureService.checkQuota(getCurrentUser().getCompanyId(), "VEHICLES");
                    }
                    vehicle.setLicensePlate(vehicleDetails.getLicensePlate());
                    vehicle.setDriverName(vehicleDetails.getDriverName());
                    vehicle.setIsActive(vehicleDetails.getIsActive());
                    return ResponseEntity.ok(vehicleRepository.save(vehicle));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return vehicleRepository.findByIdAndCompanyId(id, getCurrentUser().getCompanyId())
                .map(vehicle -> {
                    vehicleRepository.delete(vehicle);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
