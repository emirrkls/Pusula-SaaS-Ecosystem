package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppMessageOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageOutboxRepository extends JpaRepository<WhatsAppMessageOutbox, Long> {
    Optional<WhatsAppMessageOutbox> findByProviderMessageId(String providerMessageId);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO whatsapp_message_outbox
                (company_id, ticket_id, notification_type, idempotency_key, recipient_phone,
                 template_name, template_language, parameters_json, status, attempt_count,
                 next_attempt_at, created_at, updated_at)
            VALUES
                (:companyId, :ticketId, :notificationType, :idempotencyKey, :recipientPhone,
                 :templateName, :templateLanguage, :parametersJson, 'PENDING', 0,
                 :now, :now, :now)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(@Param("companyId") Long companyId,
                        @Param("ticketId") Long ticketId,
                        @Param("notificationType") String notificationType,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("recipientPhone") String recipientPhone,
                        @Param("templateName") String templateName,
                        @Param("templateLanguage") String templateLanguage,
                        @Param("parametersJson") String parametersJson,
                        @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT * FROM whatsapp_message_outbox
            WHERE (status IN ('PENDING', 'RETRY') AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND processing_started_at < :staleBefore)
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<WhatsAppMessageOutbox> lockDueBatch(@Param("now") LocalDateTime now,
                                             @Param("staleBefore") LocalDateTime staleBefore,
                                             @Param("batchSize") int batchSize);
}
