package com.pusula.backend.service;

import com.pusula.backend.entity.AppleAuthChallenge;
import com.pusula.backend.repository.AppleAuthChallengeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppleAuthChallengeServiceTest {
    private final AppleAuthChallengeRepository repository = mock(AppleAuthChallengeRepository.class);
    private final AppleAuthChallengeService service = new AppleAuthChallengeService(repository);
    @Test void issuesUnpredictableDifferentNonces() {
        var a = service.create(); var b = service.create();
        assertNotEquals(a.id(), b.id()); assertNotEquals(a.nonce(), b.nonce()); assertEquals(64, a.nonce().length());
    }
    @Test void consumesOnlyOnceAndRejectsExpiredMissing() {
        var c = new AppleAuthChallenge(); c.setId("id"); c.setNonce("nonce"); c.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(repository.findForUpdate("id")).thenReturn(Optional.of(c));
        assertEquals("nonce", service.consume("id"));
        assertThrows(BadCredentialsException.class, () -> service.consume("id"));
        c.setUsedAt(null); c.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(BadCredentialsException.class, () -> service.consume("id"));
        assertThrows(BadCredentialsException.class, () -> service.consume("missing"));
    }
}
