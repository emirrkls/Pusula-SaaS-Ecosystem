package com.pusula.backend.repository;

import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushPlatform;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PushDevice> findByTokenHash(String tokenHash);

    Optional<PushDevice> findByTokenHashAndCompanyIdAndUserId(String tokenHash, Long companyId, Long userId);

    List<PushDevice> findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
            Long companyId, Long userId, PushPlatform platform);
}
