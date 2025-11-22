package com.pusula.backend.controller;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.service.ServiceTicketService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
            @PathVariable UUID id,
            @RequestBody ServiceTicketDTO dto) {
        return ResponseEntity.ok(service.updateTicket(id, dto));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<ServiceTicketDTO> assignTechnician(@PathVariable UUID id, @RequestParam UUID technicianId) {
        return ResponseEntity.ok(service.assignTechnician(id, technicianId));
    }

    @PostMapping("/{id}/parts")
    public ResponseEntity<com.pusula.backend.dto.ServiceUsedPartDTO> addUsedPart(@PathVariable UUID id,
            @RequestBody com.pusula.backend.dto.ServiceUsedPartDTO dto) {
        return ResponseEntity.ok(service.addUsedPart(id, dto));
    }

    @GetMapping("/{id}/parts")
    public ResponseEntity<List<com.pusula.backend.dto.ServiceUsedPartDTO>> getUsedParts(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getUsedParts(id));
    }
}
