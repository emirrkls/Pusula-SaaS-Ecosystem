package com.pusula.backend.repository;

import com.pusula.backend.entity.AppStoreNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AppStoreNotificationRepository extends JpaRepository<AppStoreNotification, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AppStoreNotification n where n.id = :id")
    Optional<AppStoreNotification> lockById(@Param("id") String id);
    List<AppStoreNotification> findTop100ByProcessingStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            String status, java.time.LocalDateTime due);
}
