package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppCoexistenceRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface WhatsAppCoexistenceRecordRepository extends JpaRepository<WhatsAppCoexistenceRecord, Long> {
    Optional<WhatsAppCoexistenceRecord> findByIntegrationIdAndGenerationAndKindAndRecordKey(
        Long integrationId, String generation, String kind, String recordKey);
    List<WhatsAppCoexistenceRecord> findByCompanyIdAndIntegrationIdAndGenerationAndKindAndDeletedFalseOrderByEventAtDesc(
        Long companyId, Long integrationId, String generation, String kind, Pageable pageable);
    List<WhatsAppCoexistenceRecord> findByCompanyIdAndIntegrationIdAndGenerationAndKindAndPeerKeyAndDeletedFalseOrderByEventAtDesc(
        Long companyId, Long integrationId, String generation, String kind, String peerKey, Pageable pageable);
    void deleteByIntegrationId(Long integrationId);
    void deleteByIntegrationIdAndGenerationAndSource(Long integrationId, String generation, String source);
    @Modifying
    @Query("delete from WhatsAppCoexistenceRecord r where r.updatedAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
