package com.pusula.backend.repository;

import com.pusula.backend.entity.MutationReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface MutationReceiptRepository extends JpaRepository<MutationReceipt, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO mutation_receipts (company_id,user_id,request_key,operation,fingerprint,created_at)
            VALUES (:companyId,:userId,:key,:operation,:fingerprint,:now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("companyId") Long companyId, @Param("userId") Long userId,
              @Param("key") String key, @Param("operation") String operation,
              @Param("fingerprint") String fingerprint, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MutationReceipt r WHERE r.companyId=:companyId AND r.userId=:userId AND r.requestKey=:key")
    Optional<MutationReceipt> lockReceipt(@Param("companyId") Long companyId,
            @Param("userId") Long userId, @Param("key") String key);

    @Modifying
    @Query("DELETE FROM MutationReceipt r WHERE r.createdAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
