package com.pusula.backend.repository;

import com.pusula.backend.entity.ServicePhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePhotoRepository extends JpaRepository<ServicePhoto, Long> {
    List<ServicePhoto> findByTicketIdOrderByUploadedAtDesc(Long ticketId);
    List<ServicePhoto> findByTicketIdInOrderByUploadedAtDesc(List<Long> ticketIds);
}
