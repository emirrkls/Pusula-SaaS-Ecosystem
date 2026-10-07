package com.pusula.backend.controller;

import com.pusula.backend.service.AppStoreNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public transport endpoint; authorization is Apple's verified V2 JWS, never a URL secret. */
@RestController
@RequestMapping("/api/public/apple/app-store-notifications")
public class AppStoreNotificationController {
    private final AppStoreNotificationService service;
    public AppStoreNotificationController(AppStoreNotificationService service) { this.service = service; }
    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> receive(@Valid @RequestBody NotificationRequest request) {
        service.receive(request.signedPayload());
        return ResponseEntity.noContent().build();
    }
    public record NotificationRequest(@NotBlank @Size(max = 131072) String signedPayload) {}
}
