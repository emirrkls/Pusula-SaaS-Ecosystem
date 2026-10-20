package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppCoexistenceSyncJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface WhatsAppCoexistenceSyncJobRepository extends JpaRepository<WhatsAppCoexistenceSyncJob, Long> {
    List<WhatsAppCoexistenceSyncJob> findByIntegrationIdAndGeneration(Long integrationId, String generation);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from WhatsAppCoexistenceSyncJob j where j.id = :id")
    Optional<WhatsAppCoexistenceSyncJob> lockById(@Param("id") Long id);
    @Query("""
        select j.id from WhatsAppCoexistenceSyncJob j, WhatsAppBusinessIntegration i
        where j.integrationId = i.id and j.generation = i.coexistenceGeneration
        and i.deleted = false and i.coexistence = true and i.status = 'CONNECTED'
        and i.webhookSubscriptionStatus = 'SUBSCRIBED'
        and j.status = 'PENDING' and j.nextAttemptAt <= :now
        order by j.nextAttemptAt, j.id
        """)
    List<Long> findDue(@Param("now") LocalDateTime now, Pageable pageable);
    @Modifying
    @Query("""
        update WhatsAppCoexistenceSyncJob j set j.status = 'UNKNOWN', j.errorCode = 'REQUEST_OUTCOME_UNKNOWN', j.updatedAt = :now
        where j.status = 'REQUESTING' and j.requestedAt < :stale
        """)
    int markStaleRequests(@Param("stale") LocalDateTime stale, @Param("now") LocalDateTime now);
    @Modifying
    @Query("""
        update WhatsAppCoexistenceSyncJob j set j.status = 'FAILED', j.errorCode = 'SYNC_WINDOW_EXPIRED', j.updatedAt = :now
        where j.deadlineAt <= :now and (j.status = 'PENDING'
            or j.syncType = 'history' and j.status in ('ACCEPTED','RECEIVING'))
        """)
    int expireWindows(@Param("now") LocalDateTime now);
}
