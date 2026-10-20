package com.pusula.backend.controller;

import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.dto.WhatsAppOnboardingDtos;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.WhatsAppOnboardingService;
import com.pusula.backend.service.WhatsAppCoexistenceMirrorService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/whatsapp")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
@RequiresFeature("WHATSAPP_INTEGRATION")
public class WhatsAppIntegrationController {
    private final WhatsAppOnboardingService service;
    private final WhatsAppCoexistenceMirrorService mirror;

    public WhatsAppIntegrationController(WhatsAppOnboardingService service, WhatsAppCoexistenceMirrorService mirror) {
        this.service = service; this.mirror = mirror;
    }

    @GetMapping("/coexistence/contacts")
    public List<WhatsAppCoexistenceMirrorService.MirrorItem> contacts(@RequestParam(defaultValue = "0") int page) {
        return mirror.read(currentUser().getCompanyId(), "CONTACT", null, page);
    }

    @GetMapping("/coexistence/messages")
    public List<WhatsAppCoexistenceMirrorService.MirrorItem> messages(
            @RequestParam(required = false) String peerKey, @RequestParam(defaultValue = "0") int page) {
        return mirror.read(currentUser().getCompanyId(), "MESSAGE", peerKey, page);
    }

    @GetMapping("/status")
    public ResponseEntity<WhatsAppOnboardingDtos.StatusResponse> status() {
        User user = currentUser();
        return ResponseEntity.ok(service.status(user.getCompanyId()));
    }

    @PostMapping("/onboarding-session")
    public ResponseEntity<WhatsAppOnboardingDtos.StartResponse> start() {
        return ResponseEntity.ok(service.start(currentUser()));
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
