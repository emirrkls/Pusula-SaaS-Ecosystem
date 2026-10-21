package com.pusula.backend.repository;

import com.pusula.backend.entity.GooglePlayNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface GooglePlayNotificationRepository extends JpaRepository<GooglePlayNotification, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from GooglePlayNotification n where n.id = :id")
    Optional<GooglePlayNotification> lockById(@Param("id") String id);
    List<GooglePlayNotification> findTop20ByProcessingStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<String> statuses, LocalDateTime now);
    @Modifying
    @Query("delete from GooglePlayNotification n where n.processedAt < :cutoff and n.encryptedToken is null")
    int deleteFinishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
