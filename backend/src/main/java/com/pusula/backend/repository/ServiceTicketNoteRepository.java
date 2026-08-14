package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceTicketNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceTicketNoteRepository extends JpaRepository<ServiceTicketNote, Long> {
    List<ServiceTicketNote> findByServiceTicketIdAndCompanyIdOrderByCreatedAtAsc(Long serviceTicketId, Long companyId);
}
