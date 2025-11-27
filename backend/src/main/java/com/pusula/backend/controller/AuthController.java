package com.pusula.backend.controller;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.service.AuthenticationService;

import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthResponse> authenticate(
            @RequestBody AuthRequest request) {
        System.out.println("AuthController: Received authentication request for user: " + request.getUsername());
        try {
            return ResponseEntity.ok(service.authenticate(request));
        } catch (Exception e) {
            System.err.println("AuthController: Authentication failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Boolean>> verifyPassword(@RequestBody AuthRequest request) {
        try {
            service.authenticate(request);
            return ResponseEntity.ok(java.util.Map.of("valid", true));
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Map.of("valid", false));
        }
    }
}
