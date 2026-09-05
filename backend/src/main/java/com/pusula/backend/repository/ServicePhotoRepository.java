package com.pusula.backend.repository;

import com.pusula.backend.entity.ServicePhoto;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePhotoRepository extends JpaRepository<ServicePhoto, Long> {
    List<ServicePhoto> findByTicketIdOrderByUploadedAtDesc(Long ticketId);
    List<ServicePhoto> findByTicketIdInOrderByUploadedAtDesc(List<Long> ticketIds);

    @Query(value = """
            SELECT p.ticketId
            FROM ServicePhoto p, ServiceTicket t, Customer c
            WHERE p.ticketId = t.id
              AND t.customerId = c.id
              AND t.companyId = :companyId
              AND (:type IS NULL OR p.type = :type)
              AND (:ticketId IS NULL OR p.ticketId = :ticketId)
              AND (:startDateTime IS NULL OR COALESCE(t.completedAt, t.updatedAt, t.scheduledDate, t.createdAt) >= :startDateTime)
              AND (:endDateTime IS NULL OR COALESCE(t.completedAt, t.updatedAt, t.scheduledDate, t.createdAt) < :endDateTime)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern
                   OR LOWER(t.description) LIKE :queryPattern
                   OR LOWER(p.note) LIKE :queryPattern
                   OR STR(p.ticketId) LIKE :queryPattern)
            GROUP BY p.ticketId
            ORDER BY MAX(COALESCE(t.completedAt, t.updatedAt, t.scheduledDate, t.createdAt)) DESC, p.ticketId DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.ticketId)
            FROM ServicePhoto p, ServiceTicket t, Customer c
            WHERE p.ticketId = t.id
              AND t.customerId = c.id
              AND t.companyId = :companyId
              AND (:type IS NULL OR p.type = :type)
              AND (:ticketId IS NULL OR p.ticketId = :ticketId)
              AND (:startDateTime IS NULL OR COALESCE(t.completedAt, t.updatedAt, t.scheduledDate, t.createdAt) >= :startDateTime)
              AND (:endDateTime IS NULL OR COALESCE(t.completedAt, t.updatedAt, t.scheduledDate, t.createdAt) < :endDateTime)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern
                   OR LOWER(t.description) LIKE :queryPattern
                   OR LOWER(p.note) LIKE :queryPattern
                   OR STR(p.ticketId) LIKE :queryPattern)
            """)
    Page<Long> findServiceFileTicketIds(
            @Param("companyId") Long companyId,
            @Param("type") ServicePhoto.PhotoType type,
            @Param("ticketId") Long ticketId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("queryPattern") String queryPattern,
            Pageable pageable);
}
