package com.pusula.backend.controller;

import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.VehicleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleRepository vehicleRepository;

    public VehicleController(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
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
        vehicle.setCompanyId(getCurrentUser().getCompanyId());
        return vehicleRepository.save(vehicle);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Vehicle> update(@PathVariable Long id,
            @RequestBody Vehicle vehicleDetails) {
        return vehicleRepository.findByIdAndCompanyId(id, getCurrentUser().getCompanyId())
                .map(vehicle -> {
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
