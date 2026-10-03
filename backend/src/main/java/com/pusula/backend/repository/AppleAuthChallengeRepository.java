package com.pusula.backend.repository;

import com.pusula.backend.entity.AppleAuthChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AppleAuthChallengeRepository extends JpaRepository<AppleAuthChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AppleAuthChallenge c where c.id = :id")
    Optional<AppleAuthChallenge> findForUpdate(@Param("id") String id);
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
