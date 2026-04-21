package com.pusula.backend.controller;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.AuthenticationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService service;

    public AuthController(AuthenticationService service) {
        this.service = service;
    }

    /**
     * Corporate user registration — adds a user to an existing company.
     * Requires COMPANY_ADMIN role.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    /**
     * Individual registration — creates a new company + admin user.
     * Used by independent technicians downloading from App Store.
     */
    @PostMapping("/register-individual")
    public ResponseEntity<AuthResponse> registerIndividual(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.registerIndividual(request));
    }

    /**
     * Authenticate — supports both individual and corporate (orgCode) flows.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<AuthResponse> authenticate(
            @RequestBody AuthRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    /**
     * Verify password — used for sensitive operations requiring re-authentication.
     */
    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Boolean>> verifyPassword(@RequestBody AuthRequest request) {
        try {
            service.authenticate(request);
            return ResponseEntity.ok(java.util.Map.of("valid", true));
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Map.of("valid", false));
        }
    }

    /**
     * Get current feature context — returns updated features, quota, and plan info.
     * Called by iOS app on foreground resume to detect plan changes.
     */
    @GetMapping("/feature-context")
    public ResponseEntity<AuthResponse> getFeatureContext() {
        User user = getCurrentUser();
        // Re-authenticate internally to get fresh feature context
        // This will be replaced by FeatureService in Sprint 2
        AuthResponse response = AuthResponse.builder()
                .role(user.getRole())
                .fullName(user.getFullName())
                .companyId(user.getCompanyId())
                .build();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete the authenticated user's account.
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/delete-account")
    public ResponseEntity<Void> deleteAccount() {
        service.deleteAccount();
        return ResponseEntity.ok().build();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
