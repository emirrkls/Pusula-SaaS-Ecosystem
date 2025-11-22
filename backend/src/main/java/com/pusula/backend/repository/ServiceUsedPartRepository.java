package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceUsedPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceUsedPartRepository extends JpaRepository<ServiceUsedPart, UUID> {
    List<ServiceUsedPart> findByServiceTicketId(UUID ticketId);
}
