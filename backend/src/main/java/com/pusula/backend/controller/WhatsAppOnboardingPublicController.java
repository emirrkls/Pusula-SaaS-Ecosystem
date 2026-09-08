package com.pusula.backend.controller;

import com.pusula.backend.dto.WhatsAppOnboardingDtos;
import com.pusula.backend.service.WhatsAppOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/whatsapp/onboarding")
public class WhatsAppOnboardingPublicController {
    private final WhatsAppOnboardingService service;

    public WhatsAppOnboardingPublicController(WhatsAppOnboardingService service) { this.service = service; }

    @PostMapping("/complete")
    public ResponseEntity<WhatsAppOnboardingDtos.CompleteResponse> complete(
            @Valid @RequestBody WhatsAppOnboardingDtos.CompleteRequest request) {
        return ResponseEntity.ok(service.complete(request));
    }
}
