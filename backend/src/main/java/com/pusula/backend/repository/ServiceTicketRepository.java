package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, Long> {
    List<ServiceTicket> findByCompanyId(Long companyId);

    List<ServiceTicket> findByAssignedTechnicianId(Long technicianId);

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
    @Query("SELECT t FROM ServiceTicket t WHERE t.status = 'COMPLETED' AND t.updatedAt >= :since")
    List<ServiceTicket> findCompletedTicketsSince(@Param("since") LocalDateTime since);
}
