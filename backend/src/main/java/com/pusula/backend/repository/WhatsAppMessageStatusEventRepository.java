package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppMessageStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface WhatsAppMessageStatusEventRepository extends JpaRepository<WhatsAppMessageStatusEvent, Long> {
    boolean existsByEventKey(String eventKey);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO whatsapp_message_status_events
                (event_key, company_id, outbox_id, provider_message_id, phone_number_id,
                 recipient_id, status, event_timestamp, error_code, error_message, received_at)
            VALUES
                (:eventKey, :companyId, :outboxId, :providerMessageId, :phoneNumberId,
                 :recipientId, :status, :eventTimestamp, :errorCode, :errorMessage, :receivedAt)
            ON CONFLICT (event_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventKey") String eventKey,
                       @Param("companyId") Long companyId,
                       @Param("outboxId") Long outboxId,
                       @Param("providerMessageId") String providerMessageId,
                       @Param("phoneNumberId") String phoneNumberId,
                       @Param("recipientId") String recipientId,
                       @Param("status") String status,
                       @Param("eventTimestamp") LocalDateTime eventTimestamp,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage,
                       @Param("receivedAt") LocalDateTime receivedAt);
}
