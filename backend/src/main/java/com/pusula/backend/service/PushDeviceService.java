package com.pusula.backend.service;

import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.dto.PushDeviceRequest;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.PushDeviceRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PushDeviceService {
    private final PushDeviceRepository repository;
    private final PushTokenCrypto tokenCrypto;
    private final ApplePushProperties properties;

    public PushDeviceService(PushDeviceRepository repository, PushTokenCrypto tokenCrypto,
            ApplePushProperties properties) {
        this.repository = repository;
        this.tokenCrypto = tokenCrypto;
        this.properties = properties;
    }

    @Transactional
    public void register(PushDeviceRequest request) {
        User principal = currentUser();
        validateBundle(request.bundleId());
        String normalizedToken = tokenCrypto.normalize(request.token());
        String tokenHash = tokenCrypto.hash(normalizedToken);
        PushDevice device = repository.findByTokenHash(tokenHash).orElseGet(PushDevice::new);

        // A device token is globally unique. A fresh authenticated registration safely
        // transfers ownership after logout/login, without trusting tenant data from JSON.
        device.setCompanyId(principal.getCompanyId());
        device.setUserId(principal.getId());
        device.setPlatform(request.platform());
        device.setEnvironment(request.environment());
        device.setBundleId(properties.getBundleId());
        device.setTokenHash(tokenHash);
        device.setTokenCiphertext(tokenCrypto.encrypt(normalizedToken));
        device.setActive(true);
        device.setLastSeenAt(LocalDateTime.now());
        repository.save(device);
    }

    @Transactional
    public void unregister(PushDeviceRequest request) {
        User principal = currentUser();
        validateBundle(request.bundleId());
        String tokenHash = tokenCrypto.hash(tokenCrypto.normalize(request.token()));
        repository.findByTokenHashAndCompanyIdAndUserId(tokenHash, principal.getCompanyId(), principal.getId())
                .ifPresent(device -> {
                    device.setActive(false);
                    device.setLastSeenAt(LocalDateTime.now());
                    repository.save(device);
                });
    }

    private void validateBundle(String bundleId) {
        if (!properties.getBundleId().equals(bundleId)) {
            throw new IllegalArgumentException("Bundle ID is not allowed");
        }
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
