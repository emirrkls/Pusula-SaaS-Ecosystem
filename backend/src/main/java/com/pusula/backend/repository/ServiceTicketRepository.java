package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, UUID> {
    List<ServiceTicket> findByCompanyId(UUID companyId);

    List<ServiceTicket> findByAssignedTechnicianId(UUID technicianId);
}
