package com.pusula.backend.repository;

import com.pusula.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

        // Find all logs for a company, ordered by timestamp descending
        Page<AuditLog> findByCompanyIdOrderByTimestampDesc(Long companyId, Pageable pageable);

        // Find logs by company and date range
        @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
                        "AND a.timestamp BETWEEN :startDate AND :endDate " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findByCompanyIdAndDateRange(
                        @Param("companyId") Long companyId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        // Find logs by company, user, and date range
        @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
                        "AND a.userId = :userId " +
                        "AND a.timestamp BETWEEN :startDate AND :endDate " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findByCompanyIdAndUserIdAndDateRange(
                        @Param("companyId") Long companyId,
                        @Param("userId") Long userId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        // Find logs by company, action type, and date range
        @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
                        "AND a.actionType = :actionType " +
                        "AND a.timestamp BETWEEN :startDate AND :endDate " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findByCompanyIdAndActionTypeAndDateRange(
                        @Param("companyId") Long companyId,
                        @Param("actionType") String actionType,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        // Find logs by company, user, action type, and date range (combined filter)
        @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
                        "AND a.userId = :userId " +
                        "AND a.actionType = :actionType " +
                        "AND a.timestamp BETWEEN :startDate AND :endDate " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findByCompanyIdAndUserIdAndActionTypeAndDateRange(
                        @Param("companyId") Long companyId,
                        @Param("userId") Long userId,
                        @Param("actionType") String actionType,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        // Find logs by company, entity type, and entity ID
        List<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
                        Long companyId, String entityType, Long entityId);

        // Find logs for timeline - chronological order (oldest first)
        List<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdOrderByTimestampAsc(
                        Long companyId, String entityType, Long entityId);

        // Count logs for a company
        long countByCompanyId(Long companyId);
}
