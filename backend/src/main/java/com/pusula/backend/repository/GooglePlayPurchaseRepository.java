package com.pusula.backend.repository;

import com.pusula.backend.entity.GooglePlayPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface GooglePlayPurchaseRepository extends JpaRepository<GooglePlayPurchase,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GooglePlayPurchase p where p.id=:id")
    Optional<GooglePlayPurchase> lockById(@Param("id") String id);
    List<GooglePlayPurchase> findTop20ByEncryptedTokenIsNotNullAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(LocalDateTime now);
}
