package com.pusula.backend.controller;

import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.service.ServiceTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final ServiceTicketService service;

    @PostMapping("/service-request")
    public ResponseEntity<ServiceTicketDTO> createServiceRequest(
            @RequestBody PublicServiceRequestDTO request) {
        return ResponseEntity.ok(service.createPublicTicket(request));
    }
}
