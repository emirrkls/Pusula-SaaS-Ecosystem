package com.pusula.backend.service;

import com.pusula.backend.entity.AppleAuthChallenge;
import com.pusula.backend.repository.AppleAuthChallengeRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AppleAuthChallengeService {
    private final AppleAuthChallengeRepository repository;
    private final SecureRandom random = new SecureRandom();
    public AppleAuthChallengeService(AppleAuthChallengeRepository repository) { this.repository = repository; }

    @Transactional
    public Challenge create() {
        var c = new AppleAuthChallenge();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        c.setId(UUID.randomUUID().toString()); c.setNonce(HexFormat.of().formatHex(bytes));
        c.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        repository.save(c);
        return new Challenge(c.getId(), c.getNonce());
    }

    /** Row lock serializes simultaneous requests; consumption commits with successful login. */
    @Transactional
    public String consume(String id) {
        var c = repository.findForUpdate(id).orElseThrow(this::invalid);
        if (c.getUsedAt() != null || !c.getExpiresAt().isAfter(LocalDateTime.now())) throw invalid();
        c.setUsedAt(LocalDateTime.now()); repository.save(c);
        return c.getNonce();
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanup() { repository.deleteByExpiresAtBefore(LocalDateTime.now().minusHours(1)); }
    private BadCredentialsException invalid() { return new BadCredentialsException("Apple giriş oturumu geçersiz veya süresi dolmuş. Tekrar deneyin."); }
    public record Challenge(String id, String nonce) {}
}
