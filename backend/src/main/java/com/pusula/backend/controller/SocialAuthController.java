package com.pusula.backend.controller;

import com.pusula.backend.dto.*;
import com.pusula.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController @RequestMapping("/api/auth")
public class SocialAuthController {
    private final SocialAuthenticationService authentication;
    private final AppleAuthChallengeService challenges;
    private final AppleSignInOAuthClient appleOAuth;
    private final ConcurrentHashMap<String, Bucket> attempts = new ConcurrentHashMap<>();
    public SocialAuthController(SocialAuthenticationService authentication, AppleAuthChallengeService challenges,
            AppleSignInOAuthClient appleOAuth) {
        this.authentication = authentication; this.challenges = challenges; this.appleOAuth = appleOAuth;
    }
    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleAuthRequest body, HttpServletRequest request) {
        if (limited(request)) return rateLimited();
        return ResponseEntity.ok(authentication.google(body));
    }
    @PostMapping("/apple/challenge")
    public ResponseEntity<?> challenge(HttpServletRequest request) {
        if (limited(request)) return rateLimited();
        appleOAuth.requireConfigured();
        return ResponseEntity.ok(challenges.create());
    }
    @PostMapping("/apple")
    public ResponseEntity<?> apple(@Valid @RequestBody AppleAuthRequest body, HttpServletRequest request) {
        if (limited(request)) return rateLimited();
        return ResponseEntity.ok(authentication.apple(body));
    }
    private boolean limited(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().start() + 60_000 < now);
        String ip = clientIp(request);
        if (attempts.size() >= 10_000 && !attempts.containsKey(ip)) return true;
        var bucket = attempts.compute(ip, (k, old) -> old == null || old.start() + 60_000 < now
                ? new Bucket(now, 1) : new Bucket(old.start(), old.count() + 1));
        return bucket.count() > 20;
    }
    private ResponseEntity<?> rateLimited() {
        return ResponseEntity.status(429).header("Retry-After", "60").body(Map.of("code", "AUTH_RATE_LIMITED",
                "message", "Çok fazla giriş isteği. Lütfen bir dakika sonra tekrar deneyin."));
    }
    private record Bucket(long start, int count) {}

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean localProxy = "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
        // Only trust the header overwritten by the local reverse proxy, not an arbitrary forwarded chain.
        String realIp = request.getHeader("X-Real-IP");
        return localProxy && realIp != null && realIp.length() <= 45 && realIp.matches("[0-9a-fA-F:.]+")
                ? realIp : remote;
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> databaseFailure(DataAccessException error) {
        boolean conflict = error instanceof DataIntegrityViolationException;
        // Never expose provider subjects, encrypted tokens or raw SQL from a unique-key race.
        return ResponseEntity.status(conflict ? 409 : 503).body(Map.of("code", "SOCIAL_AUTH_RETRY",
                "message", "Giriş şu anda tamamlanamadı. Lütfen tekrar deneyin."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> unavailable(IllegalStateException error) {
        return ResponseEntity.status(503).body(Map.of("code", "SOCIAL_AUTH_UNAVAILABLE", "message", error.getMessage()));
    }
}
