package com.pusula.backend.repository;

import com.pusula.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserId(Long userId);

    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    List<Notification> findTop100ByCompanyIdAndUserIdOrderByCreatedAtDesc(Long companyId, Long userId);
    long countByCompanyIdAndUserIdAndIsReadFalse(Long companyId, Long userId);
    Optional<Notification> findByIdAndCompanyIdAndUserId(Long id, Long companyId, Long userId);
}
