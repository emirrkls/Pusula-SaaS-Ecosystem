package com.pusula.backend.controller;

import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired
    private VehicleRepository vehicleRepository;

    @GetMapping
    public List<Vehicle> getAll(@RequestHeader("X-Company-Id") Long companyId) {
        return vehicleRepository.findByCompanyId(companyId);
    }

    @GetMapping("/active")
    public List<Vehicle> getActive(@RequestHeader("X-Company-Id") Long companyId) {
        return vehicleRepository.findByCompanyIdAndIsActiveTrue(companyId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getById(@PathVariable Long id) {
        return vehicleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Vehicle create(@RequestHeader("X-Company-Id") Long companyId,
            @RequestBody Vehicle vehicle) {
        vehicle.setCompanyId(companyId);
        return vehicleRepository.save(vehicle);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> update(@PathVariable Long id,
            @RequestBody Vehicle vehicleDetails) {
        return vehicleRepository.findById(id)
                .map(vehicle -> {
                    vehicle.setLicensePlate(vehicleDetails.getLicensePlate());
                    vehicle.setDriverName(vehicleDetails.getDriverName());
                    vehicle.setIsActive(vehicleDetails.getIsActive());
                    return ResponseEntity.ok(vehicleRepository.save(vehicle));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return vehicleRepository.findById(id)
                .map(vehicle -> {
                    vehicleRepository.delete(vehicle);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
