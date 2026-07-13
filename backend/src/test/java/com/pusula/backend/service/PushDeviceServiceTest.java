package com.pusula.backend.service;

import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.dto.PushDeviceRequest;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushEnvironment;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.PushDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {
    private static final String TOKEN = "a1".repeat(32);
    @Mock PushDeviceRepository repository;
    private PushDeviceService service;
    private PushTokenCrypto crypto;
    private ApplePushProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApplePushProperties();
        properties.setBundleId("com.pusula.service");
        properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)));
        crypto = new PushTokenCrypto(properties);
        service = new PushDeviceService(repository, crypto, properties);
        authenticate(7L, 10L);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void registerCreatesEncryptedDeviceForAuthenticatedOwner() {
        when(repository.findByTokenHash(crypto.hash(TOKEN))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.register(request(TOKEN));

        ArgumentCaptor<PushDevice> captor = ArgumentCaptor.forClass(PushDevice.class);
        verify(repository).save(captor.capture());
        PushDevice saved = captor.getValue();
        assertEquals(10L, saved.getCompanyId());
        assertEquals(7L, saved.getUserId());
        assertTrue(saved.isActive());
        assertEquals(TOKEN, crypto.decrypt(saved.getTokenCiphertext()));
        assertNotEquals(TOKEN, saved.getTokenCiphertext());
        assertFalse(saved.getTokenCiphertext().contains(TOKEN));
    }

    @Test
    void registerUpsertTransfersOwnershipToAuthenticatedPrincipal() {
        PushDevice existing = new PushDevice();
        existing.setCompanyId(20L);
        existing.setUserId(99L);
        existing.setActive(false);
        when(repository.findByTokenHash(crypto.hash(TOKEN))).thenReturn(Optional.of(existing));

        service.register(request(TOKEN));

        assertEquals(10L, existing.getCompanyId());
        assertEquals(7L, existing.getUserId());
        assertTrue(existing.isActive());
        verify(repository).save(existing);
    }

    @Test
    void unregisterOnlyDeactivatesAuthenticatedUsersToken() {
        PushDevice own = new PushDevice();
        own.setActive(true);
        String hash = crypto.hash(TOKEN);
        when(repository.findByTokenHashAndCompanyIdAndUserId(hash, 10L, 7L)).thenReturn(Optional.of(own));

        service.unregister(request(TOKEN));

        assertFalse(own.isActive());
        verify(repository).save(own);
    }

    @Test
    void unregisterDoesNotCrossTenantBoundary() {
        String hash = crypto.hash(TOKEN);
        when(repository.findByTokenHashAndCompanyIdAndUserId(hash, 10L, 7L)).thenReturn(Optional.empty());

        service.unregister(request(TOKEN));

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsUnexpectedBundleWithoutLookingUpToken() {
        PushDeviceRequest request = new PushDeviceRequest(TOKEN, PushPlatform.IOS,
                PushEnvironment.SANDBOX, "com.attacker.app");
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    void requestStringRepresentationRedactsPlaintextToken() {
        String rendered = request(TOKEN).toString();
        assertFalse(rendered.contains(TOKEN));
        assertTrue(rendered.contains("<redacted>"));
    }

    private PushDeviceRequest request(String token) {
        return new PushDeviceRequest(token, PushPlatform.IOS, PushEnvironment.SANDBOX, "com.pusula.service");
    }

    private void authenticate(Long userId, Long companyId) {
        User user = new User();
        user.setId(userId);
        user.setCompanyId(companyId);
        user.setRole("TECHNICIAN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
