package com.pusula.backend.controller;

import com.pusula.backend.dto.PushDeviceRequest;
import com.pusula.backend.service.PushDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push-devices")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
public class PushDeviceController {
    private final PushDeviceService service;

    public PushDeviceController(PushDeviceService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody PushDeviceRequest request) {
        service.register(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@Valid @RequestBody PushDeviceRequest request) {
        service.unregister(request);
        return ResponseEntity.noContent().build();
    }
}
