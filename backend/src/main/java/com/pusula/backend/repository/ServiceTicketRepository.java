package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, Long> {
    List<ServiceTicket> findByCompanyId(Long companyId);

    List<ServiceTicket> findByAssignedTechnicianId(Long technicianId);

    @Query("SELECT t FROM ServiceTicket t WHERE t.assignedTechnicianId IS NOT NULL "
            + "AND t.assignmentNotificationSentAt IS NULL "
            + "AND t.status IN ('ASSIGNED', 'IN_PROGRESS') "
            + "AND (t.scheduledDate IS NULL OR t.scheduledDate <= :cutoff) "
            + "ORDER BY t.scheduledDate ASC")
    List<ServiceTicket> findAssignmentsDueForNotification(@Param("cutoff") LocalDateTime cutoff);

    long countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long companyId, LocalDateTime periodStart, LocalDateTime periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ServiceTicket t WHERE t.id = :id AND t.companyId = :companyId")
    Optional<ServiceTicket> findByIdAndCompanyIdForUpdate(
            @Param("id") Long id, @Param("companyId") Long companyId);

    // Count active tickets for a specific technician (excluding COMPLETED and CANCELLED)
    @Query("SELECT COUNT(t) FROM ServiceTicket t WHERE t.assignedTechnicianId = :techId AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    Long countActiveTicketsForTechnician(@Param("techId") Long techId);

    // Reassign active tickets from one technician to another
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ServiceTicket t SET t.assignedTechnicianId = :newTechId WHERE t.assignedTechnicianId = :oldTechId AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    void reassignActiveTickets(@Param("oldTechId") Long oldTechId, @Param("newTechId") Long newTechId);

    // Count active tickets (excluding COMPLETED and CANCELLED)
    @Query("SELECT COUNT(t) FROM ServiceTicket t WHERE t.companyId = :companyId AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    Long countActiveTickets(@Param("companyId") Long companyId);

    // Get completed tickets for performance tracking
    @Query("SELECT t FROM ServiceTicket t WHERE t.companyId = :companyId AND t.status = 'COMPLETED' AND COALESCE(t.completedAt, t.updatedAt) >= :since")
    List<ServiceTicket> findCompletedTicketsSince(@Param("companyId") Long companyId,
            @Param("since") LocalDateTime since);
}
