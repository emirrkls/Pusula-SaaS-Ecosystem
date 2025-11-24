package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceUsedPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceUsedPartRepository extends JpaRepository<ServiceUsedPart, Long> {
    List<ServiceUsedPart> findByCompanyId(Long companyId);

    List<ServiceUsedPart> findByServiceTicketId(Long ticketId);
}
