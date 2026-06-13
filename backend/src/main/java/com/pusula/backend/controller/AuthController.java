package com.pusula.backend.controller;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.GoogleAuthRequest;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService service;
    private final ConcurrentHashMap<String, AttemptWindow> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 8;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

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
    public ResponseEntity<?> authenticate(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        String key = buildThrottleKey(request.getUsername(), httpRequest);
        if (isBlocked(key)) {
            return ResponseEntity.status(429).body(Map.of(
                    "status", 429,
                    "code", "AUTH_RATE_LIMITED",
                    "message", "Çok fazla başarısız giriş denemesi. Lütfen daha sonra tekrar deneyin.",
                    "path", "/api/auth/authenticate"));
        }
        try {
            AuthResponse response = service.authenticate(request);
            failedAttempts.remove(key);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            registerFailure(key);
            throw ex;
        }
    }

    /**
     * Google authentication — verifies Google ID token and signs user in/up.
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(service.authenticateWithGoogle(request));
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

    private String buildThrottleKey(String username, HttpServletRequest request) {
        String normalizedUser = username == null ? "" : username.trim().toLowerCase();
        String ip = request.getRemoteAddr();
        return normalizedUser + "|" + ip;
    }

    private boolean isBlocked(String key) {
        AttemptWindow window = failedAttempts.get(key);
        if (window == null) return false;
        long now = System.currentTimeMillis();
        if (now - window.windowStartMs > WINDOW_MS) {
            failedAttempts.remove(key);
            return false;
        }
        return window.failedCount.get() >= MAX_FAILED_ATTEMPTS;
    }

    private void registerFailure(String key) {
        long now = System.currentTimeMillis();
        failedAttempts.compute(key, (k, current) -> {
            if (current == null || now - current.windowStartMs > WINDOW_MS) {
                return new AttemptWindow(now, new AtomicInteger(1));
            }
            current.failedCount.incrementAndGet();
            return current;
        });
    }

    private static final class AttemptWindow {
        private final long windowStartMs;
        private final AtomicInteger failedCount;

        private AttemptWindow(long windowStartMs, AtomicInteger failedCount) {
            this.windowStartMs = windowStartMs;
            this.failedCount = failedCount;
        }
    }
}
