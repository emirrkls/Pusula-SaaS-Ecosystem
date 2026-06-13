package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceUsedPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceUsedPartRepository extends JpaRepository<ServiceUsedPart, Long> {
    List<ServiceUsedPart> findByCompanyId(Long companyId);

    @Query("SELECT DISTINCT s FROM ServiceUsedPart s LEFT JOIN FETCH s.inventory WHERE s.serviceTicket.id = :ticketId")
    List<ServiceUsedPart> findByServiceTicketId(@Param("ticketId") Long ticketId);
}
