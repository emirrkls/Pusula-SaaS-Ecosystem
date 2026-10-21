package com.pusula.backend.controller;

import com.pusula.backend.entity.User;
import com.pusula.backend.service.GooglePlayBillingStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class GooglePlayBillingStatusController {
    private final GooglePlayBillingStatusService service;
    public GooglePlayBillingStatusController(GooglePlayBillingStatusService service) { this.service=service; }
    @GetMapping("/api/subscription/google-status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','SUPER_ADMIN')")
    public GooglePlayBillingStatusService.Status status() {
        User user=(User)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return service.status(user.getCompanyId());
    }
}
