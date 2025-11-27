package com.pusula.backend.controller;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.service.ServiceTicketService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class ServiceTicketController {

    private final ServiceTicketService service;

    public ServiceTicketController(ServiceTicketService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ServiceTicketDTO>> getAllTickets() {
        return ResponseEntity.ok(service.getAllTickets());
    }

    @PostMapping
    public ResponseEntity<ServiceTicketDTO> createTicket(@RequestBody ServiceTicketDTO dto) {
        return ResponseEntity.ok(service.createTicket(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceTicketDTO> updateTicket(
            @PathVariable Long id,
            @RequestBody ServiceTicketDTO dto) {
        return ResponseEntity.ok(service.updateTicket(id, dto));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ServiceTicketDTO> assignTechnician(@PathVariable Long id, @RequestParam Long technicianId) {
        return ResponseEntity.ok(service.assignTechnician(id, technicianId));
    }

    @PostMapping("/{id}/parts")
    public ResponseEntity<com.pusula.backend.dto.ServiceUsedPartDTO> addUsedPart(@PathVariable Long id,
            @RequestBody com.pusula.backend.dto.ServiceUsedPartDTO dto) {
        return ResponseEntity.ok(service.addUsedPart(id, dto));
    }

    @GetMapping("/{id}/parts")
    public ResponseEntity<List<com.pusula.backend.dto.ServiceUsedPartDTO>> getUsedParts(@PathVariable Long id) {
        return ResponseEntity.ok(service.getUsedParts(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ServiceTicketDTO> completeService(@PathVariable Long id,
            @RequestParam java.math.BigDecimal amount) {
        return ResponseEntity.ok(service.completeService(id, amount));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ServiceTicketDTO> cancelService(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancelService(id));
    }

    @PostMapping("/{id}/follow-up")
    public ResponseEntity<ServiceTicketDTO> createFollowUp(@PathVariable Long id) {
        return ResponseEntity.ok(service.createFollowUpTicket(id));
    }
}
