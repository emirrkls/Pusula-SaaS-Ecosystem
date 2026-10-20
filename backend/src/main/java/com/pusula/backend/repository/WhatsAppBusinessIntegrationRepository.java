package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WhatsAppBusinessIntegrationRepository extends JpaRepository<WhatsAppBusinessIntegration, Long> {
    Optional<WhatsAppBusinessIntegration> findByCompanyIdAndDeletedFalse(Long companyId);
    Optional<WhatsAppBusinessIntegration> findByPhoneNumberIdAndDeletedFalse(String phoneNumberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WhatsAppBusinessIntegration i where i.id = :id and i.deleted = false")
    Optional<WhatsAppBusinessIntegration> lockActiveById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WhatsAppBusinessIntegration i where i.wabaId = :wabaId and i.deleted = false order by i.id")
    List<WhatsAppBusinessIntegration> lockActiveByWabaId(@Param("wabaId") String wabaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            select integration from WhatsAppBusinessIntegration integration
            where integration.deleted = false
              and integration.webhookSubscriptionStatus in ('PENDING', 'RETRY')
              and (integration.webhookSubscriptionNextAttemptAt is null
                   or integration.webhookSubscriptionNextAttemptAt <= :now)
            order by integration.updatedAt
            """)
    List<WhatsAppBusinessIntegration> lockDueWebhookSubscriptions(@Param("now") LocalDateTime now,
                                                                  org.springframework.data.domain.Pageable pageable);
}
