package com.pusula.backend.controller;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.service.ServiceTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class ServiceTicketController {

    private final ServiceTicketService service;

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
}
